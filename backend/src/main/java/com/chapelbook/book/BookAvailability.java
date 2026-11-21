package com.chapelbook.book;

public record BookAvailability(int totalCopies, int availableCopies, int pendingReservations) {

    public static final BookAvailability NONE = new BookAvailability(0, 0, 0);
}
