package com.chapelbook.borrowing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {

    /** Loads the copy, its title and the student in the same query, so a page of loans is not one query per row. */
    @Override
    @EntityGraph(attributePaths = {"copy", "copy.book", "user"})
    Page<BorrowRecord> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"copy", "copy.book", "user"})
    Optional<BorrowRecord> findById(Long id);

    @EntityGraph(attributePaths = {"copy", "copy.book", "user"})
    Page<BorrowRecord> findByUserId(Long userId, Pageable pageable);

    /** Loans the student still has out. */
    @EntityGraph(attributePaths = {"copy", "copy.book", "user"})
    Page<BorrowRecord> findByUserIdAndReturnedAtIsNull(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"copy", "copy.book", "user"})
    Page<BorrowRecord> findByReturnedAtIsNull(Pageable pageable);

    long countByUserIdAndReturnedAtIsNull(Long userId);

    /** The open loan of a copy, if any (at most one, enforced by a partial unique index). */
    @EntityGraph(attributePaths = {"copy", "copy.book", "user"})
    Optional<BorrowRecord> findByCopyIdAndReturnedAtIsNull(Long copyId);

    /** Open loans past their due date, oldest first: the librarian's overdue list. */
    @EntityGraph(attributePaths = {"copy", "copy.book", "user"})
    @Query("""
            select b from BorrowRecord b
            where b.returnedAt is null and b.dueAt < :now
            order by b.dueAt
            """)
    Page<BorrowRecord> findOverdue(@Param("now") Instant now, Pageable pageable);

    long countByReturnedAtIsNull();

    /** Does the student currently have any copy of this title out on loan? */
    boolean existsByUserIdAndCopyBookIdAndReturnedAtIsNull(Long userId, Long bookId);

    /** Does the student have any loan past its due date that has not been returned? */
    boolean existsByUserIdAndReturnedAtIsNullAndDueAtBefore(Long userId, Instant now);

    List<BorrowRecord> findByUserIdAndReturnedAtIsNull(Long userId);
}
