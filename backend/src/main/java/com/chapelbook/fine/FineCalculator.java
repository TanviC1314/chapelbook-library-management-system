package com.chapelbook.fine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * The overdue fine rule, kept as a pure function so it is trivial to test: every started day late costs
 * one day's rate, up to a cap per loan. Money is BigDecimal with two decimal places, never a double.
 */
public final class FineCalculator {

    private FineCalculator() {
    }

    /** Whole days late, counting a started day as a full day. Zero when returned on or before the due time. */
    public static long daysOverdue(Instant dueAt, Instant returnedAt) {
        if (!returnedAt.isAfter(dueAt)) {
            return 0;
        }
        Duration late = Duration.between(dueAt, returnedAt);
        long days = late.toDays();
        return late.equals(Duration.ofDays(days)) ? days : days + 1;
    }

    public static BigDecimal fineFor(Instant dueAt, Instant returnedAt, BigDecimal perDay, BigDecimal cap) {
        BigDecimal amount = perDay.multiply(BigDecimal.valueOf(daysOverdue(dueAt, returnedAt)))
                .setScale(2, RoundingMode.HALF_UP);
        return amount.min(cap.setScale(2, RoundingMode.HALF_UP));
    }
}
