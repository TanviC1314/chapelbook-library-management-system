package com.chapelbook.borrowing;

import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.book.CopyStatus;
import com.chapelbook.common.BusinessRuleException;
import com.chapelbook.common.ConflictException;
import com.chapelbook.common.ResourceNotFoundException;
import com.chapelbook.config.LibraryProperties;
import com.chapelbook.fine.Fine;
import com.chapelbook.fine.FineCalculator;
import com.chapelbook.fine.FineRepository;
import com.chapelbook.fine.FineStatus;
import com.chapelbook.reservation.Reservation;
import com.chapelbook.reservation.ReservationService;
import com.chapelbook.security.AuthenticatedUser;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lending and taking back books.
 *
 * <p><b>Locking.</b> Like the reservation queue, every operation locks the title first and then the copy
 * ({@code SELECT ... FOR UPDATE}), always in that order, so two librarians cannot lend the same copy and a
 * return cannot race a reservation for the same title. The database backs this up: a partial unique index
 * allows only one open loan per copy.
 */
@Service
public class BorrowingService {

    private final BorrowRecordRepository loans;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final UserRepository users;
    private final FineRepository fines;
    private final ReservationService reservationService;
    private final LibraryProperties policy;
    private final Clock clock;

    public BorrowingService(BorrowRecordRepository loans, BookRepository books, BookCopyRepository copies,
            UserRepository users, FineRepository fines, ReservationService reservationService,
            LibraryProperties policy, Clock clock) {
        this.loans = loans;
        this.books = books;
        this.copies = copies;
        this.users = users;
        this.fines = fines;
        this.reservationService = reservationService;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * Lends the copy with this barcode to a student. A held copy can only go to the student it is held for;
     * if the student had a live reservation for the title it is fulfilled by this loan.
     */
    @Transactional
    public LoanResponse issue(AuthenticatedUser staff, long studentId, String barcode) {
        BookCopy probe = copies.findByBarcode(barcode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No copy has that barcode."));
        long bookId = probe.getBook().getId();
        books.findByIdForUpdate(bookId).orElseThrow(() -> new ResourceNotFoundException("Book not found."));
        BookCopy copy = copies.findByIdForUpdate(probe.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Copy not found."));

        User student = users.findById(studentId).orElseThrow(() -> new ResourceNotFoundException("Student not found."));
        if (student.getRole() != Role.STUDENT || !student.isActive()) {
            throw new BusinessRuleException("Books can only be issued to active student accounts.");
        }
        User issuer = users.findById(staff.id()).orElseThrow(() -> new ResourceNotFoundException("User not found."));

        checkCopyCanBeIssuedTo(copy, studentId);
        checkStudentMayBorrow(studentId, bookId);

        Reservation fulfilled = reservationService.fulfilForLoan(studentId, copy, bookId).orElse(null);
        copy.setStatus(CopyStatus.ON_LOAN);
        copies.saveAndFlush(copy);

        Instant now = clock.instant();
        BorrowRecord loan = loans.saveAndFlush(
                new BorrowRecord(copy, student, issuer, fulfilled, now, now.plus(policy.loanDays(), ChronoUnit.DAYS)));
        // A different copy the student was holding may have gone back on the shelf; offer it to the next in line.
        reservationService.serveQueue(bookId);
        return LoanResponse.from(loan, now, null);
    }

    /**
     * Takes a copy back. If it is late, a fine is raised for the number of started days overdue (capped per
     * loan). The copy goes back on the shelf, or straight to the student waiting longest for the title.
     */
    @Transactional
    public LoanResponse returnCopy(String barcode) {
        BookCopy probe = copies.findByBarcode(barcode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No copy has that barcode."));
        long bookId = probe.getBook().getId();
        books.findByIdForUpdate(bookId).orElseThrow(() -> new ResourceNotFoundException("Book not found."));
        BookCopy copy = copies.findByIdForUpdate(probe.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Copy not found."));

        BorrowRecord loan = loans.findByCopyIdAndReturnedAtIsNull(copy.getId())
                .orElseThrow(() -> new ConflictException("This copy is not on loan."));
        Instant now = clock.instant();
        loan.markReturned(now);
        loans.saveAndFlush(loan);
        copy.setStatus(CopyStatus.AVAILABLE);
        copies.saveAndFlush(copy);

        BigDecimal amount = FineCalculator.fineFor(loan.getDueAt(), now, policy.finePerDay(), policy.maxFinePerLoan());
        if (amount.signum() > 0) {
            fines.saveAndFlush(new Fine(loan, loan.getUser(), amount));
        }
        reservationService.serveQueue(bookId);
        return LoanResponse.from(loan, now, amount.signum() > 0 ? amount : null);
    }

    private void checkCopyCanBeIssuedTo(BookCopy copy, long studentId) {
        switch (copy.getStatus()) {
            case AVAILABLE -> { }
            case HELD -> {
                boolean heldForThisStudent = reservationService.holderOf(copy.getId())
                        .map(reservation -> reservation.getUser().getId().equals(studentId)).orElse(false);
                if (!heldForThisStudent) {
                    throw new BusinessRuleException("This copy is being held for another student.");
                }
            }
            case ON_LOAN -> throw new ConflictException("This copy is already on loan.");
            case LOST, WITHDRAWN -> throw new BusinessRuleException("This copy is "
                    + copy.getStatus().name().toLowerCase() + " and cannot be lent.");
        }
    }

    private void checkStudentMayBorrow(long studentId, long bookId) {
        if (loans.countByUserIdAndReturnedAtIsNull(studentId) >= policy.maxLoansPerStudent()) {
            throw new BusinessRuleException(
                    "This student already has the maximum of " + policy.maxLoansPerStudent() + " books on loan.");
        }
        if (loans.existsByUserIdAndReturnedAtIsNullAndDueAtBefore(studentId, clock.instant())) {
            throw new BusinessRuleException("This student has an overdue book. It must be returned first.");
        }
        if (fines.sumAmountByUserAndStatus(studentId, FineStatus.UNPAID).compareTo(policy.borrowingBlockedAbove()) > 0) {
            throw new BusinessRuleException("This student owes more than " + policy.borrowingBlockedAbove()
                    + " in unpaid fines. They must be paid first.");
        }
        if (loans.existsByUserIdAndCopyBookIdAndReturnedAtIsNull(studentId, bookId)) {
            throw new BusinessRuleException("This student already has a copy of this book on loan.");
        }
    }
}
