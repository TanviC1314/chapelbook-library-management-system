package com.chapelbook.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CopyRequest(
        @NotBlank @Size(max = 64) String barcode,
        @Size(max = 100) String shelfLocation) {
}
