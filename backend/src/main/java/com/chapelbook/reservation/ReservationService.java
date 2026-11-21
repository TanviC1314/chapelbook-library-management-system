package com.chapelbook.reservation;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.book.CopyStatus;
import com.chapelbook.borrowing.BorrowRecordRepository;
import com.chapelbook.common.BusinessRuleException;
import com.chapelbook.common.ConflictException;
import com.chapelbook.common.PageResponse;
import com.chapelbook.common.ResourceNotFoundException;
import com.chapelbook.config.LibraryProperties;
import com.chapelbook.security.AuthenticatedUser;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reservations work as a first-come first-served queue per title.
 *
 * <ul>
 *   <li>A reservation joins the queue (PENDING). If a copy is on the shelf and nobody is ahead, the
 *       oldest waiting student immediately gets that copy held for them (READY) for a few days.</li>
 *   <li>When a copy comes back, is added, or a hold is cancelled or expires, the same routine hands it
 *       to the next student in line.</li>
 * </ul>
 *
 * <p><b>Concurrency.</b> Every operation that changes a title's queue first locks that title's row
 * ({@code SELECT ... FOR UPDATE}). Requests for the same title therefore run one at a time, so two
 * students racing for the last copy cannot both get it and the queue cannot be served out of order.
 * Requests for different titles do not block each other.
 */
