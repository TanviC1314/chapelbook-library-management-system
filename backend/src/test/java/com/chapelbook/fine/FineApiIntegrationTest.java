package com.chapelbook.fine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.borrowing.BorrowRecord;
import com.chapelbook.borrowing.BorrowRecordRepository;
import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import java.math.BigDecimal;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class FineApiIntegrationTest {

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

    private Category category;
    private User student;
    private User otherStudent;
    private User librarianUser;
    private String studentToken;
    private String otherToken;
    private String librarian;
    private String admin;

    @BeforeEach
    void setUp() {
        category = categories.saveAndFlush(new Category("Fine cat " + SEQ.incrementAndGet(), null));
        student = auth.createUser(Role.STUDENT);
        otherStudent = auth.createUser(Role.STUDENT);
        librarianUser = auth.createUser(Role.LIBRARIAN);
        studentToken = auth.bearer(student);
        otherToken = auth.bearer(otherStudent);
        librarian = auth.bearer(librarianUser);
        admin = auth.bearerFor(Role.ADMIN);
    }

    private Fine fine(User owner, String amount) {
        Book book = books.saveAndFlush(new Book("Fine book " + SEQ.incrementAndGet(), "Author", category));
        BookCopy copy = copies.saveAndFlush(new BookCopy(book, "FN-" + SEQ.incrementAndGet() + "-" + System.nanoTime(), null));
        Instant due = Instant.now().minus(10, ChronoUnit.DAYS);
        BorrowRecord loan = loans.saveAndFlush(new BorrowRecord(copy, owner, librarianUser, null,
                due.minus(14, ChronoUnit.DAYS), due));
        loan.markReturned(Instant.now().minus(1, ChronoUnit.DAYS));
        loans.saveAndFlush(loan);
        return fines.saveAndFlush(new Fine(loan, owner, new BigDecimal(amount)));
    }

    @Test
    void aStudentSeesOnlyTheirOwnFinesAndCanFilterByStatus() throws Exception {
        Fine unpaid = fine(student, "2.00");
        Fine paid = fine(student, "1.50");
        paid.markPaid(Instant.now());
        fines.saveAndFlush(paid);
        fine(otherStudent, "9.00");

        mvc.perform(get("/api/v1/fines/mine").header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].bookTitle").isNotEmpty());
        mvc.perform(get("/api/v1/fines/mine").param("status", "UNPAID").header("Authorization", studentToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(unpaid.getId()));
    }

    @Test
    void theBalanceCountsOnlyUnpaidFinesAndSaysWhetherBorrowingIsBlocked() throws Exception {
        fine(student, "2.00");
        fine(student, "1.50");
        Fine paid = fine(student, "9.00");
        paid.markPaid(Instant.now());
        fines.saveAndFlush(paid);

        mvc.perform(get("/api/v1/fines/mine/balance").header("Authorization", studentToken))
                .andExpect(jsonPath("$.unpaid").value(3.5))
                .andExpect(jsonPath("$.borrowingBlockedAbove").value(5.0))
                .andExpect(jsonPath("$.blocked").value(false));

        fine(student, "2.00");
        mvc.perform(get("/api/v1/fines/mine/balance").header("Authorization", studentToken))
                .andExpect(jsonPath("$.unpaid").value(5.5))
                .andExpect(jsonPath("$.blocked").value(true));
    }

    @Test
    void staffListFinesAndFilterByStudentAndStatus() throws Exception {
        Fine mine = fine(student, "2.00");
        fine(otherStudent, "3.00");

        mvc.perform(get("/api/v1/fines").param("userId", student.getId().toString()).header("Authorization", librarian))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine.getId()));
        mvc.perform(get("/api/v1/fines").param("status", "UNPAID").header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/fines").header("Authorization", studentToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/fines/mine").header("Authorization", librarian)).andExpect(status().isForbidden());
    }

    @Test
    void aLibrarianRecordsPaymentOnceAndTheBalanceDrops() throws Exception {
        Fine fine = fine(student, "2.00");

        mvc.perform(post("/api/v1/fines/" + fine.getId() + "/pay").header("Authorization", librarian))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        mvc.perform(post("/api/v1/fines/" + fine.getId() + "/pay").header("Authorization", librarian))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/fines/mine/balance").header("Authorization", studentToken))
                .andExpect(jsonPath("$.unpaid").value(0));
    }

    @Test
    void studentsCannotMarkTheirOwnFinesAsPaidOrWaiveThem() throws Exception {
        Fine fine = fine(student, "2.00");

        mvc.perform(post("/api/v1/fines/" + fine.getId() + "/pay").header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fines/" + fine.getId() + "/waive").header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fines/" + fine.getId() + "/pay")).andExpect(status().isUnauthorized());
        assertThat(fines.findById(fine.getId()).orElseThrow().getStatus()).isEqualTo(FineStatus.UNPAID);
    }

    @Test
    void onlyAnAdminCanWaiveAFineAndOnlyWhileItIsUnpaid() throws Exception {
        Fine fine = fine(student, "2.00");

        mvc.perform(post("/api/v1/fines/" + fine.getId() + "/waive").header("Authorization", librarian))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fines/" + fine.getId() + "/waive").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAIVED"));
        mvc.perform(post("/api/v1/fines/" + fine.getId() + "/waive").header("Authorization", admin))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/fines/" + fine.getId() + "/pay").header("Authorization", librarian))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/fines/mine/balance").header("Authorization", studentToken))
                .andExpect(jsonPath("$.unpaid").value(0));
    }

    @Test
    void anUnknownFineIsA404() throws Exception {
        mvc.perform(post("/api/v1/fines/999999/pay").header("Authorization", librarian)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/fines/999999/waive").header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test
    void anotherStudentsFinesNeverAppearInMine() throws Exception {
        Fine theirs = fine(otherStudent, "4.00");

        mvc.perform(get("/api/v1/fines/mine").header("Authorization", studentToken))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/fines/mine").header("Authorization", otherToken))
                .andExpect(jsonPath("$.content[0].id").value(theirs.getId()));
    }
}
