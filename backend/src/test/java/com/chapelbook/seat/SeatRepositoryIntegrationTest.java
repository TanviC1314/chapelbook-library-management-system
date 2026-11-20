package com.chapelbook.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainerConfig.class)
class SeatRepositoryIntegrationTest {

    private static final Instant T9 = Instant.parse("2032-05-01T09:00:00Z");
    private static final Instant T10 = Instant.parse("2032-05-01T10:00:00Z");
    private static final Instant T11 = Instant.parse("2032-05-01T11:00:00Z");
    private static final Instant T12 = Instant.parse("2032-05-01T12:00:00Z");

    @Autowired
    private LibrarySeatRepository seats;
    @Autowired
    private SeatBookingRepository bookings;
    @Autowired
    private UserRepository users;

    private LibrarySeat seatA;
    private LibrarySeat seatB;
    private User student;
    private User other;

    @BeforeEach
    void setUp() {
        seatA = seats.saveAndFlush(new LibrarySeat("SR-A1", "Quiet", true));
        seatB = seats.saveAndFlush(new LibrarySeat("SR-B1", "Group", false));
        student = users.saveAndFlush(new User("seat-r1@example.com", "h", "S1", Role.STUDENT, null));
        other = users.saveAndFlush(new User("seat-r2@example.com", "h", "S2", Role.STUDENT, null));
    }

    @Test
    void savesSeatsAndListsOnlyActiveOnesInOrder() {
        LibrarySeat broken = seats.saveAndFlush(new LibrarySeat("SR-C1", "Quiet", false));
        broken.setStatus(SeatStatus.OUT_OF_SERVICE);
        seats.saveAndFlush(broken);

        var active = seats.findByStatusOrderByZoneAscLabelAsc(SeatStatus.ACTIVE);

        assertThat(active).extracting(LibrarySeat::getLabel).contains("SR-B1", "SR-A1").doesNotContain("SR-C1");
        assertThat(seats.existsByLabelIgnoreCase("sr-a1")).isTrue();
    }

    @Test
    void findsBookedSeatsForATimeWindow() {
        bookings.saveAndFlush(new SeatBooking(seatA, student, T9, T11));

        assertThat(bookings.findBookedSeatIds(T10, T12)).containsExactly(seatA.getId());
        // half-open: a window starting exactly when the booking ends is free
        assertThat(bookings.findBookedSeatIds(T11, T12)).isEmpty();
    }

    @Test
    void cancelledBookingsDoNotCountAsBooked() {
        SeatBooking booking = bookings.saveAndFlush(new SeatBooking(seatA, student, T9, T11));
        booking.cancel();
        bookings.saveAndFlush(booking);

        assertThat(bookings.findBookedSeatIds(T9, T11)).isEmpty();
        assertThat(bookings.findActiveOverlapping(T9, T11, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void listsAStudentsBookingsNewestFirst() {
        bookings.saveAndFlush(new SeatBooking(seatA, student, T9, T10));
        bookings.saveAndFlush(new SeatBooking(seatB, student, T11, T12));

        var page = bookings.findByUserIdOrderByStartsAtDesc(student.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(SeatBooking::getStartsAt).containsExactly(T11, T9);
    }

    @Test
    void theDatabaseRejectsAnOverlapEvenIfTheApplicationDidNotCheck() {
        bookings.saveAndFlush(new SeatBooking(seatA, student, T9, T11));

        assertThatThrownBy(() -> bookings.saveAndFlush(new SeatBooking(seatA, other, T10, T12)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
