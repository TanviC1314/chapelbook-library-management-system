package com.chapelbook.reservation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, Collection<ReservationStatus> statuses);

    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);

    /** The next student in the queue for a title: the oldest pending reservation. */
    Optional<Reservation> findFirstByBookIdAndStatusOrderByReservedAtAsc(Long bookId, ReservationStatus status);

    /** Reservations whose collection deadline has passed, so the copy can go to the next student. */
    List<Reservation> findByStatusAndReadyUntilBefore(ReservationStatus status, Instant cutoff);

    long countByBookIdAndStatus(Long bookId, ReservationStatus status);
}
