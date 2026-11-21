package com.chapelbook.fine;

import java.math.BigDecimal;
import java.time.Instant;

public record FineResponse(
        Long id,
        Long loanId,
        String bookTitle,
        Long userId,
        String userName,
        BigDecimal amount,
        FineStatus status,
        Instant assessedAt,
        Instant paidAt) {

    public static FineResponse from(Fine fine) {
        return new FineResponse(fine.getId(), fine.getBorrowRecord().getId(),
                fine.getBorrowRecord().getCopy().getBook().getTitle(), fine.getUser().getId(),
                fine.getUser().getFullName(), fine.getAmount(), fine.getStatus(), fine.getAssessedAt(), fine.getPaidAt());
    }
}
