package com.chapelbook.borrowing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A librarian lends the copy with this barcode to this student. */
public record IssueRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 64) String copyBarcode) {
}
