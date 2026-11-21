package com.chapelbook.book;

import com.chapelbook.category.CategoryResponse;

/**
 * A title with its live availability. {@code totalCopies} counts copies in circulation (available,
 * on loan or held), not lost or withdrawn ones. {@code pendingReservations} is the queue length.
 */
public record BookResponse(
        Long id,
        String isbn,
        String title,
        String author,
        String publisher,
        Integer publicationYear,
        String description,
        CategoryResponse category,
        int totalCopies,
        int availableCopies,
        int pendingReservations) {

    public static BookResponse from(Book book, BookAvailability availability) {
        return new BookResponse(book.getId(), book.getIsbn(), book.getTitle(), book.getAuthor(), book.getPublisher(),
                book.getPublicationYear(), book.getDescription(), CategoryResponse.from(book.getCategory()),
                availability.totalCopies(), availability.availableCopies(), availability.pendingReservations());
    }
}
