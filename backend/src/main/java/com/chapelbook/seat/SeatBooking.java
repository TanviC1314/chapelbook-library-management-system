package com.chapelbook.seat;

import com.chapelbook.common.AuditedEntity;
import com.chapelbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A student's booking of one seat for a half-open time range [startsAt, endsAt). */
@Entity
@Table(name = "seat_bookings")
public class SeatBooking extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private LibrarySeat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatBookingStatus status = SeatBookingStatus.BOOKED;

    protected SeatBooking() {
        // for JPA
    }

    public SeatBooking(LibrarySeat seat, User user, Instant startsAt, Instant endsAt) {
        this.seat = seat;
        this.user = user;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public Long getId() {
        return id;
    }

    public LibrarySeat getSeat() {
        return seat;
    }

    public User getUser() {
        return user;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public SeatBookingStatus getStatus() {
        return status;
    }

    public void cancel() {
        this.status = SeatBookingStatus.CANCELLED;
    }
}
