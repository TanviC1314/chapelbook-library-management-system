package com.chapelbook.statistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
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
}
