package com.chapelbook.statistics;

import com.chapelbook.common.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** An inclusive range of UTC calendar days for a report. Defaults to the last 30 days; at most a year. */
public record DateRange(LocalDate from, LocalDate to) {

    static final int MAX_DAYS = 366;
    static final int DEFAULT_DAYS = 30;

    public static DateRange resolve(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessRuleException("'from' must not be after 'to'.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BusinessRuleException("A report can cover at most " + MAX_DAYS + " days.");
        }
        return new DateRange(start, end);
    }

    /** The first instant of the first day. */
    public Instant start() {
        return from.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** The first instant after the last day (exclusive end), so a whole last day is included. */
    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public List<LocalDate> days() {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }
}
