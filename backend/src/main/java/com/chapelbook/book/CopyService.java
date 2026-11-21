package com.chapelbook.book;

import com.chapelbook.common.BusinessRuleException;
import com.chapelbook.common.ConflictException;
import com.chapelbook.common.ResourceNotFoundException;
import com.chapelbook.reservation.ReservationService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff management of physical copies. Locks are always taken in the same order, title first and then
 * copy, which is the order the reservation queue uses, so the two can never deadlock each other.
 */
@Service
public class CopyService {

    private static final Set<CopyStatus> STAFF_SETTABLE = Set.of(CopyStatus.AVAILABLE, CopyStatus.LOST, CopyStatus.WITHDRAWN);
    private static final Set<CopyStatus> STAFF_CHANGEABLE = STAFF_SETTABLE;

    private final BookRepository books;
    private final BookCopyRepository copies;
    private final ReservationService reservationService;

    public CopyService(BookRepository books, BookCopyRepository copies, ReservationService reservationService) {
        this.books = books;
        this.copies = copies;
        this.reservationService = reservationService;
    }

    @Transactional(readOnly = true)
    public List<CopyResponse> list(Long bookId) {
        if (!books.existsById(bookId)) {
            throw new ResourceNotFoundException("Book not found.");
        }
        return copies.findByBookIdOrderByBarcode(bookId).stream().map(CopyResponse::from).toList();
    }

    /** Adds a copy to the shelf. If students are waiting for this title, the oldest one gets it straight away. */
    @Transactional
    public CopyResponse add(Long bookId, CopyRequest request) {
        Book book = books.findByIdForUpdate(bookId).orElseThrow(() -> new ResourceNotFoundException("Book not found."));
        String barcode = request.barcode().trim();
        if (copies.existsByBarcode(barcode)) {
            throw new ConflictException("A copy with barcode \"" + barcode + "\" already exists.");
        }
        BookCopy copy = copies.saveAndFlush(new BookCopy(book, barcode, blankToNull(request.shelfLocation())));
        reservationService.serveQueue(bookId);
        return CopyResponse.from(copy);
    }

    @Transactional
    public CopyResponse update(Long copyId, CopyUpdateRequest request) {
        Long bookId = copies.findById(copyId).orElseThrow(() -> new ResourceNotFoundException("Copy not found."))
                .getBook().getId();
        books.findByIdForUpdate(bookId);
        BookCopy copy = copies.findByIdForUpdate(copyId)
                .orElseThrow(() -> new ResourceNotFoundException("Copy not found."));

        if (request.shelfLocation() != null) {
            copy.setShelfLocation(blankToNull(request.shelfLocation()));
        }
        boolean backOnShelf = false;
        if (request.status() != null && request.status() != copy.getStatus()) {
            if (!STAFF_SETTABLE.contains(request.status())) {
                throw new BusinessRuleException("A copy's status can only be set to AVAILABLE, LOST or WITHDRAWN.");
            }
            if (!STAFF_CHANGEABLE.contains(copy.getStatus())) {
                throw new BusinessRuleException("A copy that is " + copy.getStatus().name().toLowerCase()
                        .replace('_', ' ') + " cannot be changed here. Return it or cancel the reservation first.");
            }
            backOnShelf = request.status() == CopyStatus.AVAILABLE;
            copy.setStatus(request.status());
        }
        BookCopy saved = copies.saveAndFlush(copy);
        if (backOnShelf) {
            reservationService.serveQueue(bookId);
        }
        return CopyResponse.from(saved);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
