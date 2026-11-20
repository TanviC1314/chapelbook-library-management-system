package com.chapelbook.book;

import com.chapelbook.common.AuditedEntity;
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

/** One physical item with its own barcode. This is what is actually lent, returned or lost. */
@Entity
@Table(name = "book_copies")
public class BookCopy extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CopyStatus status = CopyStatus.AVAILABLE;

    @Column(name = "shelf_location")
    private String shelfLocation;

    @Version
    private long version;

    protected BookCopy() {
        // for JPA
    }

    public BookCopy(Book book, String barcode, String shelfLocation) {
        this.book = book;
        this.barcode = barcode.trim();
        this.shelfLocation = shelfLocation;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public String getBarcode() {
        return barcode;
    }

    public CopyStatus getStatus() {
        return status;
    }

    public void setStatus(CopyStatus status) {
        this.status = status;
    }

    public String getShelfLocation() {
        return shelfLocation;
    }

    public void setShelfLocation(String shelfLocation) {
        this.shelfLocation = shelfLocation;
    }

    public long getVersion() {
        return version;
    }
}
