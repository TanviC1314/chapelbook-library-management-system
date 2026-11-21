package com.chapelbook.fine;

import java.math.BigDecimal;

/** What a student owes right now, and the limit above which they cannot borrow. */
public record FineBalanceResponse(BigDecimal unpaid, BigDecimal borrowingBlockedAbove, boolean blocked) {
}
