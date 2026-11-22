package com.chapelbook.statistics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Seat usage over a range of UTC days, counted by the day a booking STARTS. Only BOOKED bookings count towards
 * usage; cancelled ones are reported separately.
 */
public record SeatStatsResponse(
        LocalDate from,
        LocalDate to,
        Totals totals,
        List<Day> daily,
        List<Zone> byZone,
        List<Hour> peakHours) {

    public record Totals(long bookings, long cancelled, BigDecimal bookedHours, long distinctStudents,
            long seatsInService) {
    }

    public record Day(LocalDate date, long bookings, BigDecimal bookedHours) {
    }

    /** {@code averageHoursPerSeatPerDay} is booked hours divided by seats in service and days in the range. */
    public record Zone(String zone, long seatsInService, long bookings, BigDecimal bookedHours,
            BigDecimal averageHoursPerSeatPerDay) {
    }

    /**
     * How many bookings were active during each hour of the day (0 to 23, UTC) summed over the range. A booking
     * from 10:30 to 11:30 counts in both the 10 o'clock and the 11 o'clock hour. Always 24 entries.
     */
    public record Hour(int hour, long bookings) {
    }
}
