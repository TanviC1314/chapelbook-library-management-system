package com.chapelbook.seat;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatBookingRepository extends JpaRepository<SeatBooking, Long> {

    Page<SeatBooking> findByUserIdOrderByStartsAtDesc(Long userId, Pageable pageable);

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

    @Query("""
            select b from SeatBooking b
            where b.status = com.chapelbook.seat.SeatBookingStatus.BOOKED
              and b.startsAt < :to and b.endsAt > :from
            order by b.startsAt
            """)
    Page<SeatBooking> findActiveOverlapping(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    long countByStatusAndEndsAtAfter(SeatBookingStatus status, Instant moment);
}
