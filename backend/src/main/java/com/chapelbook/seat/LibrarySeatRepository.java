package com.chapelbook.seat;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibrarySeatRepository extends JpaRepository<LibrarySeat, Long> {

    boolean existsByLabelIgnoreCase(String label);

    boolean existsByLabelIgnoreCaseAndIdNot(String label, Long id);

    List<LibrarySeat> findByStatusOrderByZoneAscLabelAsc(SeatStatus status);

    default List<LibrarySeat> findAllOrdered() {
        return findAll(Sort.by(Sort.Order.asc("zone"), Sort.Order.asc("label")));
    }
}
