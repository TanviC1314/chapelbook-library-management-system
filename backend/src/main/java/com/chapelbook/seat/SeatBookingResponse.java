package com.chapelbook.seat;

import java.time.Instant;

public record SeatBookingResponse(
        Long id,
        Long seatId,
        String seatLabel,
        String zone,
        Long userId,
        String userName,
        Instant startsAt,
        Instant endsAt,
        SeatBookingStatus status) {

    public static SeatBookingResponse from(SeatBooking booking) {
        return new SeatBookingResponse(booking.getId(), booking.getSeat().getId(), booking.getSeat().getLabel(),
                booking.getSeat().getZone(), booking.getUser().getId(), booking.getUser().getFullName(),
                booking.getStartsAt(), booking.getEndsAt(), booking.getStatus());
    }
}
