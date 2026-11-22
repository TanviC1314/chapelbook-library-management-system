package com.chapelbook.statistics;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reporting for the staff dashboards. Read-only. All dates and times are UTC. */
@RestController
@Tag(name = "Statistics", description = "Live overview and period reports.")
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

    /**
     * Admin report: loans issued and returned per day, fines raised, most borrowed titles and loans per category.
     * {@code from} and {@code to} are inclusive UTC dates (yyyy-MM-dd); the default is the last 30 days, the
     * maximum a year.
     */
    @GetMapping("/circulation")
    @PreAuthorize("hasRole('ADMIN')")
    public CirculationStatsResponse circulation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statistics.circulation(from, to);
    }

    /**
     * Admin report: seat bookings and booked hours per day and per zone, plus the busiest hours of the day.
     * Same {@code from} / {@code to} rules as the circulation report.
     */
    @GetMapping("/seats")
    @PreAuthorize("hasRole('ADMIN')")
    public SeatStatsResponse seats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statistics.seats(from, to);
    }
}
