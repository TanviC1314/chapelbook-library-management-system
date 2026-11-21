package com.chapelbook.book;

import jakarta.validation.constraints.Size;

/**
 * Either field may be omitted. {@code status} may only move a copy between AVAILABLE, LOST and WITHDRAWN;
 * ON_LOAN and HELD are set by lending and reserving, never by hand.
 */
public record CopyUpdateRequest(
        @Size(max = 100) String shelfLocation,
        CopyStatus status) {
}
