package com.chapelbook.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Library policy from {@code app.library.*}. Any value that is missing (or nonsensical) falls back to a
 * sensible default, so the application always starts with a complete set of rules.
 *
 * @param reservationHoldDays     days a held copy waits to be collected
 * @param maxActiveReservations   reservations one student may have at once
 * @param loanDays                loan period
 * @param maxLoansPerStudent      books one student may have out at once
 * @param maxRenewals             times one loan may be renewed
 * @param renewalDays             days added by each renewal
 * @param finePerDay              fine for each started day a book is overdue
 * @param maxFinePerLoan          the most one loan can ever be fined
 * @param borrowingBlockedAbove   a student owing more than this in unpaid fines cannot borrow
 * @param seatMaxAdvanceDays      how far ahead a seat can be booked
 * @param seatMinMinutes          shortest seat booking
 * @param maxUpcomingSeatBookings seat bookings one student may hold that have not ended yet
 */
@ConfigurationProperties("app.library")
public record LibraryProperties(
        Integer reservationHoldDays,
        Integer maxActiveReservations,
        Integer loanDays,
        Integer maxLoansPerStudent,
        Integer maxRenewals,
        Integer renewalDays,
        BigDecimal finePerDay,
        BigDecimal maxFinePerLoan,
        BigDecimal borrowingBlockedAbove,
        Integer seatMaxAdvanceDays,
        Integer seatMinMinutes,
        Integer maxUpcomingSeatBookings) {

    public LibraryProperties {
        reservationHoldDays = positive(reservationHoldDays, 3);
        maxActiveReservations = positive(maxActiveReservations, 5);
        loanDays = positive(loanDays, 14);
        maxLoansPerStudent = positive(maxLoansPerStudent, 5);
        maxRenewals = maxRenewals == null || maxRenewals < 0 ? 2 : maxRenewals;
        renewalDays = positive(renewalDays, 14);
        finePerDay = money(finePerDay, "0.25");
        maxFinePerLoan = money(maxFinePerLoan, "10.00");
        borrowingBlockedAbove = money(borrowingBlockedAbove, "5.00");
        seatMaxAdvanceDays = positive(seatMaxAdvanceDays, 14);
        seatMinMinutes = positive(seatMinMinutes, 15);
        maxUpcomingSeatBookings = positive(maxUpcomingSeatBookings, 3);
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private static BigDecimal money(BigDecimal value, String fallback) {
        BigDecimal result = value == null || value.signum() < 0 ? new BigDecimal(fallback) : value;
        return result.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
