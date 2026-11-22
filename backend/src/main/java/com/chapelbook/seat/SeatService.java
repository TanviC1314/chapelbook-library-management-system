package com.chapelbook.seat;

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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Study seats and seat bookings.
 *
 * <p><b>No double booking.</b> The service checks first, to give a friendly message in the normal case, but a
 * check followed by an insert is a race: two students can both see the seat free. The real guarantee is the
 * database's exclusion constraints (one per seat and one per student, on the booked time range). If two
 * requests slip through the check together, PostgreSQL rejects the second insert and the service turns that
 * into the same clear 409.
 *
 * <p>One student's bookings are additionally serialised by locking that student's row first. Without it, several
 * requests from the same student for different seats made PostgreSQL detect a deadlock between the two exclusion
 * constraints (found by the concurrency test). Different students never wait for each other.
 */
@Service
public class SeatService {

    private static final Duration MAX_BOOKING = Duration.ofHours(8);
    private static final Duration START_GRACE = Duration.ofMinutes(1);

    private final LibrarySeatRepository seats;
    private final SeatBookingRepository bookings;
    private final UserRepository users;
    private final LibraryProperties policy;
    private final Clock clock;

    public SeatService(LibrarySeatRepository seats, SeatBookingRepository bookings, UserRepository users,
            LibraryProperties policy, Clock clock) {
        this.seats = seats;
        this.bookings = bookings;
        this.users = users;
        this.policy = policy;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ seats

    /**
     * The seats, optionally filtered by zone. When {@code from} and {@code to} are both given each seat says
     * whether it is free for that whole window. Students only see seats that are in service.
     */
    @Transactional(readOnly = true)
    public List<SeatResponse> list(boolean includeOutOfService, String zone, Instant from, Instant to) {
        if ((from == null) != (to == null)) {
            throw new BusinessRuleException("Give both 'from' and 'to' to check availability, or neither.");
        }
        if (from != null && !to.isAfter(from)) {
            throw new BusinessRuleException("'to' must be after 'from'.");
        }
        Set<Long> booked = from == null ? Set.of() : new HashSet<>(bookings.findBookedSeatIds(from, to));
        return seats.findAllOrdered().stream()
                .filter(seat -> includeOutOfService || seat.getStatus() == SeatStatus.ACTIVE)
                .filter(seat -> zone == null || zone.isBlank() || seat.getZone().equalsIgnoreCase(zone.trim()))
                .map(seat -> SeatResponse.from(seat,
                        from == null ? null : seat.getStatus() == SeatStatus.ACTIVE && !booked.contains(seat.getId())))
                .toList();
    }

    @Transactional
    public SeatResponse create(SeatRequest request) {
        String label = request.label().trim();
        if (seats.existsByLabelIgnoreCase(label)) {
            throw new ConflictException("A seat labelled \"" + label + "\" already exists.");
        }
        LibrarySeat seat = new LibrarySeat(label, request.zone(), request.hasPower());
        if (request.status() != null) {
            seat.setStatus(request.status());
        }
        return SeatResponse.from(seats.saveAndFlush(seat), null);
    }

    @Transactional
    public SeatResponse update(Long id, SeatRequest request) {
        LibrarySeat seat = seats.findById(id).orElseThrow(() -> new ResourceNotFoundException("Seat not found."));
        String label = request.label().trim();
        if (seats.existsByLabelIgnoreCaseAndIdNot(label, id)) {
            throw new ConflictException("A seat labelled \"" + label + "\" already exists.");
        }
        if (request.status() == SeatStatus.OUT_OF_SERVICE && seat.getStatus() != SeatStatus.OUT_OF_SERVICE
                && bookings.existsBySeatIdAndStatusAndEndsAtAfter(id, SeatBookingStatus.BOOKED, clock.instant())) {
            throw new ConflictException("This seat has upcoming bookings. Cancel them before taking it out of service.");
        }
        seat.setLabel(label);
        seat.setZone(request.zone());
        seat.setHasPower(request.hasPower());
        if (request.status() != null) {
            seat.setStatus(request.status());
        }
        return SeatResponse.from(seats.saveAndFlush(seat), null);
    }

    // ------------------------------------------------------------------ bookings

    @Transactional
    public SeatBookingResponse book(long userId, long seatId, Instant startsAt, Instant endsAt) {
        // One student's bookings run one at a time (see the class comment): lock their row first.
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        LibrarySeat seat = seats.findById(seatId).orElseThrow(() -> new ResourceNotFoundException("Seat not found."));
        Instant now = clock.instant();
        validateWindow(startsAt, endsAt, now);
        if (seat.getStatus() != SeatStatus.ACTIVE) {
            throw new BusinessRuleException("This seat is out of service.");
        }
        if (bookings.countByUserIdAndStatusAndEndsAtAfter(userId, SeatBookingStatus.BOOKED, now)
                >= policy.maxUpcomingSeatBookings()) {
            throw new BusinessRuleException("You can have at most " + policy.maxUpcomingSeatBookings()
                    + " upcoming seat bookings.");
        }
        if (bookings.seatIsBooked(seatId, startsAt, endsAt)) {
            throw new ConflictException(SEAT_TAKEN);
        }
        if (bookings.studentHasBooking(userId, startsAt, endsAt)) {
            throw new ConflictException(STUDENT_BUSY);
        }
        try {
            return SeatBookingResponse.from(bookings.saveAndFlush(new SeatBooking(seat, user, startsAt, endsAt)));
        } catch (DataIntegrityViolationException e) {
            // Another request got in between our check and our insert; the database exclusion constraint stopped us.
            String cause = String.valueOf(NestedExceptionUtils.getMostSpecificCause(e).getMessage());
            if (cause.contains("ex_seat_bookings_no_user_overlap")) {
                throw new ConflictException(STUDENT_BUSY);
            }
            if (cause.contains("ex_seat_bookings_no_seat_overlap")) {
                throw new ConflictException(SEAT_TAKEN);
            }
            throw e;
        } catch (PessimisticLockingFailureException e) {
            // PostgreSQL chose this request as the loser of a lock cycle: nothing was saved, so it is safe to retry.
            throw new ConflictException(BUSY_TRY_AGAIN);
        }
    }

    @Transactional
    public SeatBookingResponse cancel(AuthenticatedUser caller, long bookingId) {
        SeatBooking booking = visibleTo(caller, bookingId);
        if (booking.getStatus() != SeatBookingStatus.BOOKED) {
            throw new ConflictException("This booking is already cancelled.");
        }
        if (!booking.getEndsAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("This booking has already ended.");
        }
        booking.cancel();
        return SeatBookingResponse.from(bookings.saveAndFlush(booking));
    }

    @Transactional(readOnly = true)
    public PageResponse<SeatBookingResponse> mine(long userId, boolean upcomingOnly, Pageable pageable) {
        var page = upcomingOnly
                ? bookings.findByUserIdAndStatusAndEndsAtAfter(userId, SeatBookingStatus.BOOKED, clock.instant(), pageable)
                : bookings.findByUserIdOrderByStartsAtDesc(userId, pageable);
        return PageResponse.of(page, SeatBookingResponse::from);
    }

    /** Staff view: every booking, or only the active ones overlapping a window. */
    @Transactional(readOnly = true)
    public PageResponse<SeatBookingResponse> list(Instant from, Instant to, Pageable pageable) {
        if ((from == null) != (to == null)) {
            throw new BusinessRuleException("Give both 'from' and 'to' to filter by time, or neither.");
        }
        var page = from == null ? bookings.findAll(pageable) : bookings.findActiveOverlapping(from, to, pageable);
        return PageResponse.of(page, SeatBookingResponse::from);
    }

    // ------------------------------------------------------------------ internals

    private static final String SEAT_TAKEN = "This seat is already booked for part of that time.";
    private static final String STUDENT_BUSY = "You already have a seat booked during that time.";
    private static final String BUSY_TRY_AGAIN = "Another booking was being made at the same moment. Please try again.";

    private void validateWindow(Instant startsAt, Instant endsAt, Instant now) {
        if (!endsAt.isAfter(startsAt)) {
            throw new BusinessRuleException("The booking must end after it starts.");
        }
        Duration length = Duration.between(startsAt, endsAt);
        if (length.compareTo(Duration.ofMinutes(policy.seatMinMinutes())) < 0) {
            throw new BusinessRuleException("A booking must be at least " + policy.seatMinMinutes() + " minutes.");
        }
        if (length.compareTo(MAX_BOOKING) > 0) {
            throw new BusinessRuleException("A booking can be at most " + MAX_BOOKING.toHours() + " hours.");
        }
        if (startsAt.isBefore(now.minus(START_GRACE))) {
            throw new BusinessRuleException("A booking cannot start in the past.");
        }
        if (startsAt.isAfter(now.plus(policy.seatMaxAdvanceDays(), ChronoUnit.DAYS))) {
            throw new BusinessRuleException("Seats can only be booked up to " + policy.seatMaxAdvanceDays() + " days ahead.");
        }
    }

    /** Students only ever see their own bookings; someone else's looks like it does not exist. */
    private SeatBooking visibleTo(AuthenticatedUser caller, long bookingId) {
        SeatBooking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found."));
        boolean staff = caller.role() == Role.LIBRARIAN || caller.role() == Role.ADMIN;
        if (!staff && !booking.getUser().getId().equals(caller.id())) {
            throw new ResourceNotFoundException("Booking not found.");
        }
        return booking;
    }
}
