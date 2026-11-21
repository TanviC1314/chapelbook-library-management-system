package com.chapelbook.borrowing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A librarian scans the barcode of a copy being handed back. */
public record ReturnRequest(@NotBlank @Size(max = 64) String copyBarcode) {
}
