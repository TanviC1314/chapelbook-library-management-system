package com.chapelbook.borrowing;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.chapelbook.fine.FineStatus;
import com.chapelbook.reservation.ReservationResponse;
import com.chapelbook.reservation.ReservationService;
import com.chapelbook.reservation.ReservationStatus;
import com.chapelbook.security.AuthenticatedUser;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
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
class LoanReturnIntegrationTest {

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
    private String librarian;

    @BeforeEach
    void setUp() {
        category = categories.saveAndFlush(new Category("Return cat " + SEQ.incrementAndGet(), null));
        student = auth.createUser(Role.STUDENT);
        otherStudent = auth.createUser(Role.STUDENT);
        librarianUser = auth.createUser(Role.LIBRARIAN);
        librarian = auth.bearer(librarianUser);
    }

    private Book book() {
        return books.saveAndFlush(new Book("Return book " + SEQ.incrementAndGet(), "Author", category));
    }

    /** A copy that is currently on loan to the student and due the given number of hours from now (negative = late). */
    private BookCopy onLoan(Book book, User to, long dueInHours) {
        BookCopy copy = copies.saveAndFlush(new BookCopy(book, "RT-" + SEQ.incrementAndGet() + "-" + System.nanoTime(), null));
        copy.setStatus(CopyStatus.ON_LOAN);
        copies.saveAndFlush(copy);
        Instant due = Instant.now().plus(dueInHours, ChronoUnit.HOURS);
        loans.saveAndFlush(new BorrowRecord(copy, to, librarianUser, null, due.minus(14, ChronoUnit.DAYS), due));
        return copy;
    }

    private ResultActions giveBack(String token, String barcode) throws Exception {
        return mvc.perform(post("/api/v1/loans/return").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"copyBarcode\":\"" + barcode + "\"}"));
    }

    @Test
    void returningOnTimeClosesTheLoanPutsTheCopyBackAndRaisesNoFine() throws Exception {
        BookCopy copy = onLoan(book(), student, 24);

        giveBack(librarian, copy.getBarcode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.returnedAt").isNotEmpty())
                .andExpect(jsonPath("$.fineAmount").doesNotExist());

        assertThat(copies.findById(copy.getId()).orElseThrow().getStatus()).isEqualTo(CopyStatus.AVAILABLE);
        assertThat(loans.findByCopyIdAndReturnedAtIsNull(copy.getId())).isEmpty();
        assertThat(fines.findAll()).noneMatch(f -> f.getUser().getId().equals(student.getId()));
    }

    @Test
    void returningLateRaisesAFineForEachStartedDay() throws Exception {
        BookCopy copy = onLoan(book(), student, -60); // 2.5 days late: three started days

        giveBack(librarian, copy.getBarcode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fineAmount").value(0.75));

        Fine fine = fines.findByBorrowRecordId(
                loans.findAll().stream().filter(l -> l.getCopy().getId().equals(copy.getId())).findFirst().orElseThrow().getId())
                .orElseThrow();
        assertThat(fine.getAmount()).isEqualByComparingTo("0.75");
        assertThat(fine.getStatus()).isEqualTo(FineStatus.UNPAID);
        assertThat(fine.getUser().getId()).isEqualTo(student.getId());
    }

    @Test
    void theFineIsCappedPerLoan() throws Exception {
        BookCopy copy = onLoan(book(), student, -100 * 24);

        giveBack(librarian, copy.getBarcode()).andExpect(jsonPath("$.fineAmount").value(10.0));
    }

    @Test
    void returningAnOverdueBookClearsTheOverdueBlockButABigFineStillBlocksLending() throws Exception {
        BookCopy copy = onLoan(book(), student, -100 * 24);
        giveBack(librarian, copy.getBarcode()).andExpect(status().isOk());
        BookCopy another = copies.saveAndFlush(new BookCopy(book(), "RT-NEXT-" + System.nanoTime(), null));

        // No overdue book any more, but 10.00 unpaid is above the 5.00 limit.
        mvc.perform(post("/api/v1/loans").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + student.getId() + ",\"copyBarcode\":\"" + another.getBarcode() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("unpaid fines")));
    }

    @Test
    void aReturnedCopyGoesStraightToTheStudentWaitingForIt() throws Exception {
        Book book = book();
        BookCopy copy = onLoan(book, student, 24);
        ReservationResponse waiting = reservationService.reserve(otherStudent.getId(), book.getId());
        assertThat(waiting.status()).isEqualTo(ReservationStatus.PENDING);

        giveBack(librarian, copy.getBarcode()).andExpect(status().isOk());

        var served = reservationService.get(waiting.id(),
                new AuthenticatedUser(otherStudent.getId(), otherStudent.getEmail(), Role.STUDENT));
        assertThat(served.status()).isEqualTo(ReservationStatus.READY);
        assertThat(served.copyBarcode()).isEqualTo(copy.getBarcode());
        assertThat(copies.findById(copy.getId()).orElseThrow().getStatus()).isEqualTo(CopyStatus.HELD);
    }

    @Test
    void aCopyThatIsNotOnLoanOrDoesNotExistIsRefused() throws Exception {
        BookCopy shelved = copies.saveAndFlush(new BookCopy(book(), "RT-SHELF-" + System.nanoTime(), null));

        giveBack(librarian, shelved.getBarcode()).andExpect(status().isConflict());
        giveBack(librarian, "NO-SUCH-COPY").andExpect(status().isNotFound());
    }

    @Test
    void returningTheSameCopyTwiceIsAConflictAndRaisesOnlyOneFine() throws Exception {
        BookCopy copy = onLoan(book(), student, -48);
        giveBack(librarian, copy.getBarcode()).andExpect(status().isOk());

        giveBack(librarian, copy.getBarcode()).andExpect(status().isConflict());

        assertThat(fines.findAll().stream().filter(f -> f.getUser().getId().equals(student.getId())).count()).isEqualTo(1);
    }

    @Test
    void onlyStaffCanTakeBooksBack() throws Exception {
        BookCopy copy = onLoan(book(), student, 24);

        giveBack(auth.bearer(student), copy.getBarcode()).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/loans/return").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/loans/return").header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"copyBarcode\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
