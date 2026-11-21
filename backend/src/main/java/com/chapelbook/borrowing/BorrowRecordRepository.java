package com.chapelbook.borrowing;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {

    Page<BorrowRecord> findByUserId(Long userId, Pageable pageable);

    /** Loans the student still has out. */
    Page<BorrowRecord> findByUserIdAndReturnedAtIsNull(Long userId, Pageable pageable);

    long countByUserIdAndReturnedAtIsNull(Long userId);

    /** The open loan of a copy, if any (at most one, enforced by a partial unique index). */
    Optional<BorrowRecord> findByCopyIdAndReturnedAtIsNull(Long copyId);

    /** Open loans past their due date, oldest first: the librarian's overdue list. */
    @Query("""
            select b from BorrowRecord b
            where b.returnedAt is null and b.dueAt < :now
            order by b.dueAt
            """)
    Page<BorrowRecord> findOverdue(@Param("now") Instant now, Pageable pageable);

    long countByReturnedAtIsNull();

    /** Does the student currently have any copy of this title out on loan? */
    boolean existsByUserIdAndCopyBookIdAndReturnedAtIsNull(Long userId, Long bookId);
}
