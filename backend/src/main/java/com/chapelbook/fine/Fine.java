package com.chapelbook.fine;

import com.chapelbook.borrowing.BorrowRecord;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/** The fine for one overdue loan. Money is BigDecimal with two decimal places, never a double. */
@Entity
@Table(name = "fines")
public class Fine extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrow_record_id", nullable = false, unique = true)
    private BorrowRecord borrowRecord;

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FineStatus status = FineStatus.UNPAID;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    private long version;

    protected Fine() {
        // for JPA
    }

    public Fine(BorrowRecord borrowRecord, User user, BigDecimal amount) {
        this.borrowRecord = borrowRecord;
        this.user = user;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public BorrowRecord getBorrowRecord() {
        return borrowRecord;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public FineStatus getStatus() {
        return status;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public long getVersion() {
        return version;
    }

    public void markPaid(Instant when) {
        this.status = FineStatus.PAID;
        this.paidAt = when;
    }

    public void waive() {
        this.status = FineStatus.WAIVED;
    }
}
