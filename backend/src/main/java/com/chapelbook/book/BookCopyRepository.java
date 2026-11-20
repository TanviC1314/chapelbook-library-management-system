package com.chapelbook.book;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    Optional<BookCopy> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    List<BookCopy> findByBookIdOrderByBarcode(Long bookId);

    long countByBookIdAndStatus(Long bookId, CopyStatus status);

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
    List<BookCopy> findAvailableForUpdate(@Param("bookId") Long bookId,
            org.springframework.data.domain.Pageable limit);
}
