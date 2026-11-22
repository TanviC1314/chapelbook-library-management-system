package com.chapelbook.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.book.CopyStatus;
import com.chapelbook.borrowing.BorrowRecord;
import com.chapelbook.borrowing.BorrowRecordRepository;
import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.fine.Fine;
import com.chapelbook.fine.FineRepository;
import com.chapelbook.reservation.ReservationService;
import com.chapelbook.seat.LibrarySeat;
import com.chapelbook.seat.LibrarySeatRepository;
import com.chapelbook.seat.SeatBooking;
import com.chapelbook.seat.SeatBookingRepository;
import com.chapelbook.seat.SeatStatus;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Other tests commit rows to the shared test database, so absolute totals are unpredictable. Each test therefore
 * takes the overview BEFORE adding data and AFTER, and checks the difference: exactly what this test added.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class OverviewIntegrationTest {

    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TestAuth auth;
    @Autowired
    private StatisticsService statistics;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private BookRepository books;
    @Autowired
    private BookCopyRepository copies;
    @Autowired
    private BorrowRecordRepository loans;
    @Autowired
    private FineRepository fines;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private LibrarySeatRepository seats;
    @Autowired
    private SeatBookingRepository bookings;
    @Autowired
    private UserRepository users;

    @Test
    void everyFigureMovesByExactlyWhatWasAdded() {
        OverviewResponse before = statistics.overview();

        // accounts: 2 students, 1 librarian, 1 admin, 1 deactivated student
        User s1 = auth.createUser(Role.STUDENT);
        User s2 = auth.createUser(Role.STUDENT);
        User lib = auth.createUser(Role.LIBRARIAN);
        auth.createUser(Role.ADMIN);
        User gone = auth.createUser(Role.STUDENT);
        gone.setActive(false);
        users.saveAndFlush(gone);

        // catalogue: 1 category, 2 titles, copies in every status
        Category category = categories.saveAndFlush(new Category("Overview cat " + System.nanoTime(), null));
        Book book = books.saveAndFlush(new Book("Overview book " + System.nanoTime(), "A", category));
        Book other = books.saveAndFlush(new Book("Overview other " + System.nanoTime(), "A", category));
        BookCopy available = copy(book, CopyStatus.AVAILABLE);
        BookCopy onLoan = copy(book, CopyStatus.ON_LOAN);
        BookCopy overdueCopy = copy(book, CopyStatus.ON_LOAN);
        copy(book, CopyStatus.HELD);
        copy(other, CopyStatus.LOST);
        copy(other, CopyStatus.WITHDRAWN);

        // circulation: one loan out and on time (issued today), one overdue, one returned today
        Instant now = Instant.now();
        loans.saveAndFlush(new BorrowRecord(onLoan, s1, lib, null, now, now.plus(14, ChronoUnit.DAYS)));
        loans.saveAndFlush(new BorrowRecord(overdueCopy, s2, lib, null, now.minus(30, ChronoUnit.DAYS), now.minus(16, ChronoUnit.DAYS)));
        BorrowRecord returned = loans.saveAndFlush(new BorrowRecord(available, s1, lib, null,
                now.minus(20, ChronoUnit.DAYS), now.minus(6, ChronoUnit.DAYS)));
        returned.markReturned(now);
        loans.saveAndFlush(returned);

        // reservations: one pending (no copy free for "other" title), one ready
        reservationService.reserve(s1.getId(), other.getId());
        reservationService.reserve(s2.getId(), other.getId());
        // fines: two unpaid (2.50 + 1.25), one paid (not counted)
        Fine f1 = fines.saveAndFlush(new Fine(returned, s1, new BigDecimal("2.50")));
        BorrowRecord r2 = loans.saveAndFlush(new BorrowRecord(copy(other, CopyStatus.AVAILABLE), s2, lib, null,
                now.minus(40, ChronoUnit.DAYS), now.minus(30, ChronoUnit.DAYS)));
        r2.markReturned(now.minus(25, ChronoUnit.DAYS));
        loans.saveAndFlush(r2);
        fines.saveAndFlush(new Fine(r2, s2, new BigDecimal("1.25")));
        BorrowRecord r3 = loans.saveAndFlush(new BorrowRecord(copy(other, CopyStatus.AVAILABLE), s2, lib, null,
                now.minus(60, ChronoUnit.DAYS), now.minus(50, ChronoUnit.DAYS)));
        r3.markReturned(now.minus(45, ChronoUnit.DAYS));
        loans.saveAndFlush(r3);
        Fine paid = fines.saveAndFlush(new Fine(r3, s2, new BigDecimal("9.00")));
        paid.markPaid(now);
        fines.saveAndFlush(paid);
        assertThat(f1.getId()).isNotNull();

        // seats: 2 in service, 1 out of service, one booking in progress, one upcoming
        LibrarySeat a = seats.saveAndFlush(new LibrarySeat("OV-A-" + System.nanoTime(), "Zone", false));
        LibrarySeat b = seats.saveAndFlush(new LibrarySeat("OV-B-" + System.nanoTime(), "Zone", false));
        LibrarySeat c = seats.saveAndFlush(new LibrarySeat("OV-C-" + System.nanoTime(), "Zone", false));
        c.setStatus(SeatStatus.OUT_OF_SERVICE);
        seats.saveAndFlush(c);
        bookings.saveAndFlush(new SeatBooking(a, s1, now.minus(30, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.MINUTES)));
        bookings.saveAndFlush(new SeatBooking(b, s2, now.plus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS)));

        OverviewResponse after = statistics.overview();

        assertThat(after.users().students() - before.users().students()).isEqualTo(2);
        assertThat(after.users().librarians() - before.users().librarians()).isEqualTo(1);
        assertThat(after.users().admins() - before.users().admins()).isEqualTo(1);
        assertThat(after.users().deactivated() - before.users().deactivated()).isEqualTo(1);

        assertThat(after.catalogue().titles() - before.catalogue().titles()).isEqualTo(2);
        assertThat(after.catalogue().categories() - before.catalogue().categories()).isEqualTo(1);
        // copies added: 3 available (one for the first loan, two for the fine scenarios), 2 on loan, 1 held,
        // 1 lost, 1 withdrawn. Lost and withdrawn copies are not "in circulation".
        assertThat(after.catalogue().copiesAvailable() - before.catalogue().copiesAvailable()).isEqualTo(3);
        assertThat(after.catalogue().copiesHeld() - before.catalogue().copiesHeld()).isEqualTo(1);
        assertThat(after.catalogue().copiesInCirculation() - before.catalogue().copiesInCirculation()).isEqualTo(6);
        assertThat(after.reservations().pending() - before.reservations().pending()).isEqualTo(2);
        assertThat(after.catalogue().copiesOnLoan() - before.catalogue().copiesOnLoan()).isEqualTo(2);
        assertThat(after.catalogue().copiesLost() - before.catalogue().copiesLost()).isEqualTo(1);
        assertThat(after.catalogue().copiesWithdrawn() - before.catalogue().copiesWithdrawn()).isEqualTo(1);

        assertThat(after.circulation().activeLoans() - before.circulation().activeLoans()).isEqualTo(2);
        assertThat(after.circulation().overdueLoans() - before.circulation().overdueLoans()).isEqualTo(1);
        assertThat(after.circulation().issuedToday() - before.circulation().issuedToday()).isGreaterThanOrEqualTo(1);
        assertThat(after.circulation().returnedToday() - before.circulation().returnedToday()).isEqualTo(1);

        assertThat(after.fines().unpaidCount() - before.fines().unpaidCount()).isEqualTo(2);
        assertThat(after.fines().unpaidTotal().subtract(before.fines().unpaidTotal())).isEqualByComparingTo("3.75");

        assertThat(after.seats().inService() - before.seats().inService()).isEqualTo(2);
        assertThat(after.seats().outOfService() - before.seats().outOfService()).isEqualTo(1);
        assertThat(after.seats().bookedNow() - before.seats().bookedNow()).isEqualTo(1);
        assertThat(after.seats().upcomingBookings() - before.seats().upcomingBookings()).isEqualTo(2);
    }

    private BookCopy copy(Book book, CopyStatus status) {
        BookCopy copy = copies.saveAndFlush(new BookCopy(book, "OV-" + System.nanoTime() + "-" + SEQ.incrementAndGet(), null));
        copy.setStatus(status);
        return copies.saveAndFlush(copy);
    }

    @Test
    void reservationsAreCountedByStatus() {
        OverviewResponse before = statistics.overview();
        User s1 = auth.createUser(Role.STUDENT);
        User s2 = auth.createUser(Role.STUDENT);
        Category category = categories.saveAndFlush(new Category("Overview res cat " + System.nanoTime(), null));
        Book heldTitle = books.saveAndFlush(new Book("Held title " + System.nanoTime(), "A", category));
        Book emptyTitle = books.saveAndFlush(new Book("Empty title " + System.nanoTime(), "A", category));
        copies.saveAndFlush(new BookCopy(heldTitle, "OVR-" + System.nanoTime(), null));

        reservationService.reserve(s1.getId(), heldTitle.getId()); // a copy is free: READY
        reservationService.reserve(s2.getId(), emptyTitle.getId()); // nothing free: PENDING

        OverviewResponse after = statistics.overview();
        assertThat(after.reservations().ready() - before.reservations().ready()).isEqualTo(1);
        assertThat(after.reservations().pending() - before.reservations().pending()).isEqualTo(1);
        assertThat(after.catalogue().copiesHeld() - before.catalogue().copiesHeld()).isEqualTo(1);
    }

    @Test
    void theEndpointIsStaffOnlyAndReturnsTheDocumentedShape() throws Exception {
        mvc.perform(get("/api/v1/statistics/overview").header("Authorization", auth.bearerFor(Role.LIBRARIAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.users.students").isNumber())
                .andExpect(jsonPath("$.catalogue.copiesInCirculation").isNumber())
                .andExpect(jsonPath("$.circulation.overdueLoans").isNumber())
                .andExpect(jsonPath("$.reservations.pending").isNumber())
                .andExpect(jsonPath("$.fines.unpaidTotal").isNumber())
                .andExpect(jsonPath("$.seats.inService").isNumber());
        mvc.perform(get("/api/v1/statistics/overview").header("Authorization", auth.bearerFor(Role.ADMIN)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/statistics/overview").header("Authorization", auth.bearerFor(Role.STUDENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/statistics/overview")).andExpect(status().isUnauthorized());
    }
}
