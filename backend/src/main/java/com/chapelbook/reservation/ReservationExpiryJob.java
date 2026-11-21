package com.chapelbook.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every few minutes, expires held reservations that were not collected in time so the copy goes to the
 * next student. Each reservation is expired in its own transaction, so one failure does not block the rest.
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final ReservationService service;

    public ReservationExpiryJob(ReservationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void run() {
        int expired = 0;
        for (Long id : service.findExpiredIds()) {
            try {
                if (service.expire(id)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not expire reservation {}: {}", id, e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Expired {} uncollected reservation(s).", expired);
        }
    }
}
