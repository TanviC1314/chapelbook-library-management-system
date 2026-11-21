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
import com.chapelbook.reservation.Reservation;
import com.chapelbook.reservation.ReservationRepository;
import com.chapelbook.reservation.ReservationResponse;
import com.chapelbook.reservation.ReservationService;
import com.chapelbook.reservation.ReservationStatus;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class LoanIssueIntegrationTest {

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
    private ReservationRepository reservations;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private FineRepository fines;

    private Category category;
    private User student;
    private User otherStudent;
    private User librarianUser;
    private String librarian;

    @BeforeEach
    void setUp() {
        category = categories.saveAndFlush(new Category("Loan cat " + SEQ.incrementAndGet(), null));
        student = auth.createUser(Role.STUDENT);
        otherStudent = auth.createUser(Role.STUDENT);
        librarianUser = auth.createUser(Role.LIBRARIAN);
        librarian = auth.bearer(librarianUser);
    }

    private Book book() {
        return books.saveAndFlush(new Book("Loan book " + SEQ.incrementAndGet(), "Author", category));
    }

    private BookCopy copyOf(Book book, CopyStatus status) {
        BookCopy copy = copies.saveAndFlush(new BookCopy(book, "LN-" + SEQ.incrementAndGet() + "-" + System.nanoTime(), null));
        copy.setStatus(status);
        return copies.saveAndFlush(copy);
    }

    private ResultActions issue(String token, long userId, String barcode) throws Exception {
        return mvc.perform(post("/api/v1/loans").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + ",\"copyBarcode\":\"" + barcode + "\"}"));
    }

    private static AuthenticatedUser as(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }

    @Test
    void librarianLendsAnAvailableCopyForTheLoanPeriod() throws Exception {
        BookCopy copy = copyOf(book(), CopyStatus.AVAILABLE);

        issue(librarian, student.getId(), copy.getBarcode())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.copyBarcode").value(copy.getBarcode()))
                .andExpect(jsonPath("$.userId").value(student.getId()))
                .andExpect(jsonPath("$.renewalCount").value(0))
                .andExpect(jsonPath("$.returnedAt").doesNotExist());

        BorrowRecord loan = loans.findByCopyIdAndReturnedAtIsNull(copy.getId()).orElseThrow();
        assertThat(copies.findById(copy.getId()).orElseThrow().getStatus()).isEqualTo(CopyStatus.ON_LOAN);
        assertThat(Duration.between(loan.getBorrowedAt(), loan.getDueAt())).isEqualTo(Duration.ofDays(14));
        assertThat(loan.getIssuedBy().getId()).isEqualTo(librarianUser.getId());
    }

    @Test
    void lendingReducesTheTitlesAvailability() throws Exception {
        Book book = book();
        BookCopy copy = copyOf(book, CopyStatus.AVAILABLE);
        copyOf(book, CopyStatus.AVAILABLE);

        issue(librarian, student.getId(), copy.getBarcode()).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/books/" + book.getId()).header("Authorization", auth.bearer(student)))
                .andExpect(jsonPath("$.totalCopies").value(2))
                .andExpect(jsonPath("$.availableCopies").value(1));
    }

    @Test
    void studentsCannotIssueBooks() throws Exception {
        BookCopy copy = copyOf(book(), CopyStatus.AVAILABLE);

        issue(auth.bearer(student), student.getId(), copy.getBarcode()).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/loans").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validationAndUnknownThingsAreReportedClearly() throws Exception {
        mvc.perform(post("/api/v1/loans").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":null,\"copyBarcode\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists())
                .andExpect(jsonPath("$.errors.copyBarcode").exists());
        issue(librarian, student.getId(), "NO-SUCH-BARCODE").andExpect(status().isNotFound());
        BookCopy copy = copyOf(book(), CopyStatus.AVAILABLE);
        issue(librarian, 999_999L, copy.getBarcode()).andExpect(status().isNotFound());
    }

    @Test
    void booksCanOnlyBeIssuedToActiveStudents() throws Exception {
        BookCopy copy = copyOf(book(), CopyStatus.AVAILABLE);
        issue(librarian, librarianUser.getId(), copy.getBarcode()).andExpect(status().isUnprocessableEntity());

        student.setActive(false);
        issue(librarian, student.getId(), copy.getBarcode()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aCopyAlreadyOnLoanOrOutOfCirculationCannotBeLent() throws Exception {
        BookCopy lent = copyOf(book(), CopyStatus.AVAILABLE);
        issue(librarian, student.getId(), lent.getBarcode()).andExpect(status().isCreated());

        issue(librarian, otherStudent.getId(), lent.getBarcode()).andExpect(status().isConflict());
        issue(librarian, otherStudent.getId(), copyOf(book(), CopyStatus.WITHDRAWN).getBarcode())
                .andExpect(status().isUnprocessableEntity());
        issue(librarian, otherStudent.getId(), copyOf(book(), CopyStatus.LOST).getBarcode())
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aHeldCopyGoesOnlyToTheStudentItIsHeldFor() throws Exception {
        Book book = book();
        BookCopy copy = copyOf(book, CopyStatus.AVAILABLE);
        ReservationResponse held = reservationService.reserve(student.getId(), book.getId());
        assertThat(held.status()).isEqualTo(ReservationStatus.READY);

        issue(librarian, otherStudent.getId(), copy.getBarcode())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("This copy is being held for another student."));
        issue(librarian, student.getId(), copy.getBarcode()).andExpect(status().isCreated());

        Reservation reservation = reservations.findById(held.id()).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(loans.findByCopyIdAndReturnedAtIsNull(copy.getId()).orElseThrow().getReservation().getId())
                .isEqualTo(held.id());
    }

    @Test
    void borrowingADifferentCopyThanTheOneHeldReleasesTheHeldOneToTheNextStudent() throws Exception {
        Book book = book();
        BookCopy heldCopy = copyOf(book, CopyStatus.AVAILABLE);
        ReservationResponse first = reservationService.reserve(student.getId(), book.getId());
        ReservationResponse second = reservationService.reserve(otherStudent.getId(), book.getId());
        assertThat(second.status()).isEqualTo(ReservationStatus.PENDING);
        BookCopy otherCopy = copyOf(book, CopyStatus.AVAILABLE); // a copy appears on the shelf unexpectedly

        issue(librarian, student.getId(), otherCopy.getBarcode()).andExpect(status().isCreated());

        assertThat(reservations.findById(first.id()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        Reservation next = reservations.findById(second.id()).orElseThrow();
        assertThat(next.getStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(next.getCopy().getId()).isEqualTo(heldCopy.getId());
        assertThat(copies.findById(heldCopy.getId()).orElseThrow().getStatus()).isEqualTo(CopyStatus.HELD);
    }

    @Test
    void thereIsALimitOnBooksOnLoanAtOnce() throws Exception {
        for (int i = 0; i < 5; i++) {
            issue(librarian, student.getId(), copyOf(book(), CopyStatus.AVAILABLE).getBarcode())
                    .andExpect(status().isCreated());
        }

        issue(librarian, student.getId(), copyOf(book(), CopyStatus.AVAILABLE).getBarcode())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("maximum of 5")));
    }

    @Test
    void aStudentCannotBorrowTwoCopiesOfTheSameTitle() throws Exception {
        Book book = book();
        issue(librarian, student.getId(), copyOf(book, CopyStatus.AVAILABLE).getBarcode()).andExpect(status().isCreated());

        issue(librarian, student.getId(), copyOf(book, CopyStatus.AVAILABLE).getBarcode())
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void anOverdueBookBlocksFurtherLending() throws Exception {
        BookCopy old = copyOf(book(), CopyStatus.ON_LOAN);
        Instant now = Instant.now();
        loans.saveAndFlush(new BorrowRecord(old, student, librarianUser, null,
                now.minus(30, ChronoUnit.DAYS), now.minus(16, ChronoUnit.DAYS)));

        issue(librarian, student.getId(), copyOf(book(), CopyStatus.AVAILABLE).getBarcode())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("overdue")));
    }

    @Test
    void unpaidFinesAboveTheLimitBlockLendingButSmallOnesDoNot() throws Exception {
        Instant now = Instant.now();
        BorrowRecord returned = loans.saveAndFlush(new BorrowRecord(copyOf(book(), CopyStatus.AVAILABLE), student,
                librarianUser, null, now.minus(40, ChronoUnit.DAYS), now.minus(26, ChronoUnit.DAYS)));
        returned.markReturned(now.minus(20, ChronoUnit.DAYS));
        loans.saveAndFlush(returned);
        Fine fine = fines.saveAndFlush(new Fine(returned, student, new BigDecimal("5.00")));

        // exactly at the limit is still allowed ("above 5.00" blocks)
        issue(librarian, student.getId(), copyOf(book(), CopyStatus.AVAILABLE).getBarcode()).andExpect(status().isCreated());

        BorrowRecord returned2 = loans.saveAndFlush(new BorrowRecord(copyOf(book(), CopyStatus.AVAILABLE), student,
                librarianUser, null, now.minus(40, ChronoUnit.DAYS), now.minus(26, ChronoUnit.DAYS)));
        returned2.markReturned(now.minus(20, ChronoUnit.DAYS));
        loans.saveAndFlush(returned2);
        fines.saveAndFlush(new Fine(returned2, student, new BigDecimal("0.50")));

        issue(librarian, student.getId(), copyOf(book(), CopyStatus.AVAILABLE).getBarcode())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("unpaid fines")));

        fine.markPaid(now);
        fines.saveAndFlush(fine);
        issue(librarian, student.getId(), copyOf(book(), CopyStatus.AVAILABLE).getBarcode()).andExpect(status().isCreated());
    }
}
