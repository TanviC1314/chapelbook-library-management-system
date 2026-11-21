package com.chapelbook.book;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @Pattern(regexp = "^[0-9Xx -]{10,20}$", message = "must be an ISBN-10 or ISBN-13") String isbn,
        @NotBlank @Size(max = 300) String title,
        @NotBlank @Size(max = 200) String author,
        @Size(max = 200) String publisher,
        @Min(1000) @Max(2200) Integer publicationYear,
        @Size(max = 2000) String description,
        @NotNull Long categoryId) {
}
