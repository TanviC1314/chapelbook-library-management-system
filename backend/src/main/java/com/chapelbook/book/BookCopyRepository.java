package com.chapelbook.book;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    Optional<BookCopy> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    boolean existsByBookId(Long bookId);

    List<BookCopy> findByBookIdOrderByBarcode(Long bookId);

    long countByBookIdAndStatus(Long bookId, CopyStatus status);

    /** Copy counts per status for a set of books, in one query (used to show availability on a page of results). */
    @Query("""
            select c.book.id as bookId, c.status as status, count(c) as total
            from BookCopy c
            where c.book.id in :bookIds
            group by c.book.id, c.status
            """)
    List<CopyStatusCount> countByBookIds(@Param("bookIds") Collection<Long> bookIds);

    /**
     * Loads a copy and takes a row lock until the transaction ends. Used when lending or holding a
     * copy so two librarians cannot act on the same copy at the same moment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c where c.id = :id")
    Optional<BookCopy> findByIdForUpdate(@Param("id") Long id);

    /** The first available copy of a title, locked, so concurrent issues never pick the same one. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from BookCopy c
            where c.book.id = :bookId and c.status = com.chapelbook.book.CopyStatus.AVAILABLE
            order by c.id
            """)
    List<BookCopy> findAvailableForUpdate(@Param("bookId") Long bookId, Pageable limit);
}
