package com.chapelbook.seat;

/** {@code available} is only filled in when the caller asked about a time window (from and to). */
public record SeatResponse(Long id, String label, String zone, boolean hasPower, SeatStatus status, Boolean available) {

    public static SeatResponse from(LibrarySeat seat, Boolean available) {
        return new SeatResponse(seat.getId(), seat.getLabel(), seat.getZone(), seat.isHasPower(), seat.getStatus(),
                available);
    }
}
