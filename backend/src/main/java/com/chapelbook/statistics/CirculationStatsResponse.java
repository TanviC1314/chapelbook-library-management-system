package com.chapelbook.statistics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Circulation over a range of UTC days. {@code daily} has one entry for every day in the range, including quiet
 * days with zeros, so a chart needs no gap-filling.
 */
public record CirculationStatsResponse(
        LocalDate from,
        LocalDate to,
        Totals totals,
        List<Day> daily,
        List<TopBook> topBooks,
        List<CategoryLoans> byCategory) {

    /** {@code returnedLate} counts returns in the range that were after their due date. */
    public record Totals(long loansIssued, long loansReturned, long returnedLate, long distinctBorrowers,
            long finesRaised, BigDecimal finesAmount) {
    }

    public record Day(LocalDate date, long loansIssued, long loansReturned, long finesRaised, BigDecimal finesAmount) {
    }

    public record TopBook(Long bookId, String title, String author, long loans) {
    }

    public record CategoryLoans(Long categoryId, String category, long loans) {
    }
}
