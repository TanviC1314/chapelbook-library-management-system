package com.chapelbook.seat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibrarySeatRepository extends JpaRepository<LibrarySeat, Long> {

    boolean existsByLabelIgnoreCase(String label);

    List<LibrarySeat> findByStatusOrderByZoneAscLabelAsc(SeatStatus status);
}
