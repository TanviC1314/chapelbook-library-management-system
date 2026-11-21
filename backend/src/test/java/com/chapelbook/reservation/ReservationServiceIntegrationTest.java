package com.chapelbook.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.book.CopyStatus;
import com.chapelbook.borrowing.BorrowRecord;
import com.chapelbook.borrowing.BorrowRecordRepository;
import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.common.BusinessRuleException;
import com.chapelbook.common.ConflictException;
import com.chapelbook.common.ResourceNotFoundException;
import com.chapelbook.security.AuthenticatedUser;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** The reservation queue rules, tested directly on the service against real PostgreSQL. */
@SpringBootTest
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class ReservationServiceIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private ReservationService service;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private BookRepository books;
    @Autowired
    private BookCopyRepository copies;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private BorrowRecordRepository loans;
    @Autowired
    private TestAuth auth;

    private Category category;
    private User ann;
    private User bob;
    private User cat;
    private User librarian;

    @BeforeEach
    void setUp() {
        category = categories.saveAndFlush(new Category("Reservation cat " + SEQ.incrementAndGet(), null));
        ann = auth.createUser(Role.STUDENT);
        bob = auth.createUser(Role.STUDENT);
        cat = auth.createUser(Role.STUDENT);
        librarian = auth.createUser(Role.LIBRARIAN);
    }

    private Book book(int copyCount) {
        Book book = books.saveAndFlush(new Book("Reserve me " + SEQ.incrementAndGet(), "Author", category));
        for (int i = 0; i < copyCount; i++) {
            copies.saveAndFlush(new BookCopy(book, "RS-" + SEQ.incrementAndGet(), null));
        }
        return book;
    }

    private static AuthenticatedUser as(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }

    private CopyStatus statusOfOnlyCopy(Book book) {
        copies.flush();
        return copies.findByBookIdOrderByBarcode(book.getId()).get(0).getStatus();
    }

    @Test
    void withACopyOnTheShelfTheStudentGetsItHeldStraightAway() {
        Book book = book(1);

        ReservationResponse result = service.reserve(ann.getId(), book.getId());

        assertThat(result.status()).isEqualTo(ReservationStatus.READY);
        assertThat(result.copyBarcode()).startsWith("RS-");
        assertThat(result.queuePosition()).isNull();
        assertThat(result.readyUntil()).isBetween(
                Instant.now().plus(3, ChronoUnit.DAYS).minus(Duration.ofMinutes(1)),
                Instant.now().plus(3, ChronoUnit.DAYS).plus(Duration.ofMinutes(1)));
        assertThat(statusOfOnlyCopy(book)).isEqualTo(CopyStatus.HELD);
    }

    @Test
    void withNoCopyAvailableTheStudentJoinsTheQueueInOrder() {
        Book book = book(0);

        ReservationResponse first = service.reserve(ann.getId(), book.getId());
        ReservationResponse second = service.reserve(bob.getId(), book.getId());

        assertThat(first.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(first.queuePosition()).isEqualTo(1);
        assertThat(second.queuePosition()).isEqualTo(2);
        assertThat(first.copyBarcode()).isNull();
    }

    @Test
    void theLastCopyGoesToTheFirstStudentAndTheNextOneWaits() {
        Book book = book(1);

        ReservationResponse first = service.reserve(ann.getId(), book.getId());
        ReservationResponse second = service.reserve(bob.getId(), book.getId());

        assertThat(first.status()).isEqualTo(ReservationStatus.READY);
        assertThat(second.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(second.queuePosition()).isEqualTo(1);
    }

    @Test
    void cancellingAHeldReservationPassesTheCopyToTheNextStudentInLine() {
        Book book = book(1);
        ReservationResponse first = service.reserve(ann.getId(), book.getId());
        ReservationResponse second = service.reserve(bob.getId(), book.getId());
        ReservationResponse third = service.reserve(cat.getId(), book.getId());

        ReservationResponse cancelled = service.cancel(first.id(), as(ann));

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(service.get(second.id(), as(bob)).status()).isEqualTo(ReservationStatus.READY);
        assertThat(service.get(third.id(), as(cat)).status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(service.get(third.id(), as(cat)).queuePosition()).isEqualTo(1);
        assertThat(statusOfOnlyCopy(book)).isEqualTo(CopyStatus.HELD);
    }

    @Test
    void cancellingWithNobodyWaitingPutsTheCopyBackOnTheShelf() {
        Book book = book(1);
        ReservationResponse held = service.reserve(ann.getId(), book.getId());

        service.cancel(held.id(), as(ann));

        assertThat(statusOfOnlyCopy(book)).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void cancellingAPendingReservationLeavesTheHolderAlone() {
        Book book = book(1);
        service.reserve(ann.getId(), book.getId());
        ReservationResponse waiting = service.reserve(bob.getId(), book.getId());

        service.cancel(waiting.id(), as(bob));

        assertThat(statusOfOnlyCopy(book)).isEqualTo(CopyStatus.HELD);
    }

    @Test
    void aSecondActiveReservationForTheSameBookIsRefusedButAllowedAfterCancelling() {
        Book book = book(0);
        ReservationResponse first = service.reserve(ann.getId(), book.getId());

        assertThatThrownBy(() -> service.reserve(ann.getId(), book.getId())).isInstanceOf(ConflictException.class);

        service.cancel(first.id(), as(ann));
        assertThat(service.reserve(ann.getId(), book.getId()).status()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void aStudentCannotReserveATitleTheyAlreadyHaveOnLoan() {
        Book book = book(1);
        BookCopy copy = copies.findByBookIdOrderByBarcode(book.getId()).get(0);
        copy.setStatus(CopyStatus.ON_LOAN);
        copies.saveAndFlush(copy);
        Instant now = Instant.now();
        loans.saveAndFlush(new BorrowRecord(copy, ann, librarian, null, now, now.plus(14, ChronoUnit.DAYS)));

        assertThatThrownBy(() -> service.reserve(ann.getId(), book.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("on loan");
    }

    @Test
    void thereIsALimitOnHowManyActiveReservationsOneStudentCanHold() {
        for (int i = 0; i < 5; i++) {
            service.reserve(ann.getId(), book(0).getId());
        }

        assertThatThrownBy(() -> service.reserve(ann.getId(), book(0).getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("at most 5");
    }

    @Test
    void anUnknownBookIsNotFound() {
        assertThatThrownBy(() -> service.reserve(ann.getId(), 999_999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anotherStudentsReservationLooksLikeItDoesNotExistButStaffCanCancelIt() {
        Book book = book(0);
        ReservationResponse annsReservation = service.reserve(ann.getId(), book.getId());

        assertThatThrownBy(() -> service.get(annsReservation.id(), as(bob))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.cancel(annsReservation.id(), as(bob))).isInstanceOf(ResourceNotFoundException.class);

        assertThat(service.cancel(annsReservation.id(), as(librarian)).status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancellingTwiceIsAConflict() {
        ReservationResponse reservation = service.reserve(ann.getId(), book(0).getId());
        service.cancel(reservation.id(), as(ann));

        assertThatThrownBy(() -> service.cancel(reservation.id(), as(ann)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("cancelled");
    }

    // ------------------------------------------------------------------ expiry

    private Reservation makeHoldOverdue(ReservationResponse held) {
        Reservation reservation = reservations.findById(held.id()).orElseThrow();
        reservation.markReady(reservation.getCopy(), Instant.now().minus(1, ChronoUnit.HOURS));
        return reservations.saveAndFlush(reservation);
    }

    @Test
    void anUncollectedHoldExpiresAndTheCopyGoesToTheNextStudent() {
        Book book = book(1);
        ReservationResponse first = service.reserve(ann.getId(), book.getId());
        ReservationResponse second = service.reserve(bob.getId(), book.getId());
        makeHoldOverdue(first);

        assertThat(service.findExpiredIds()).containsExactly(first.id());
        assertThat(service.expire(first.id())).isTrue();

        assertThat(service.get(first.id(), as(ann)).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(service.get(second.id(), as(bob)).status()).isEqualTo(ReservationStatus.READY);
        assertThat(statusOfOnlyCopy(book)).isEqualTo(CopyStatus.HELD);
    }

    @Test
    void anExpiredHoldWithNobodyWaitingReturnsTheCopyToTheShelf() {
        Book book = book(1);
        makeHoldOverdue(service.reserve(ann.getId(), book.getId()));
        Long id = service.findExpiredIds().get(0);

        service.expire(id);

        assertThat(statusOfOnlyCopy(book)).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void aHoldThatIsNotDueYetIsLeftAlone() {
        Book book = book(1);
        ReservationResponse held = service.reserve(ann.getId(), book.getId());

        assertThat(service.findExpiredIds()).doesNotContain(held.id());
        assertThat(service.expire(held.id())).isFalse();
        assertThat(service.get(held.id(), as(ann)).status()).isEqualTo(ReservationStatus.READY);
    }

    @Test
    void expiringSomethingThatDoesNotExistOrIsNotHeldChangesNothing() {
        assertThat(service.expire(999_999L)).isFalse();
        ReservationResponse pending = service.reserve(ann.getId(), book(0).getId());

        assertThat(service.expire(pending.id())).isFalse();
        assertThat(service.get(pending.id(), as(ann)).status()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void theExpiryJobExpiresDueHoldsAndSurvivesNothingToDo() {
        ReservationExpiryJob job = new ReservationExpiryJob(service);
        job.run(); // nothing due: must not fail

        Book book = book(1);
        makeHoldOverdue(service.reserve(ann.getId(), book.getId()));
        job.run();

        assertThat(statusOfOnlyCopy(book)).isEqualTo(CopyStatus.AVAILABLE);
    }
}
