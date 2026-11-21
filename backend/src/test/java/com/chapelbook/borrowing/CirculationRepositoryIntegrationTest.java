package com.chapelbook.borrowing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.fine.Fine;
import com.chapelbook.fine.FineRepository;
import com.chapelbook.fine.FineStatus;
import com.chapelbook.reservation.Reservation;
import com.chapelbook.reservation.ReservationRepository;
import com.chapelbook.reservation.ReservationStatus;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainerConfig.class)
class CirculationRepositoryIntegrationTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private BookRepository books;
    @Autowired
    private BookCopyRepository copies;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private BorrowRecordRepository loans;
    @Autowired
    private FineRepository fines;

    private User student;
    private User other;
    private User librarian;
    private Book book;
    private BookCopy copy1;
    private BookCopy copy2;

    @BeforeEach
    void setUp() {
        student = users.saveAndFlush(new User("circ-s@example.com", "h", "Student", Role.STUDENT, "C1"));
        other = users.saveAndFlush(new User("circ-o@example.com", "h", "Other", Role.STUDENT, "C2"));
        librarian = users.saveAndFlush(new User("circ-l@example.com", "h", "Librarian", Role.LIBRARIAN, null));
        Category category = categories.saveAndFlush(new Category("Circulation cat", null));
        book = books.saveAndFlush(new Book("Title", "Author", category));
        copy1 = copies.saveAndFlush(new BookCopy(book, "CIRC-1", null));
        copy2 = copies.saveAndFlush(new BookCopy(book, "CIRC-2", null));
    }

    private BorrowRecord lend(BookCopy copy, User to, Instant borrowedAt, Instant dueAt) {
        return loans.saveAndFlush(new BorrowRecord(copy, to, librarian, null, borrowedAt, dueAt));
    }

    // ------------------------------------------------------------ reservations

    @Test
    void reservationStartsPendingAndTheQueueIsOldestFirst() throws Exception {
        Reservation first = reservations.saveAndFlush(new Reservation(student, book));
        Thread.sleep(5);
        reservations.saveAndFlush(new Reservation(other, book));

        var next = reservations.findFirstByBookIdAndStatusOrderByReservedAtAscIdAsc(book.getId(), ReservationStatus.PENDING);

        assertThat(first.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(next).isPresent();
        assertThat(next.get().getId()).isEqualTo(first.getId());
        assertThat(reservations.countByBookIdAndStatus(book.getId(), ReservationStatus.PENDING)).isEqualTo(2);
    }

    @Test
    void detectsAnActiveReservationForTheSameStudentAndBook() {
        reservations.saveAndFlush(new Reservation(student, book));

        assertThat(reservations.existsByUserIdAndBookIdAndStatusIn(student.getId(), book.getId(),
                List.of(ReservationStatus.PENDING, ReservationStatus.READY))).isTrue();
        assertThat(reservations.existsByUserIdAndBookIdAndStatusIn(other.getId(), book.getId(),
                List.of(ReservationStatus.PENDING, ReservationStatus.READY))).isFalse();
    }

    @Test
    void aReadyReservationHoldsACopyAndCanExpire() {
        Reservation reservation = new Reservation(student, book);
        Instant deadline = Instant.now().minus(1, ChronoUnit.HOURS);
        reservation.markReady(copy1, deadline);
        reservations.saveAndFlush(reservation);

        var overdue = reservations.findByStatusAndReadyUntilBefore(ReservationStatus.READY, Instant.now());

        assertThat(overdue).extracting(Reservation::getId).contains(reservation.getId());
        assertThat(overdue.get(0).getCopy().getId()).isEqualTo(copy1.getId());
    }

    @Test
    void theDatabaseStillRejectsADuplicateActiveReservation() {
        reservations.saveAndFlush(new Reservation(student, book));

        assertThatThrownBy(() -> reservations.saveAndFlush(new Reservation(student, book)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aCancelledReservationFreesTheStudentToReserveAgain() {
        Reservation first = reservations.saveAndFlush(new Reservation(student, book));
        first.cancel();
        reservations.saveAndFlush(first);

        assertThat(reservations.saveAndFlush(new Reservation(student, book)).getId()).isNotNull();
    }

    // ------------------------------------------------------------ loans

    @Test
    void openLoansAreFoundByCopyAndStudent() {
        Instant now = Instant.now();
        BorrowRecord loan = lend(copy1, student, now, now.plus(14, ChronoUnit.DAYS));

        assertThat(loans.findByCopyIdAndReturnedAtIsNull(copy1.getId())).isPresent();
        assertThat(loans.countByUserIdAndReturnedAtIsNull(student.getId())).isEqualTo(1);
        assertThat(loans.findByUserIdAndReturnedAtIsNull(student.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(BorrowRecord::getId).containsExactly(loan.getId());

        loan.markReturned(now.plus(1, ChronoUnit.DAYS));
        loans.saveAndFlush(loan);

        assertThat(loans.findByCopyIdAndReturnedAtIsNull(copy1.getId())).isEmpty();
        assertThat(loans.countByUserIdAndReturnedAtIsNull(student.getId())).isZero();
    }

    @Test
    void overdueQueryReturnsOnlyOpenLoansPastDueOldestFirst() {
        Instant now = Instant.now();
        BorrowRecord veryOverdue = lend(copy1, student, now.minus(30, ChronoUnit.DAYS), now.minus(16, ChronoUnit.DAYS));
        BorrowRecord overdue = lend(copy2, other, now.minus(20, ChronoUnit.DAYS), now.minus(6, ChronoUnit.DAYS));
        Book another = books.saveAndFlush(new Book("Other", "Author", book.getCategory()));
        BookCopy notDue = copies.saveAndFlush(new BookCopy(another, "CIRC-3", null));
        lend(notDue, student, now, now.plus(14, ChronoUnit.DAYS));

        var page = loans.findOverdue(now, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(BorrowRecord::getId)
                .containsExactly(veryOverdue.getId(), overdue.getId());
    }

    @Test
    void domainHelpersReportOverdueAndRenewals() {
        Instant now = Instant.now();
        BorrowRecord loan = lend(copy1, student, now.minus(20, ChronoUnit.DAYS), now.minus(6, ChronoUnit.DAYS));

        assertThat(loan.isOverdueAt(now)).isTrue();
        loan.renew(now.plus(14, ChronoUnit.DAYS));
        assertThat(loan.getRenewalCount()).isEqualTo(1);
        assertThat(loan.isOverdueAt(now)).isFalse();
    }

    // ------------------------------------------------------------ fines

    @Test
    void storesAFineAndSumsWhatTheStudentOwes() {
        Instant now = Instant.now();
        BorrowRecord loan1 = lend(copy1, student, now.minus(30, ChronoUnit.DAYS), now.minus(16, ChronoUnit.DAYS));
        BorrowRecord loan2 = lend(copy2, student, now.minus(20, ChronoUnit.DAYS), now.minus(6, ChronoUnit.DAYS));
        fines.saveAndFlush(new Fine(loan1, student, new BigDecimal("3.20")));
        Fine second = fines.saveAndFlush(new Fine(loan2, student, new BigDecimal("1.20")));

        assertThat(fines.sumAmountByUserAndStatus(student.getId(), FineStatus.UNPAID))
                .isEqualByComparingTo("4.40");

        second.markPaid(now);
        fines.saveAndFlush(second);

        assertThat(fines.sumAmountByUserAndStatus(student.getId(), FineStatus.UNPAID)).isEqualByComparingTo("3.20");
        assertThat(fines.sumAmountByUserAndStatus(other.getId(), FineStatus.UNPAID)).isEqualByComparingTo("0");
        assertThat(fines.findByBorrowRecordId(loan2.getId())).isPresent();
        assertThat(Duration.between(second.getPaidAt(), now).abs()).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void aLoanCannotHaveTwoFines() {
        Instant now = Instant.now();
        BorrowRecord loan = lend(copy1, student, now.minus(30, ChronoUnit.DAYS), now.minus(16, ChronoUnit.DAYS));
        fines.saveAndFlush(new Fine(loan, student, new BigDecimal("3.20")));

        assertThat(fines.existsByBorrowRecordId(loan.getId())).isTrue();
        assertThatThrownBy(() -> fines.saveAndFlush(new Fine(loan, student, new BigDecimal("1.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
