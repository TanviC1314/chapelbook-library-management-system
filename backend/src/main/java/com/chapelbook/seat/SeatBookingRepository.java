package com.chapelbook.seat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatBookingRepository extends JpaRepository<SeatBooking, Long> {

    @Override
    @EntityGraph(attributePaths = {"seat", "user"})
    Optional<SeatBooking> findById(Long id);

    @EntityGraph(attributePaths = {"seat", "user"})
    Page<SeatBooking> findByUserIdOrderByStartsAtDesc(Long userId, Pageable pageable);

    /** The student's bookings that are still BOOKED and have not ended yet. */
    @EntityGraph(attributePaths = {"seat", "user"})
    Page<SeatBooking> findByUserIdAndStatusAndEndsAtAfter(Long userId, SeatBookingStatus status, Instant after,
            Pageable pageable);

    long countByUserIdAndStatusAndEndsAtAfter(Long userId, SeatBookingStatus status, Instant after);

    /** Does the seat have an active booking that has not ended? Used before taking a seat out of service. */
    boolean existsBySeatIdAndStatusAndEndsAtAfter(Long seatId, SeatBookingStatus status, Instant after);

    /**
     * Seats that have a BOOKED booking overlapping [from, to). Half-open ranges: a booking that ends at
     * {@code from} does not overlap. This is for showing availability; the exclusion constraint in the
     * database is what actually prevents double booking.
     */
    @Query("""
            select b.seat.id from SeatBooking b
            where b.status = com.chapelbook.seat.SeatBookingStatus.BOOKED
              and b.startsAt < :to and b.endsAt > :from
            """)
    List<Long> findBookedSeatIds(@Param("from") Instant from, @Param("to") Instant to);

    @EntityGraph(attributePaths = {"seat", "user"})
    @Query("""
            select b from SeatBooking b
            where b.status = com.chapelbook.seat.SeatBookingStatus.BOOKED
              and b.startsAt < :to and b.endsAt > :from
            order by b.startsAt
            """)
    Page<SeatBooking> findActiveOverlapping(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"seat", "user"})
    Page<SeatBooking> findAll(Pageable pageable);

    /** A friendly early check. The database exclusion constraint remains the real guarantee under concurrency. */
    @Query("""
            select count(b) > 0 from SeatBooking b
            where b.seat.id = :seatId and b.status = com.chapelbook.seat.SeatBookingStatus.BOOKED
              and b.startsAt < :to and b.endsAt > :from
            """)
    boolean seatIsBooked(@Param("seatId") Long seatId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(b) > 0 from SeatBooking b
            where b.user.id = :userId and b.status = com.chapelbook.seat.SeatBookingStatus.BOOKED
              and b.startsAt < :to and b.endsAt > :from
            """)
    boolean studentHasBooking(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

    long countByStatusAndEndsAtAfter(SeatBookingStatus status, Instant moment);
}
