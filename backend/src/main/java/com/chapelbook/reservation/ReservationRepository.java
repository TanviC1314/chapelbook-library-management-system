package com.chapelbook.reservation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Loads the title, student and held copy in the same query so a list of reservations is not one query per row. */
    @Override
    @EntityGraph(attributePaths = {"book", "user", "copy"})
    Page<Reservation> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"book", "user", "copy"})
    Optional<Reservation> findById(Long id);

    boolean existsByBookId(Long bookId);

    boolean existsByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, Collection<ReservationStatus> statuses);

    /** The student's live reservation for a title, if they have one. */
    Optional<Reservation> findFirstByUserIdAndBookIdAndStatusIn(Long userId, Long bookId,
            Collection<ReservationStatus> statuses);

    /** Who a held copy is being kept for. */
    @EntityGraph(attributePaths = {"user"})
    Optional<Reservation> findByCopyIdAndStatus(Long copyId, ReservationStatus status);

    long countByUserIdAndStatusIn(Long userId, Collection<ReservationStatus> statuses);

    @EntityGraph(attributePaths = {"book", "user", "copy"})
    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"book", "user", "copy"})
    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);

    /** The next student in the queue for a title: the oldest pending reservation. */
    Optional<Reservation> findFirstByBookIdAndStatusOrderByReservedAtAscIdAsc(Long bookId, ReservationStatus status);

    /** Reservations whose collection deadline has passed, so the copy can go to the next student. */
    List<Reservation> findByStatusAndReadyUntilBefore(ReservationStatus status, Instant cutoff);

    long countByBookIdAndStatus(Long bookId, ReservationStatus status);

    /** How many pending reservations are ahead of this one in the queue. */
    @Query("""
            select count(r) from Reservation r
            where r.book.id = :bookId and r.status = com.chapelbook.reservation.ReservationStatus.PENDING
              and (r.reservedAt < :reservedAt or (r.reservedAt = :reservedAt and r.id < :id))
            """)
    long countPendingAhead(@Param("bookId") Long bookId, @Param("reservedAt") Instant reservedAt, @Param("id") Long id);

    /** The title a reservation belongs to, read without loading the entity (so the title can be locked first). */
    @Query("select r.book.id from Reservation r where r.id = :id")
    Optional<Long> findBookId(@Param("id") Long id);

    /** Queue length per title for a set of books, in one query. */
    @Query("""
            select r.book.id as bookId, count(r) as total from Reservation r
            where r.book.id in :bookIds and r.status = com.chapelbook.reservation.ReservationStatus.PENDING
            group by r.book.id
            """)
    List<PendingCount> countPendingByBookIds(@Param("bookIds") Collection<Long> bookIds);

    interface PendingCount {

        Long getBookId();

        long getTotal();
    }
}
