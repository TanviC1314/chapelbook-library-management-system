package com.chapelbook.fine;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FineRepository extends JpaRepository<Fine, Long> {

    Page<Fine> findByUserId(Long userId, Pageable pageable);

    Optional<Fine> findByBorrowRecordId(Long borrowRecordId);

    boolean existsByBorrowRecordId(Long borrowRecordId);

    /** What a student currently owes. Returns 0 rather than null when there are no unpaid fines. */
    @Query("select coalesce(sum(f.amount), 0) from Fine f where f.user.id = :userId and f.status = :status")
    BigDecimal sumAmountByUserAndStatus(@Param("userId") Long userId, @Param("status") FineStatus status);
}
