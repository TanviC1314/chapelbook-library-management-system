package com.chapelbook.borrowing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.book.CopyStatus;
import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.fine.Fine;
import com.chapelbook.fine.FineRepository;
import com.chapelbook.reservation.ReservationResponse;
import com.chapelbook.reservation.ReservationService;
import com.chapelbook.security.AuthenticatedUser;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Renewals and the loan listings ("My borrowings", and the librarian's active and overdue views). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class LoanQueryIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TestAuth auth;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private BookRepository books;
    @Autowired
    private BookCopyRepository copies;
    @Autowired
    private BorrowRecordRepository loans;
    @Autowired
    private FineRepository fines;
    @Autowired
    private ReservationService reservationService;

    private Category category;
    private User student;
    private User otherStudent;
    private User librarianUser;
    private String studentToken;
    private String otherToken;
    private String librarian;

    @BeforeEach
    void setUp() {
        category = categories.saveAndFlush(new Category("Loan query cat " + SEQ.incrementAndGet(), null));
        student = auth.createUser(Role.STUDENT);
        otherStudent = auth.createUser(Role.STUDENT);
        librarianUser = auth.createUser(Role.LIBRARIAN);
        studentToken = auth.bearer(student);
        otherToken = auth.bearer(otherStudent);
        librarian = auth.bearer(librarianUser);
    }

    private Book book() {
        return books.saveAndFlush(new Book("Query book " + SEQ.incrementAndGet(), "Author", category));
    }

    private BorrowRecord loan(Book book, User to, long dueInHours) {
        BookCopy copy = copies.saveAndFlush(new BookCopy(book, "LQ-" + SEQ.incrementAndGet() + "-" + System.nanoTime(), null));
        copy.setStatus(CopyStatus.ON_LOAN);
        copies.saveAndFlush(copy);
        Instant due = Instant.now().plus(dueInHours, ChronoUnit.HOURS);
        return loans.saveAndFlush(new BorrowRecord(copy, to, librarianUser, null, due.minus(14, ChronoUnit.DAYS), due));
    }

    private ResultActions renew(String token, long loanId) throws Exception {
        return mvc.perform(post("/api/v1/loans/" + loanId + "/renew").header("Authorization", token));
    }

    // ------------------------------------------------------------ renewals

    @Test
    void aStudentRenewsTheirOwnLoanAndTheDueDateMovesOutByTheRenewalPeriod() throws Exception {
        BorrowRecord loan = loan(book(), student, 48);
        Instant originalDue = loan.getDueAt();

        renew(studentToken, loan.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renewalCount").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(Duration.between(originalDue, loans.findById(loan.getId()).orElseThrow().getDueAt()))
                .isEqualTo(Duration.ofDays(14));
    }

    @Test
    void aLoanCanBeRenewedOnlyAFixedNumberOfTimes() throws Exception {
        BorrowRecord loan = loan(book(), student, 48);

        renew(studentToken, loan.getId()).andExpect(status().isOk());
        renew(studentToken, loan.getId()).andExpect(status().isOk());
        renew(studentToken, loan.getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("renewed 2 times")));
    }

    @Test
    void anOverdueLoanCannotBeRenewed() throws Exception {
        BorrowRecord loan = loan(book(), student, -24);

        renew(studentToken, loan.getId()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aReturnedLoanCannotBeRenewed() throws Exception {
        BorrowRecord loan = loan(book(), student, 48);
        loan.markReturned(Instant.now());
        loans.saveAndFlush(loan);

        renew(studentToken, loan.getId()).andExpect(status().isConflict());
    }

    @Test
    void aLoanCannotBeRenewedWhileAnotherStudentIsWaitingForTheTitle() throws Exception {
        Book book = book();
        BorrowRecord loan = loan(book, student, 48);
        ReservationResponse waiting = reservationService.reserve(otherStudent.getId(), book.getId());

        renew(studentToken, loan.getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("waiting")));

        reservationService.cancel(waiting.id(), new AuthenticatedUser(otherStudent.getId(), otherStudent.getEmail(), Role.STUDENT));
        renew(studentToken, loan.getId()).andExpect(status().isOk());
    }

    @Test
    void anotherStudentCannotRenewButStaffCan() throws Exception {
        BorrowRecord loan = loan(book(), student, 48);

        renew(otherToken, loan.getId()).andExpect(status().isNotFound());
        renew(librarian, loan.getId()).andExpect(status().isOk());
        renew(studentToken, 999_999L).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ listings

    @Test
    void myBorrowingsListsOnlyMyLoansNewestFirstAndCanShowJustTheActiveOnes() throws Exception {
        BorrowRecord returned = loan(book(), student, 48);
        returned.markReturned(Instant.now());
        loans.saveAndFlush(returned);
        BorrowRecord active = loan(book(), student, 96);
        loan(book(), otherStudent, 48);

        mvc.perform(get("/api/v1/loans/mine").header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(active.getId()))
                .andExpect(jsonPath("$.content[1].status").value("RETURNED"));
        mvc.perform(get("/api/v1/loans/mine").param("activeOnly", "true").header("Authorization", studentToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(active.getId()));
    }

    @Test
    void overdueLoansAreMarkedOverdueInTheListing() throws Exception {
        loan(book(), student, -72);

        mvc.perform(get("/api/v1/loans/mine").header("Authorization", studentToken))
                .andExpect(jsonPath("$.content[0].status").value("OVERDUE"));
    }

    @Test
    void staffSeeOverdueActiveAndAllLoansAndCanFilterByStudent() throws Exception {
        BorrowRecord overdue = loan(book(), student, -72);
        BorrowRecord active = loan(book(), otherStudent, 72);
        BorrowRecord returned = loan(book(), student, 72);
        returned.markReturned(Instant.now());
        loans.saveAndFlush(returned);

        mvc.perform(get("/api/v1/loans").param("scope", "OVERDUE").header("Authorization", librarian))
                .andExpect(jsonPath("$.content[?(@.id==" + overdue.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.id==" + active.getId() + ")]").doesNotExist());
        mvc.perform(get("/api/v1/loans").param("scope", "ACTIVE").header("Authorization", librarian))
                .andExpect(jsonPath("$.content[?(@.id==" + active.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.id==" + returned.getId() + ")]").doesNotExist());
        mvc.perform(get("/api/v1/loans").param("userId", student.getId().toString()).header("Authorization", librarian))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/loans").param("userId", student.getId().toString()).param("scope", "ACTIVE")
                        .header("Authorization", librarian))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void studentsCannotListEveryonesLoans() throws Exception {
        mvc.perform(get("/api/v1/loans").header("Authorization", studentToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/loans/mine").header("Authorization", librarian)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/loans/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSingleLoanShowsItsFineToTheOwnerAndIsHiddenFromOtherStudents() throws Exception {
        BorrowRecord loan = loan(book(), student, -72);
        loan.markReturned(Instant.now());
        loans.saveAndFlush(loan);
        fines.saveAndFlush(new Fine(loan, student, new BigDecimal("0.75")));

        mvc.perform(get("/api/v1/loans/" + loan.getId()).header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineAmount").value(0.75))
                .andExpect(jsonPath("$.bookTitle").isNotEmpty());
        mvc.perform(get("/api/v1/loans/" + loan.getId()).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/loans/" + loan.getId()).header("Authorization", librarian)).andExpect(status().isOk());
    }
}
