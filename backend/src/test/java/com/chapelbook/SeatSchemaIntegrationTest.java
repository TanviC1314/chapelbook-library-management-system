package com.chapelbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chapelbook.support.PostgresTestContainerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves a seat cannot be double booked, using the database's own exclusion constraint.
 *
 * <p>These tests deliberately do <b>not</b> use a rolled-back transaction: the concurrency test needs
 * real commits from several connections. Each test creates its own uniquely named rows instead.
 */
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class SeatSchemaIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbc;

    private long seat;
    private long otherSeat;
    private long student1;
    private long student2;

    @BeforeEach
    void createBaseData() {
        int n = SEQUENCE.incrementAndGet();
        seat = insertSeat("A-" + n);
        otherSeat = insertSeat("B-" + n);
        student1 = insertStudent("seat-s1-" + n + "@example.com");
        student2 = insertStudent("seat-s2-" + n + "@example.com");
    }

    private long insertSeat(String label) {
        return jdbc.queryForObject(
                "INSERT INTO library_seats (label, zone) VALUES (?, 'Quiet') RETURNING id", Long.class, label);
    }

    private long insertStudent(String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, 'hash', 'Student', 'STUDENT') RETURNING id
                """, Long.class, email);
    }

    private void book(long seatId, long userId, String from, String to, String status) {
        jdbc.update("""
                INSERT INTO seat_bookings (seat_id, user_id, starts_at, ends_at, status)
                VALUES (?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), ?)
                """, seatId, userId, from, to, status);
    }

    private void book(long seatId, long userId, String from, String to) {
        book(seatId, userId, from, to, "BOOKED");
    }

    @Test
    void acceptsABooking() {
        assertThatCode(() -> book(seat, student1, "2031-03-01T09:00:00Z", "2031-03-01T11:00:00Z"))
                .doesNotThrowAnyException();
    }

    @Test
    void sameSeatCannotBeBookedForOverlappingTimes() {
        book(seat, student1, "2031-03-01T09:00:00Z", "2031-03-01T11:00:00Z");

        assertThatThrownBy(() -> book(seat, student2, "2031-03-01T10:00:00Z", "2031-03-01T12:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_seat_bookings_no_seat_overlap");
    }

    @Test
    void aBookingInsideAnotherIsAnOverlap() {
        book(seat, student1, "2031-03-02T09:00:00Z", "2031-03-02T13:00:00Z");

        assertThatThrownBy(() -> book(seat, student2, "2031-03-02T10:00:00Z", "2031-03-02T11:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_seat_bookings_no_seat_overlap");
    }

    @Test
    void backToBackBookingsDoNotOverlap() {
        book(seat, student1, "2031-03-03T09:00:00Z", "2031-03-03T10:00:00Z");

        assertThatCode(() -> book(seat, student2, "2031-03-03T10:00:00Z", "2031-03-03T11:00:00Z"))
                .doesNotThrowAnyException();
    }

    @Test
    void sameTimeOnADifferentSeatIsAllowed() {
        book(seat, student1, "2031-03-04T09:00:00Z", "2031-03-04T11:00:00Z");

        assertThatCode(() -> book(otherSeat, student2, "2031-03-04T09:00:00Z", "2031-03-04T11:00:00Z"))
                .doesNotThrowAnyException();
    }

    @Test
    void aCancelledSlotCanBeBookedAgain() {
        book(seat, student1, "2031-03-05T09:00:00Z", "2031-03-05T11:00:00Z", "CANCELLED");

        assertThatCode(() -> book(seat, student2, "2031-03-05T09:00:00Z", "2031-03-05T11:00:00Z"))
                .doesNotThrowAnyException();
    }

    @Test
    void cancellingABookingFreesTheSeat() {
        book(seat, student1, "2031-03-06T09:00:00Z", "2031-03-06T11:00:00Z");
        jdbc.update("UPDATE seat_bookings SET status = 'CANCELLED' WHERE seat_id = ?", seat);

        assertThatCode(() -> book(seat, student2, "2031-03-06T09:00:00Z", "2031-03-06T11:00:00Z"))
                .doesNotThrowAnyException();
    }

    @Test
    void aStudentCannotBookTwoSeatsAtTheSameTime() {
        book(seat, student1, "2031-03-07T09:00:00Z", "2031-03-07T11:00:00Z");

        assertThatThrownBy(() -> book(otherSeat, student1, "2031-03-07T10:00:00Z", "2031-03-07T12:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_seat_bookings_no_user_overlap");
    }

    @Test
    void endMustBeAfterStart() {
        assertThatThrownBy(() -> book(seat, student1, "2031-03-08T11:00:00Z", "2031-03-08T09:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_seat_bookings_ends_after_start");
    }

    @Test
    void aBookingCannotLastLongerThanEightHours() {
        assertThatThrownBy(() -> book(seat, student1, "2031-03-09T08:00:00Z", "2031-03-09T16:00:01Z"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_seat_bookings_max_duration");
    }

    @Test
    void anEightHourBookingIsAllowed() {
        assertThatCode(() -> book(seat, student1, "2031-03-10T08:00:00Z", "2031-03-10T16:00:00Z"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownSeatStatusAndBookingStatus() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO library_seats (label, zone, status) VALUES ('Z-bad', 'Quiet', 'BROKEN')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_library_seats_status");
        assertThatThrownBy(() -> book(seat, student1, "2031-03-11T09:00:00Z", "2031-03-11T10:00:00Z", "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_seat_bookings_status");
    }

    @Test
    void seatLabelIsUniqueIgnoringCase() {
        jdbc.update("INSERT INTO library_seats (label, zone) VALUES ('Dup-Label', 'Quiet')");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO library_seats (label, zone) VALUES ('dup-label', 'Quiet')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_library_seats_label");
    }

    /**
     * The real proof: many students try to book the same seat at the same instant from separate
     * database connections. Exactly one succeeds, because PostgreSQL serialises the conflicting inserts.
     */
    @Test
    void whenManyStudentsRaceForOneSeatExactlyOneWins() throws Exception {
        int contenders = 10;
        List<Long> students = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            students.add(insertStudent("race-" + SEQUENCE.incrementAndGet() + "@example.com"));
        }

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (long student : students) {
            results.add(pool.submit(() -> {
                startTogether.await();
                try {
                    book(seat, student, "2031-04-01T09:00:00Z", "2031-04-01T11:00:00Z");
                    return true;
                } catch (DataIntegrityViolationException e) {
                    return false;
                }
            }));
        }
        startTogether.countDown();

        int winners = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                winners++;
            }
        }
        pool.shutdown();

        assertThat(winners).isEqualTo(1);
        Integer stored = jdbc.queryForObject(
                "SELECT count(*) FROM seat_bookings WHERE seat_id = ? AND status = 'BOOKED'", Integer.class, seat);
        assertThat(stored).isEqualTo(1);
    }
}
