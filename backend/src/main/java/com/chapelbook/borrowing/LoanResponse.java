package com.chapelbook.borrowing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A loan as shown to the student or staff. {@code fineAmount} is only filled in where a fine is known
 * (the return response and the single-loan view); list views leave it null.
 */
public record LoanResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String copyBarcode,
        Long userId,
        String userName,
        Instant borrowedAt,
        Instant dueAt,
        Instant returnedAt,
        int renewalCount,
        LoanStatus status,
        BigDecimal fineAmount) {

    public static LoanResponse from(BorrowRecord loan, Instant now, BigDecimal fineAmount) {
        LoanStatus status = loan.isReturned() ? LoanStatus.RETURNED
                : loan.getDueAt().isBefore(now) ? LoanStatus.OVERDUE : LoanStatus.ACTIVE;
        return new LoanResponse(loan.getId(), loan.getCopy().getBook().getId(), loan.getCopy().getBook().getTitle(),
                loan.getCopy().getBarcode(), loan.getUser().getId(), loan.getUser().getFullName(), loan.getBorrowedAt(),
                loan.getDueAt(), loan.getReturnedAt(), loan.getRenewalCount(), status, fineAmount);
    }
}
