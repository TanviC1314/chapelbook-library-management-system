package com.chapelbook.statistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only reporting queries, written as plain SQL because they are aggregations (counts, sums, group by) that
 * are clearer and faster in SQL than as entity graphs. Nothing here changes data. Dates are UTC.
 */
@Repository
public class StatisticsRepository {

    private final JdbcTemplate jdbc;

    public StatisticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** pgjdbc does not accept java.time.Instant as a parameter, so timestamps are passed as UTC OffsetDateTime. */
    static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    long count(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }

    /** "SELECT key, count" style queries as a map. */
    Map<String, Long> countsByKey(String sql, Object... args) {
        Map<String, Long> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        }, args);
        return result;
    }

    BigDecimal sum(String sql, Object... args) {
        BigDecimal result = jdbc.queryForObject(sql, BigDecimal.class, args);
        return result == null ? BigDecimal.ZERO.setScale(2) : result;
    }

    JdbcTemplate jdbc() {
        return jdbc;
    }

    // ---------------------------------------------------------------- overview

    OverviewResponse.Users users() {
        Map<String, Long> byRole = countsByKey("SELECT role, count(*) FROM users WHERE active GROUP BY role");
        return new OverviewResponse.Users(byRole.getOrDefault("STUDENT", 0L), byRole.getOrDefault("LIBRARIAN", 0L),
                byRole.getOrDefault("ADMIN", 0L), count("SELECT count(*) FROM users WHERE NOT active"));
    }

    OverviewResponse.Catalogue catalogue() {
        Map<String, Long> byStatus = countsByKey("SELECT status, count(*) FROM book_copies GROUP BY status");
        long available = byStatus.getOrDefault("AVAILABLE", 0L);
        long onLoan = byStatus.getOrDefault("ON_LOAN", 0L);
        long held = byStatus.getOrDefault("HELD", 0L);
        return new OverviewResponse.Catalogue(count("SELECT count(*) FROM books"),
                count("SELECT count(*) FROM categories"), available + onLoan + held, available, onLoan, held,
                byStatus.getOrDefault("LOST", 0L), byStatus.getOrDefault("WITHDRAWN", 0L));
    }

    OverviewResponse.Circulation circulation(Instant now, Instant startOfToday, Instant startOfTomorrow) {
        return new OverviewResponse.Circulation(
                count("SELECT count(*) FROM borrow_records WHERE returned_at IS NULL"),
                count("SELECT count(*) FROM borrow_records WHERE returned_at IS NULL AND due_at < ?", utc(now)),
                count("SELECT count(*) FROM borrow_records WHERE borrowed_at >= ? AND borrowed_at < ?",
                        utc(startOfToday), utc(startOfTomorrow)),
                count("SELECT count(*) FROM borrow_records WHERE returned_at >= ? AND returned_at < ?",
                        utc(startOfToday), utc(startOfTomorrow)));
    }

    OverviewResponse.Reservations reservations() {
        Map<String, Long> byStatus = countsByKey(
                "SELECT status, count(*) FROM reservations WHERE status IN ('PENDING', 'READY') GROUP BY status");
        return new OverviewResponse.Reservations(byStatus.getOrDefault("PENDING", 0L), byStatus.getOrDefault("READY", 0L));
    }

    OverviewResponse.Fines fines() {
        return new OverviewResponse.Fines(count("SELECT count(*) FROM fines WHERE status = 'UNPAID'"),
                sum("SELECT coalesce(sum(amount), 0) FROM fines WHERE status = 'UNPAID'"));
    }

    OverviewResponse.Seats seats(Instant now, Instant startOfToday, Instant startOfTomorrow) {
        Map<String, Long> byStatus = countsByKey("SELECT status, count(*) FROM library_seats GROUP BY status");
        return new OverviewResponse.Seats(byStatus.getOrDefault("ACTIVE", 0L), byStatus.getOrDefault("OUT_OF_SERVICE", 0L),
                count("SELECT count(*) FROM seat_bookings WHERE status = 'BOOKED' AND starts_at <= ? AND ends_at > ?",
                        utc(now), utc(now)),
                count("SELECT count(*) FROM seat_bookings WHERE status = 'BOOKED' AND ends_at > ?", utc(now)),
                count("SELECT count(*) FROM seat_bookings WHERE status = 'BOOKED' AND starts_at >= ? AND starts_at < ?",
                        utc(startOfToday), utc(startOfTomorrow)));
    }

    // ---------------------------------------------------------------- circulation report

    /** (UTC day, count) pairs for loans issued in [from, to). Only days that had loans appear. */
    Map<LocalDate, Long> loansIssuedPerDay(Instant from, Instant to) {
        return perDay("SELECT (borrowed_at AT TIME ZONE 'UTC')::date, count(*) FROM borrow_records "
                + "WHERE borrowed_at >= ? AND borrowed_at < ? GROUP BY 1", from, to);
    }

    Map<LocalDate, Long> loansReturnedPerDay(Instant from, Instant to) {
        return perDay("SELECT (returned_at AT TIME ZONE 'UTC')::date, count(*) FROM borrow_records "
                + "WHERE returned_at >= ? AND returned_at < ? GROUP BY 1", from, to);
    }

    /** Fines raised per day: count and total amount. */
    Map<LocalDate, Object[]> finesPerDay(Instant from, Instant to) {
        Map<LocalDate, Object[]> result = new HashMap<>();
        jdbc.query("SELECT (assessed_at AT TIME ZONE 'UTC')::date, count(*), coalesce(sum(amount), 0) FROM fines "
                + "WHERE assessed_at >= ? AND assessed_at < ? GROUP BY 1", rs -> {
                    result.put(rs.getObject(1, LocalDate.class), new Object[] {rs.getLong(2), rs.getBigDecimal(3)});
                }, utc(from), utc(to));
        return result;
    }

    long returnedLate(Instant from, Instant to) {
        return count("SELECT count(*) FROM borrow_records WHERE returned_at >= ? AND returned_at < ? "
                + "AND returned_at > due_at", utc(from), utc(to));
    }

    long distinctBorrowers(Instant from, Instant to) {
        return count("SELECT count(DISTINCT user_id) FROM borrow_records WHERE borrowed_at >= ? AND borrowed_at < ?",
                utc(from), utc(to));
    }

    List<CirculationStatsResponse.TopBook> topBooks(Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT bk.id, bk.title, bk.author, count(*) AS loans
                FROM borrow_records b
                JOIN book_copies c ON c.id = b.copy_id
                JOIN books bk ON bk.id = c.book_id
                WHERE b.borrowed_at >= ? AND b.borrowed_at < ?
                GROUP BY bk.id, bk.title, bk.author
                ORDER BY loans DESC, bk.title, bk.id
                LIMIT ?
                """, (rs, i) -> new CirculationStatsResponse.TopBook(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getLong(4)), utc(from), utc(to), limit);
    }

    List<CirculationStatsResponse.CategoryLoans> loansByCategory(Instant from, Instant to) {
        return jdbc.query("""
                SELECT cat.id, cat.name, count(*) AS loans
                FROM borrow_records b
                JOIN book_copies c ON c.id = b.copy_id
                JOIN books bk ON bk.id = c.book_id
                JOIN categories cat ON cat.id = bk.category_id
                WHERE b.borrowed_at >= ? AND b.borrowed_at < ?
                GROUP BY cat.id, cat.name
                ORDER BY loans DESC, cat.name, cat.id
                """, (rs, i) -> new CirculationStatsResponse.CategoryLoans(rs.getLong(1), rs.getString(2), rs.getLong(3)),
                utc(from), utc(to));
    }

    private Map<LocalDate, Long> perDay(String sql, Instant from, Instant to) {
        Map<LocalDate, Long> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getObject(1, LocalDate.class), rs.getLong(2));
        }, utc(from), utc(to));
        return result;
    }

    // ---------------------------------------------------------------- seat report

    /** Per UTC day (of the start time): booking count and booked hours. Only BOOKED bookings. */
    Map<LocalDate, Object[]> seatBookingsPerDay(Instant from, Instant to) {
        Map<LocalDate, Object[]> result = new HashMap<>();
        jdbc.query("""
                SELECT (starts_at AT TIME ZONE 'UTC')::date, count(*),
                       coalesce(sum(extract(epoch FROM (ends_at - starts_at))) / 3600.0, 0)
                FROM seat_bookings
                WHERE status = 'BOOKED' AND starts_at >= ? AND starts_at < ?
                GROUP BY 1
                """, rs -> {
                    result.put(rs.getObject(1, LocalDate.class), new Object[] {rs.getLong(2), rs.getBigDecimal(3)});
                }, utc(from), utc(to));
        return result;
    }

    long cancelledSeatBookings(Instant from, Instant to) {
        return count("SELECT count(*) FROM seat_bookings WHERE status = 'CANCELLED' AND starts_at >= ? AND starts_at < ?",
                utc(from), utc(to));
    }

    long distinctSeatStudents(Instant from, Instant to) {
        return count("SELECT count(DISTINCT user_id) FROM seat_bookings "
                + "WHERE status = 'BOOKED' AND starts_at >= ? AND starts_at < ?", utc(from), utc(to));
    }

    long seatsInService() {
        return count("SELECT count(*) FROM library_seats WHERE status = 'ACTIVE'");
    }

    /** Every zone (including ones with no bookings): seats in service, bookings and booked hours. */
    List<Object[]> seatUsageByZone(Instant from, Instant to) {
        return jdbc.query("""
                SELECT s.zone,
                       count(DISTINCT s.id) FILTER (WHERE s.status = 'ACTIVE') AS seats,
                       count(b.id) AS bookings,
                       coalesce(sum(extract(epoch FROM (b.ends_at - b.starts_at))) / 3600.0, 0) AS hours
                FROM library_seats s
                LEFT JOIN seat_bookings b ON b.seat_id = s.id AND b.status = 'BOOKED'
                     AND b.starts_at >= ? AND b.starts_at < ?
                GROUP BY s.zone
                ORDER BY hours DESC, s.zone
                """, (rs, i) -> new Object[] {rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getBigDecimal(4)},
                utc(from), utc(to));
    }

    /** For each hour of the day (UTC), how many bookings were active during it, summed over the range. */
    Map<Integer, Long> bookingsActivePerHourOfDay(Instant from, Instant to) {
        Map<Integer, Long> result = new HashMap<>();
        jdbc.query("""
                SELECT extract(hour FROM h)::int AS hr, count(*)
                FROM seat_bookings b
                CROSS JOIN LATERAL generate_series(
                        date_trunc('hour', b.starts_at AT TIME ZONE 'UTC'),
                        (b.ends_at AT TIME ZONE 'UTC') - interval '1 microsecond',
                        interval '1 hour') AS h
                WHERE b.status = 'BOOKED' AND b.starts_at >= ? AND b.starts_at < ?
                GROUP BY 1
                """, rs -> {
                    result.put(rs.getInt(1), rs.getLong(2));
                }, utc(from), utc(to));
        return result;
    }
}
