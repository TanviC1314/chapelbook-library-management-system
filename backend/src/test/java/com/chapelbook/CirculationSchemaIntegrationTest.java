package com.chapelbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chapelbook.support.PostgresTestContainerConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the circulation rules (one open loan per copy, one active reservation per student and
 * book, sensible dates and fines) are enforced by PostgreSQL itself. Each test is rolled back.
 */
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
@Transactional
class CirculationSchemaIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbc;

    private long student1;
    private long student2;
    private long librarian;
    private long book;
    private long copy1;
    private long copy2;

    @BeforeEach
    void createBaseData() {
        int n = SEQUENCE.incrementAndGet();
        student1 = insertUser("s1-" + n + "@example.com", "STUDENT");
        student2 = insertUser("s2-" + n + "@example.com", "STUDENT");
        librarian = insertUser("lib-" + n + "@example.com", "LIBRARIAN");
        long category = jdbc.queryForObject(
                "INSERT INTO categories (name) VALUES (?) RETURNING id", Long.class, "Category " + n);
        book = jdbc.queryForObject(
                "INSERT INTO books (title, author, category_id) VALUES ('Book', 'Author', ?) RETURNING id",
                Long.class, category);
        copy1 = insertCopy("C1-" + n);
        copy2 = insertCopy("C2-" + n);
    }

    private long insertUser(String email, String role) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, 'hash', 'Test User', ?) RETURNING id
                """, Long.class, email, role);
    }

    private long insertCopy(String barcode) {
        return jdbc.queryForObject(
                "INSERT INTO book_copies (book_id, barcode) VALUES (?, ?) RETURNING id", Long.class, book, barcode);
    }

    private long reserve(long user, String status, Long copy, String readyUntil) {
        return jdbc.queryForObject("""
                INSERT INTO reservations (user_id, book_id, copy_id, status, ready_until)
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz)) RETURNING id
                """, Long.class, user, book, copy, status, readyUntil);
    }

    private long lend(long copy, long user, String borrowedAt, String dueAt, String returnedAt) {
        return jdbc.queryForObject("""
                INSERT INTO borrow_records (copy_id, user_id, issued_by, borrowed_at, due_at, returned_at)
                VALUES (?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), CAST(? AS timestamptz))
                RETURNING id
                """, Long.class, copy, user, librarian, borrowedAt, dueAt, returnedAt);
    }

    private void fine(long borrowRecord, long user, String amount, String status, String paidAt) {
        jdbc.update("""
                INSERT INTO fines (borrow_record_id, user_id, amount, status, paid_at)
                VALUES (?, ?, CAST(? AS numeric), ?, CAST(? AS timestamptz))
                """, borrowRecord, user, amount, status, paidAt);
    }

    // ---------------------------------------------------------------- reservations

    @Test
    void studentCannotHaveTwoActiveReservationsForTheSameBook() {
        reserve(student1, "PENDING", null, null);

        assertThatThrownBy(() -> reserve(student1, "PENDING", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_reservations_active_user_book");
    }

    @Test
    void differentStudentsMayReserveTheSameBook() {
        reserve(student1, "PENDING", null, null);

        assertThatCode(() -> reserve(student2, "PENDING", null, null)).doesNotThrowAnyException();
    }

    @Test
    void studentMayReserveAgainAfterCancelling() {
        reserve(student1, "CANCELLED", null, null);

        assertThatCode(() -> reserve(student1, "PENDING", null, null)).doesNotThrowAnyException();
    }

    @Test
    void aCopyCanBeHeldForOnlyOneReservation() {
        reserve(student1, "READY", copy1, "2030-01-10T10:00:00Z");

        assertThatThrownBy(() -> reserve(student2, "READY", copy1, "2030-01-10T10:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_reservations_ready_copy");
    }

    @Test
    void readyReservationMustNameACopyAndADeadline() {
        assertThatThrownBy(() -> reserve(student1, "READY", null, "2030-01-10T10:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reservations_ready_has_copy");
    }

    @Test
    void readyReservationMustHaveACollectionDeadline() {
        assertThatThrownBy(() -> reserve(student1, "READY", copy1, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reservations_ready_has_copy");
    }

    @Test
    void pendingReservationCannotHaveACopyAssigned() {
        assertThatThrownBy(() -> reserve(student1, "PENDING", copy1, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reservations_pending_has_no_copy");
    }

    @Test
    void rejectsUnknownReservationStatus() {
        assertThatThrownBy(() -> reserve(student1, "WAITING", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reservations_status");
    }

    // ---------------------------------------------------------------- loans

    @Test
    void aCopyCannotBeLentTwiceWhileOnLoan() {
        lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", null);

        assertThatThrownBy(() -> lend(copy1, student2, "2030-01-02T09:00:00Z", "2030-01-16T09:00:00Z", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_borrow_records_open_copy");
    }

    @Test
    void aCopyCanBeLentAgainOnceReturned() {
        lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", "2030-01-10T09:00:00Z");

        assertThatCode(() -> lend(copy1, student2, "2030-01-11T09:00:00Z", "2030-01-25T09:00:00Z", null))
                .doesNotThrowAnyException();
    }

    @Test
    void differentCopiesOfTheSameBookCanBeOnLoanAtOnce() {
        lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", null);

        assertThatCode(() -> lend(copy2, student2, "2030-01-01T10:00:00Z", "2030-01-15T10:00:00Z", null))
                .doesNotThrowAnyException();
    }

    @Test
    void dueDateMustBeAfterBorrowDate() {
        assertThatThrownBy(() -> lend(copy1, student1, "2030-01-10T09:00:00Z", "2030-01-10T09:00:00Z", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_borrow_records_due_after_borrowed");
    }

    @Test
    void returnDateCannotBeBeforeBorrowDate() {
        assertThatThrownBy(() -> lend(copy1, student1, "2030-01-10T09:00:00Z", "2030-01-24T09:00:00Z",
                "2030-01-09T09:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_borrow_records_returned_after_borrowed");
    }

    @Test
    void aReservationCanBeFulfilledByOnlyOneLoan() {
        long reservation = reserve(student1, "FULFILLED", copy1, "2030-01-10T10:00:00Z");
        jdbc.update("""
                INSERT INTO borrow_records (copy_id, user_id, issued_by, reservation_id, due_at)
                VALUES (?, ?, ?, ?, now() + interval '14 days')
                """, copy1, student1, librarian, reservation);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO borrow_records (copy_id, user_id, issued_by, reservation_id, due_at)
                VALUES (?, ?, ?, ?, now() + interval '14 days')
                """, copy2, student1, librarian, reservation))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_borrow_records_reservation");
    }

    @Test
    void overdueReportQueryUsesTheOpenLoanDueDateIndex() {
        lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", null);
        jdbc.execute("SET LOCAL enable_seqscan = off");

        String plan = String.join("\n", jdbc.queryForList(
                "EXPLAIN SELECT id FROM borrow_records WHERE returned_at IS NULL AND due_at < now()", String.class));

        assertThat(plan).contains("ix_borrow_records_open_due");
    }

    // ---------------------------------------------------------------- fines

    @Test
    void aLoanCanHaveOnlyOneFine() {
        long loan = lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", "2030-01-20T09:00:00Z");
        fine(loan, student1, "2.50", "UNPAID", null);

        assertThatThrownBy(() -> fine(loan, student1, "1.00", "UNPAID", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_fines_borrow_record");
    }

    @Test
    void fineAmountMustBePositive() {
        long loan = lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", "2030-01-20T09:00:00Z");

        assertThatThrownBy(() -> fine(loan, student1, "0.00", "UNPAID", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_fines_amount_positive");
    }

    @Test
    void aPaidFineMustRecordWhenItWasPaid() {
        long loan = lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", "2030-01-20T09:00:00Z");

        assertThatThrownBy(() -> fine(loan, student1, "2.50", "PAID", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_fines_paid_has_date");
    }

    @Test
    void acceptsAPaidFineWithADate() {
        long loan = lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", "2030-01-20T09:00:00Z");

        assertThatCode(() -> fine(loan, student1, "2.50", "PAID", "2030-01-21T09:00:00Z"))
                .doesNotThrowAnyException();
    }

    @Test
    void fineMoneyKeepsExactlyTwoDecimalPlaces() {
        long loan = lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", "2030-01-20T09:00:00Z");
        fine(loan, student1, "0.105", "UNPAID", null);

        String stored = jdbc.queryForObject("SELECT amount::text FROM fines WHERE borrow_record_id = ?",
                String.class, loan);
        assertThat(stored).isEqualTo("0.11");
    }

    @Test
    void historyIsProtectedFromDeletingABorrowedCopy() {
        lend(copy1, student1, "2030-01-01T09:00:00Z", "2030-01-15T09:00:00Z", null);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM book_copies WHERE id = ?", copy1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("borrow_records_copy_id_fkey");
    }
}
