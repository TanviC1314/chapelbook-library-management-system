package com.chapelbook.fine;

import com.chapelbook.common.ConflictException;
import com.chapelbook.common.PageResponse;
import com.chapelbook.common.ResourceNotFoundException;
import com.chapelbook.config.LibraryProperties;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fines are raised automatically when a late book is returned (see BorrowingService). Staff record payment
 * at the desk and admins can waive. Real card payments are out of scope: a payment here is a librarian
 * confirming that the student paid the full amount.
 */
@Service
public class FineService {

    private final FineRepository fines;
    private final LibraryProperties policy;
    private final Clock clock;

    public FineService(FineRepository fines, LibraryProperties policy, Clock clock) {
        this.fines = fines;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<FineResponse> mine(long userId, FineStatus status, Pageable pageable) {
        var page = status == null ? fines.findByUserId(userId, pageable)
                : fines.findByUserIdAndStatus(userId, status, pageable);
        return PageResponse.of(page, FineResponse::from);
    }

    @Transactional(readOnly = true)
    public FineBalanceResponse balance(long userId) {
        BigDecimal unpaid = fines.sumAmountByUserAndStatus(userId, FineStatus.UNPAID);
        return new FineBalanceResponse(unpaid, policy.borrowingBlockedAbove(),
                unpaid.compareTo(policy.borrowingBlockedAbove()) > 0);
    }

    @Transactional(readOnly = true)
    public PageResponse<FineResponse> list(Long userId, FineStatus status, Pageable pageable) {
        var page = userId != null
                ? (status == null ? fines.findByUserId(userId, pageable) : fines.findByUserIdAndStatus(userId, status, pageable))
                : (status == null ? fines.findAll(pageable) : fines.findByStatus(status, pageable));
        return PageResponse.of(page, FineResponse::from);
    }

    /**
     * Records that the fine was paid in full. Two librarians clicking at once cannot both succeed: the fine
     * carries a version number, so the second save is refused (and reported as a conflict).
     */
    @Transactional
    public FineResponse pay(long id) {
        Fine fine = open(id, "paid");
        fine.markPaid(clock.instant());
        return FineResponse.from(fines.saveAndFlush(fine));
    }

    @Transactional
    public FineResponse waive(long id) {
        Fine fine = open(id, "waived");
        fine.waive();
        return FineResponse.from(fines.saveAndFlush(fine));
    }

    private Fine open(long id, String action) {
        Fine fine = fines.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fine not found."));
        if (fine.getStatus() != FineStatus.UNPAID) {
            throw new ConflictException("This fine is already " + fine.getStatus().name().toLowerCase()
                    + " and cannot be " + action + ".");
        }
        return fine;
    }
}
