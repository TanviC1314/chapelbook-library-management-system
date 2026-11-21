package com.chapelbook.borrowing;

/** Which loans a staff listing shows. */
public enum LoanScope {
    ALL,
    /** Loans that have not been returned. */
    ACTIVE,
    /** Loans that have not been returned and are past due. */
    OVERDUE
}
