package com.chapelbook.fine;

public enum FineStatus {
    UNPAID,
    PAID,
    /** A librarian or admin cancelled the fine. */
    WAIVED
}
