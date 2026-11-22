package com.chapelbook.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.borrowing.BorrowRecord;
import com.chapelbook.borrowing.BorrowRecordRepository;
import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.fine.Fine;
import com.chapelbook.fine.FineRepository;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The data sits in March 2020, a window no other test writes to, so the expected numbers are exact. Layout:
 * <pre>
 *   Mar 1  L1 Alpha  s1 issued, returned Mar 2 (on time)      L2 Alpha  s2 issued, returned Mar 20 (outside window)
 *   Mar 3  L3 Beta   s1 issued, returned Mar 4 (on time)
 *   Mar 4  L4 Gamma  s3 issued (due the same day), returned Mar 5 (late) -> fine 0.50 raised Mar 5
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class CirculationStatisticsIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

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
    private JdbcTemplate jdbc;

    private String admin;
    private User librarian;

    private static Instant at(int day, int hour) {
        return LocalDate.of(2020, 3, day).atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    @BeforeEach
    void setUp() {
        admin = auth.bearerFor(Role.ADMIN);
        librarian = auth.createUser(Role.LIBRARIAN);
        String run = Long.toString(System.nanoTime());
        User s1 = auth.createUser(Role.STUDENT);
        User s2 = auth.createUser(Role.STUDENT);
        User s3 = auth.createUser(Role.STUDENT);
        Category science = categories.saveAndFlush(new Category("Stats science " + run, null));
        Category art = categories.saveAndFlush(new Category("Stats art " + run, null));
        Book alpha = books.saveAndFlush(new Book("Alpha " + run, "Author A", science));
        Book beta = books.saveAndFlush(new Book("Beta " + run, "Author B", science));
        Book gamma = books.saveAndFlush(new Book("Gamma " + run, "Author C", art));

        loan(alpha, s1, at(1, 10), at(15, 10), at(2, 10));
        loan(alpha, s2, at(1, 11), at(15, 11), at(20, 9));
        loan(beta, s1, at(3, 9), at(17, 9), at(4, 9));
        BorrowRecord late = loan(gamma, s3, at(4, 9), at(4, 12), at(5, 10));
        Fine fine = fines.saveAndFlush(new Fine(late, s3, new BigDecimal("0.50")));
        jdbc.update("UPDATE fines SET assessed_at = ? WHERE id = ?", at(5, 10).atOffset(ZoneOffset.UTC), fine.getId());
    }

    private BorrowRecord loan(Book book, User student, Instant borrowedAt, Instant dueAt, Instant returnedAt) {
        BookCopy copy = copies.saveAndFlush(new BookCopy(book, "ST-" + System.nanoTime() + "-" + SEQ.incrementAndGet(), null));
        BorrowRecord loan = loans.saveAndFlush(new BorrowRecord(copy, student, librarian, null, borrowedAt, dueAt));
        loan.markReturned(returnedAt);
        return loans.saveAndFlush(loan);
    }

    @Test
    void totalsCountOnlyWhatHappenedInsideTheRange() {
        var report = statistics.circulation(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 3, 5));

        assertThat(report.totals().loansIssued()).isEqualTo(4);
        assertThat(report.totals().loansReturned()).isEqualTo(3); // L2 came back on Mar 20, outside the window
        assertThat(report.totals().returnedLate()).isEqualTo(1);
        assertThat(report.totals().distinctBorrowers()).isEqualTo(3);
        assertThat(report.totals().finesRaised()).isEqualTo(1);
        assertThat(report.totals().finesAmount()).isEqualByComparingTo("0.50");
    }

    @Test
    void theDailySeriesHasOneEntryPerDayIncludingQuietDays() {
        var report = statistics.circulation(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 3, 5));

        assertThat(report.daily()).hasSize(5);
        assertThat(report.daily()).extracting(d -> d.date().getDayOfMonth()).containsExactly(1, 2, 3, 4, 5);
        assertThat(report.daily()).extracting(d -> d.loansIssued()).containsExactly(2L, 0L, 1L, 1L, 0L);
        assertThat(report.daily()).extracting(d -> d.loansReturned()).containsExactly(0L, 1L, 0L, 1L, 1L);
        assertThat(report.daily()).extracting(d -> d.finesRaised()).containsExactly(0L, 0L, 0L, 0L, 1L);
        assertThat(report.daily().get(1).finesAmount()).isEqualByComparingTo("0.00");
        assertThat(report.daily().get(4).finesAmount()).isEqualByComparingTo("0.50");
    }

    @Test
    void topBooksAreRankedByLoansThenTitle() {
        var report = statistics.circulation(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 3, 5));

        assertThat(report.topBooks()).extracting(b -> b.title().split(" ")[0]).containsExactly("Alpha", "Beta", "Gamma");
        assertThat(report.topBooks()).extracting(b -> b.loans()).containsExactly(2L, 1L, 1L);
    }

    @Test
    void loansAreSplitByCategory() {
        var report = statistics.circulation(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 3, 5));

        assertThat(report.byCategory()).extracting(c -> c.category().split(" ")[1]).containsExactly("science", "art");
        assertThat(report.byCategory()).extracting(c -> c.loans()).containsExactly(3L, 1L);
    }

    @Test
    void aNarrowerRangeExcludesOtherDays() {
        var report = statistics.circulation(LocalDate.of(2020, 3, 4), LocalDate.of(2020, 3, 4));

        assertThat(report.daily()).hasSize(1);
        assertThat(report.totals().loansIssued()).isEqualTo(1);
        assertThat(report.totals().loansReturned()).isEqualTo(1);
        assertThat(report.topBooks()).hasSize(1);
    }

    @Test
    void anEmptyRangeGivesZerosNotErrors() {
        var report = statistics.circulation(LocalDate.of(1999, 1, 1), LocalDate.of(1999, 1, 3));

        assertThat(report.daily()).hasSize(3);
        assertThat(report.totals().loansIssued()).isZero();
        assertThat(report.totals().finesAmount()).isEqualByComparingTo("0");
        assertThat(report.topBooks()).isEmpty();
        assertThat(report.byCategory()).isEmpty();
    }

    @Test
    void theEndpointReturnsTheReportToAdminsOnly() throws Exception {
        mvc.perform(get("/api/v1/statistics/circulation").param("from", "2020-03-01").param("to", "2020-03-05")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2020-03-01"))
                .andExpect(jsonPath("$.totals.loansIssued").value(4))
                .andExpect(jsonPath("$.daily", hasSize(5)))
                .andExpect(jsonPath("$.topBooks[0].loans").value(2));
        mvc.perform(get("/api/v1/statistics/circulation").header("Authorization", auth.bearer(librarian)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/statistics/circulation").header("Authorization", auth.bearerFor(Role.STUDENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/statistics/circulation")).andExpect(status().isUnauthorized());
    }

    @Test
    void withNoDatesItCoversTheLastThirtyDays() throws Exception {
        mvc.perform(get("/api/v1/statistics/circulation").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily", hasSize(30)));
    }

    @Test
    void badRangesAreRefused() throws Exception {
        mvc.perform(get("/api/v1/statistics/circulation").param("from", "2020-03-05").param("to", "2020-03-01")
                        .header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/statistics/circulation").param("from", "2018-01-01").param("to", "2020-01-01")
                        .header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/statistics/circulation").param("from", "not-a-date").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        // exactly 366 days is the largest allowed range
        mvc.perform(get("/api/v1/statistics/circulation").param("from", "2019-12-31").param("to", "2020-12-30")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
    }
}
