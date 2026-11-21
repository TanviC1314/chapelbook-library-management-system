package com.chapelbook.borrowing;

public enum LoanStatus {
    /** On loan and not yet due. */
    ACTIVE,
    /** On loan and past its due date. */
    OVERDUE,
    RETURNED
}
