package com.chapelbook.book;

public record CopyResponse(Long id, Long bookId, String barcode, CopyStatus status, String shelfLocation) {

    public static CopyResponse from(BookCopy copy) {
        return new CopyResponse(copy.getId(), copy.getBook().getId(), copy.getBarcode(), copy.getStatus(),
                copy.getShelfLocation());
    }
}