@Service
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE = List.of(ReservationStatus.PENDING, ReservationStatus.READY);

    private final ReservationRepository reservations;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final UserRepository users;
    private final BorrowRecordRepository loans;
    private final LibraryProperties policy;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations, BookRepository books, BookCopyRepository copies,
            UserRepository users, BorrowRecordRepository loans, LibraryProperties policy, Clock clock) {
        this.reservations = reservations;
        this.books = books;
        this.copies = copies;
        this.users = users;
        this.loans = loans;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public ReservationResponse reserve(long userId, long bookId) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        Book book = lockBook(bookId);
        if (reservations.existsByUserIdAndBookIdAndStatusIn(userId, bookId, ACTIVE)) {
            throw new ConflictException("You already have an active reservation for this book.");
        }
        if (loans.existsByUserIdAndCopyBookIdAndReturnedAtIsNull(userId, bookId)) {
            throw new BusinessRuleException("You already have this book on loan.");
        }
        if (reservations.countByUserIdAndStatusIn(userId, ACTIVE) >= policy.maxActiveReservations()) {
            throw new BusinessRuleException(
                    "You can have at most " + policy.maxActiveReservations() + " active reservations.");
        }
        Reservation reservation = reservations.saveAndFlush(new Reservation(user, book));
        serveQueue(book);
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse cancel(long reservationId, AuthenticatedUser caller) {
        Book book = lockBook(bookIdOf(reservationId));
        Reservation reservation = visibleTo(caller, reservationId);
        if (!reservation.getStatus().isActive()) {
            throw new ConflictException("This reservation is already " + reservation.getStatus().name().toLowerCase() + ".");
        }
        releaseHeldCopy(reservation);
        reservation.cancel();
        reservations.saveAndFlush(reservation);
        serveQueue(book);
        return toResponse(reservation);
    }

    /**
     * Expires one READY reservation whose collection deadline has passed and offers its copy to the next
     * student. Returns false (and changes nothing) if it is no longer due, e.g. it was collected meanwhile.
     */
    @Transactional
    public boolean expire(long reservationId) {
        Optional<Long> bookId = reservations.findBookId(reservationId);
        if (bookId.isEmpty()) {
            return false;
        }
        Book book = lockBook(bookId.get());
        Reservation reservation = reservations.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.READY
                || !reservation.getReadyUntil().isBefore(clock.instant())) {
            return false;
        }
        releaseHeldCopy(reservation);
        reservation.expire();
        reservations.saveAndFlush(reservation);
        serveQueue(book);
        return true;
    }

    /**
     * A student is borrowing a title: if they had a live reservation for it, it is now fulfilled. If they were
     * holding a different copy than the one they were given, that copy goes back on the shelf. The caller must
     * hold the title's lock and call {@link #serveQueue(long)} afterwards.
     */
    @Transactional
    public Optional<Reservation> fulfilForLoan(long userId, BookCopy borrowedCopy, long bookId) {
        Optional<Reservation> live = reservations.findFirstByUserIdAndBookIdAndStatusIn(userId, bookId, ACTIVE);
        live.ifPresent(reservation -> {
            boolean holdingAnotherCopy = reservation.getStatus() == ReservationStatus.READY
                    && reservation.getCopy() != null && !reservation.getCopy().getId().equals(borrowedCopy.getId());
            if (holdingAnotherCopy) {
                releaseHeldCopy(reservation);
            }
            reservation.markFulfilled(borrowedCopy);
            reservations.saveAndFlush(reservation);
        });
        return live;
    }

    /** The reservation a HELD copy is being kept for, if any. */
    @Transactional(readOnly = true)
    public Optional<Reservation> holderOf(long copyId) {
        return reservations.findByCopyIdAndStatus(copyId, ReservationStatus.READY);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredIds() {
        return reservations.findByStatusAndReadyUntilBefore(ReservationStatus.READY, clock.instant())
                .stream().map(Reservation::getId).toList();
    }

    /**
     * Hands every free copy of a title to the students waiting for it, oldest reservation first. Safe to call
     * whenever a copy may have become available (a return, a new copy, a cancelled or expired hold).
     */
    @Transactional
    public void serveQueue(long bookId) {
        serveQueue(lockBook(bookId));
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(long id, AuthenticatedUser caller) {
        return toResponse(visibleTo(caller, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> mine(long userId, Pageable pageable) {
        return PageResponse.of(reservations.findByUserId(userId, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> list(ReservationStatus status, Pageable pageable) {
        var page = status == null ? reservations.findAll(pageable) : reservations.findByStatus(status, pageable);
        return PageResponse.of(page, this::toResponse);
    }

    // ------------------------------------------------------------------ internals

    /** Caller must already hold the title's lock. */
    private void serveQueue(Book book) {
        while (true) {
            Optional<Reservation> next = reservations
                    .findFirstByBookIdAndStatusOrderByReservedAtAscIdAsc(book.getId(), ReservationStatus.PENDING);
            if (next.isEmpty()) {
                return;
            }
            List<BookCopy> free = copies.findAvailableForUpdate(book.getId(), PageRequest.of(0, 1));
            if (free.isEmpty()) {
                return;
            }
            BookCopy copy = free.get(0);
            copy.setStatus(CopyStatus.HELD);
            copies.saveAndFlush(copy);
            next.get().markReady(copy, holdDeadline());
            reservations.saveAndFlush(next.get());
        }
    }

    private void releaseHeldCopy(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.READY && reservation.getCopy() != null) {
            copies.findByIdForUpdate(reservation.getCopy().getId()).ifPresent(copy -> {
                if (copy.getStatus() == CopyStatus.HELD) {
                    copy.setStatus(CopyStatus.AVAILABLE);
                    copies.saveAndFlush(copy);
                }
            });
        }
    }

    private Instant holdDeadline() {
        return clock.instant().plus(policy.reservationHoldDays(), ChronoUnit.DAYS);
    }

    private Book lockBook(long bookId) {
        return books.findByIdForUpdate(bookId).orElseThrow(() -> new ResourceNotFoundException("Book not found."));
    }

    private long bookIdOf(long reservationId) {
        return reservations.findBookId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found."));
    }

    /** Students only ever see their own reservations; someone else's looks like it does not exist. */
    private Reservation visibleTo(AuthenticatedUser caller, long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found."));
        boolean staff = caller.role() == Role.LIBRARIAN || caller.role() == Role.ADMIN;
        if (!staff && !reservation.getUser().getId().equals(caller.id())) {
            throw new ResourceNotFoundException("Reservation not found.");
        }
        return reservation;
    }

    private ReservationResponse toResponse(Reservation reservation) {
        Long position = null;
        if (reservation.getStatus() == ReservationStatus.PENDING) {
            position = reservations.countPendingAhead(reservation.getBook().getId(), reservation.getReservedAt(),
                    reservation.getId()) + 1;
        }
        return ReservationResponse.from(reservation, position);
    }
}
