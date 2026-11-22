package com.chapelbook.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {

    private static final int TOP_BOOKS = 10;

    private final StatisticsRepository repository;
    private final Clock clock;

    public StatisticsService(StatisticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Loans, returns, fines, the most borrowed titles and loans per category for a range of days. */
    @Transactional(readOnly = true)
    public CirculationStatsResponse circulation(LocalDate from, LocalDate to) {
        DateRange range = DateRange.resolve(from, to, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
        Instant start = range.start();
        Instant end = range.endExclusive();
        Map<LocalDate, Long> issued = repository.loansIssuedPerDay(start, end);
        Map<LocalDate, Long> returned = repository.loansReturnedPerDay(start, end);
        Map<LocalDate, Object[]> fines = repository.finesPerDay(start, end);

        List<CirculationStatsResponse.Day> daily = new ArrayList<>();
        long totalIssued = 0;
        long totalReturned = 0;
        long totalFines = 0;
        BigDecimal totalFineAmount = BigDecimal.ZERO.setScale(2);
        for (LocalDate day : range.days()) {
            Object[] fine = fines.get(day);
            long fineCount = fine == null ? 0 : (Long) fine[0];
            BigDecimal fineAmount = fine == null ? BigDecimal.ZERO.setScale(2) : (BigDecimal) fine[1];
            long dayIssued = issued.getOrDefault(day, 0L);
            long dayReturned = returned.getOrDefault(day, 0L);
            daily.add(new CirculationStatsResponse.Day(day, dayIssued, dayReturned, fineCount, fineAmount));
            totalIssued += dayIssued;
            totalReturned += dayReturned;
            totalFines += fineCount;
            totalFineAmount = totalFineAmount.add(fineAmount);
        }
        return new CirculationStatsResponse(range.from(), range.to(),
                new CirculationStatsResponse.Totals(totalIssued, totalReturned, repository.returnedLate(start, end),
                        repository.distinctBorrowers(start, end), totalFines, totalFineAmount),
                daily, repository.topBooks(start, end, TOP_BOOKS), repository.loansByCategory(start, end));
    }

    /** Seat bookings and booked hours per day and per zone, and which hours of the day are busiest. */
    @Transactional(readOnly = true)
    public SeatStatsResponse seats(LocalDate from, LocalDate to) {
        DateRange range = DateRange.resolve(from, to, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
        Instant start = range.start();
        Instant end = range.endExclusive();
        Map<LocalDate, Object[]> perDay = repository.seatBookingsPerDay(start, end);

        List<SeatStatsResponse.Day> daily = new ArrayList<>();
        long totalBookings = 0;
        BigDecimal totalHours = BigDecimal.ZERO.setScale(2);
        for (LocalDate day : range.days()) {
            Object[] row = perDay.get(day);
            long bookings = row == null ? 0 : (Long) row[0];
            BigDecimal hours = row == null ? BigDecimal.ZERO.setScale(2) : hours((BigDecimal) row[1]);
            daily.add(new SeatStatsResponse.Day(day, bookings, hours));
            totalBookings += bookings;
            totalHours = totalHours.add(hours);
        }

        long days = range.days().size();
        List<SeatStatsResponse.Zone> zones = new ArrayList<>();
        for (Object[] row : repository.seatUsageByZone(start, end)) {
            long seatsInService = (Long) row[1];
            BigDecimal hours = hours((BigDecimal) row[3]);
            BigDecimal average = seatsInService == 0 ? BigDecimal.ZERO.setScale(2)
                    : hours.divide(BigDecimal.valueOf(seatsInService * days), 2, RoundingMode.HALF_UP);
            zones.add(new SeatStatsResponse.Zone((String) row[0], seatsInService, (Long) row[2], hours, average));
        }

        Map<Integer, Long> activePerHour = repository.bookingsActivePerHourOfDay(start, end);
        List<SeatStatsResponse.Hour> peakHours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            peakHours.add(new SeatStatsResponse.Hour(hour, activePerHour.getOrDefault(hour, 0L)));
        }
        return new SeatStatsResponse(range.from(), range.to(),
                new SeatStatsResponse.Totals(totalBookings, repository.cancelledSeatBookings(start, end), totalHours,
                        repository.distinctSeatStudents(start, end), repository.seatsInService()),
                daily, zones, peakHours);
    }

    private static BigDecimal hours(BigDecimal raw) {
        return raw.setScale(2, RoundingMode.HALF_UP);
    }

    /** Live dashboard numbers. Read-only, and every figure comes straight from the database. */
    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new OverviewResponse(now, repository.users(), repository.catalogue(),
                repository.circulation(now, startOfToday, startOfTomorrow), repository.reservations(),
                repository.fines(), repository.seats(now, startOfToday, startOfTomorrow));
    }
}
