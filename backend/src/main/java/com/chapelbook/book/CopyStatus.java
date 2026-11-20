package com.chapelbook.book;

/** Where a physical copy is in its life. Mirrors the CHECK constraint on book_copies.status. */
public enum CopyStatus {
    /** On the shelf and can be lent. */
    AVAILABLE,
    /** Currently borrowed. */
    ON_LOAN,
    /** Set aside behind the desk for a student who reserved the title. */
    HELD,
    LOST,
    /** Removed from circulation (damaged, retired). Kept so history is not lost. */
    WITHDRAWN
}
