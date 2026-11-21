package com.chapelbook.reservation;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
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
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /** The copy held for the student. Only set while the reservation is READY (or afterwards, for history). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copy_id")
    private BookCopy copy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt = Instant.now();

    @Column(name = "ready_until")
    private Instant readyUntil;

    @Version
    private long version;

    protected Reservation() {
        // for JPA
    }

    public Reservation(User user, Book book) {
        this.user = user;
        this.book = book;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Book getBook() {
        return book;
    }

    public BookCopy getCopy() {
        return copy;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getReadyUntil() {
        return readyUntil;
    }

    public long getVersion() {
        return version;
    }

    /** A copy has become available: hold it for this student until the deadline. */
    public void markReady(BookCopy heldCopy, Instant collectBy) {
        this.copy = heldCopy;
        this.readyUntil = collectBy;
        this.status = ReservationStatus.READY;
    }

    /** The student collected the book: remember which copy they ended up borrowing. */
    public void markFulfilled(BookCopy borrowedCopy) {
        this.copy = borrowedCopy;
        this.status = ReservationStatus.FULFILLED;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }
}
