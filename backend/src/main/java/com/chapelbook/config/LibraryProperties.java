package com.chapelbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Library policy from {@code app.library.*}. Defaults apply when a value is not set. */
@ConfigurationProperties("app.library")
public record LibraryProperties(Integer reservationHoldDays, Integer maxActiveReservations) {

    public LibraryProperties {
        if (reservationHoldDays == null || reservationHoldDays < 1) {
            reservationHoldDays = 3;
        }
        if (maxActiveReservations == null || maxActiveReservations < 1) {
            maxActiveReservations = 5;
        }
    }
}
