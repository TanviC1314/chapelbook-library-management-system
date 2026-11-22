package com.chapelbook.statistics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reporting for the staff dashboards. Read-only. All dates and times are UTC. */
@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {

    private final StatisticsService statistics;

    public StatisticsController(StatisticsService statistics) {
        this.statistics = statistics;
    }

    /** Live counts for the librarian and admin dashboards: accounts, copies, loans, reservations, fines, seats. */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public OverviewResponse overview() {
        return statistics.overview();
    }
}
