package com.chapelbook.statistics;

import java.math.BigDecimal;
import java.time.Instant;

/** The live numbers for the librarian and admin dashboards. Every figure is counted by the database on request. */
public record OverviewResponse(
        Instant generatedAt,
        Users users,
        Catalogue catalogue,
        Circulation circulation,
        Reservations reservations,
        Fines fines,
        Seats seats) {

    /** Accounts by role (active ones) and how many accounts are deactivated. */
    public record Users(long students, long librarians, long admins, long deactivated) {
    }

    /** Titles and copies. "inCirculation" is available + on loan + held (not lost or withdrawn). */
    public record Catalogue(long titles, long categories, long copiesInCirculation, long copiesAvailable,
            long copiesOnLoan, long copiesHeld, long copiesLost, long copiesWithdrawn) {
    }

    public record Circulation(long activeLoans, long overdueLoans, long issuedToday, long returnedToday) {
    }

    public record Reservations(long pending, long ready) {
    }

    public record Fines(long unpaidCount, BigDecimal unpaidTotal) {
    }

    public record Seats(long inService, long outOfService, long bookedNow, long upcomingBookings,
            long bookingsStartingToday) {
    }
}
