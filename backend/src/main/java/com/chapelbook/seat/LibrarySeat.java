package com.chapelbook.seat;

import com.chapelbook.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "library_seats")
public class LibrarySeat extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String zone;

    @Column(name = "has_power", nullable = false)
    private boolean hasPower;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.ACTIVE;

    protected LibrarySeat() {
        // for JPA
    }

    public LibrarySeat(String label, String zone, boolean hasPower) {
        this.label = label.trim();
        this.zone = zone.trim();
        this.hasPower = hasPower;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getZone() {
        return zone;
    }

    public boolean isHasPower() {
        return hasPower;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    public void setZone(String zone) {
        this.zone = zone.trim();
    }

    public void setHasPower(boolean hasPower) {
        this.hasPower = hasPower;
    }
}
