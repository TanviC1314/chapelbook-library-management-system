package com.chapelbook.borrowing;

import com.chapelbook.book.BookCopy;
import com.chapelbook.common.AuditedEntity;
import com.chapelbook.reservation.Reservation;
import com.chapelbook.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** One loan of one copy to one student. {@code returnedAt} stays null while the book is out. */
@Entity
@Table(name = "borrow_records")
public class BorrowRecord extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "copy_id", nullable = false)
    private BookCopy copy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The librarian who issued the loan. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by", nullable = false)
    private User issuedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "borrowed_at", nullable = false)
    private Instant borrowedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "renewal_count", nullable = false)
    private int renewalCount;

    @Version
    private long version;

    protected BorrowRecord() {
        // for JPA
    }

    public BorrowRecord(BookCopy copy, User user, User issuedBy, Reservation reservation,
            Instant borrowedAt, Instant dueAt) {
        this.copy = copy;
        this.user = user;
        this.issuedBy = issuedBy;
        this.reservation = reservation;
        this.borrowedAt = borrowedAt;
        this.dueAt = dueAt;
    }

    public Long getId() {
        return id;
    }

    public BookCopy getCopy() {
        return copy;
    }

    public User getUser() {
        return user;
    }

    public User getIssuedBy() {
        return issuedBy;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public int getRenewalCount() {
        return renewalCount;
    }

    public long getVersion() {
        return version;
    }

    public boolean isReturned() {
        return returnedAt != null;
    }

    public boolean isOverdueAt(Instant moment) {
        Instant end = returnedAt != null ? returnedAt : moment;
        return end.isAfter(dueAt);
    }

    public void markReturned(Instant when) {
        this.returnedAt = when;
    }

    public void renew(Instant newDueAt) {
        this.dueAt = newDueAt;
        this.renewalCount++;
    }
}
