package com.chapelbook.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.seat.LibrarySeat;
import com.chapelbook.seat.LibrarySeatRepository;
import com.chapelbook.seat.SeatBooking;
import com.chapelbook.seat.SeatBookingRepository;
import com.chapelbook.seat.SeatStatus;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data sits in March 2020, a window no other test writes to, so the numbers are exact. Layout (UTC):
 * <pre>
 *   Mar 2  Q1 s1 09:00-11:00 (2h)     Q2 s2 10:30-12:00 (1.5h)
 *   Mar 3  Q1 s2 09:00-10:00 CANCELLED
 *   Mar 4  G1 s1 14:00-15:00 (1h)     Q1 s3 14:00-16:00 (2h)
 *   Mar 20 Q1 s1 (outside the window)
 * </pre>
 * Zone Q has two seats in service and one out of service; zone G has one seat.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class SeatStatisticsIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TestAuth auth;
    @Autowired
    private StatisticsService statistics;
    @Autowired
    private LibrarySeatRepository seats;
    @Autowired
    private SeatBookingRepository bookings;

    private String quiet;
    private String group;

    private static Instant at(int day, int hour, int minute) {
        return LocalDate.of(2020, 3, day).atTime(hour, minute).toInstant(ZoneOffset.UTC);
    }

    @BeforeEach
    void setUp() {
        String run = Long.toString(System.nanoTime());
        quiet = "Quiet " + run;
        group = "Group " + run;
        LibrarySeat q1 = seats.saveAndFlush(new LibrarySeat("Q1-" + run, quiet, true));
        LibrarySeat q2 = seats.saveAndFlush(new LibrarySeat("Q2-" + run, quiet, true));
        LibrarySeat q3 = seats.saveAndFlush(new LibrarySeat("Q3-" + run, quiet, false));
        q3.setStatus(SeatStatus.OUT_OF_SERVICE);
        seats.saveAndFlush(q3);
        LibrarySeat g1 = seats.saveAndFlush(new LibrarySeat("G1-" + run, group, false));
        User s1 = auth.createUser(Role.STUDENT);
        User s2 = auth.createUser(Role.STUDENT);
        User s3 = auth.createUser(Role.STUDENT);

        bookings.saveAndFlush(new SeatBooking(q1, s1, at(2, 9, 0), at(2, 11, 0)));
        bookings.saveAndFlush(new SeatBooking(q2, s2, at(2, 10, 30), at(2, 12, 0)));
        SeatBooking cancelled = bookings.saveAndFlush(new SeatBooking(q1, s2, at(3, 9, 0), at(3, 10, 0)));
        cancelled.cancel();
        bookings.saveAndFlush(cancelled);
        bookings.saveAndFlush(new SeatBooking(g1, s1, at(4, 14, 0), at(4, 15, 0)));
        bookings.saveAndFlush(new SeatBooking(q1, s3, at(4, 14, 0), at(4, 16, 0)));
        bookings.saveAndFlush(new SeatBooking(q1, s1, at(20, 9, 0), at(20, 10, 0)));
    }

    private SeatStatsResponse report() {
        return statistics.seats(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 3, 5));
    }

    @Test
    void totalsCountBookedBookingsCancelledOnesAndStudents() {
        var totals = report().totals();

        assertThat(totals.bookings()).isEqualTo(4);
        assertThat(totals.cancelled()).isEqualTo(1);
        assertThat(totals.bookedHours()).isEqualByComparingTo("6.50");
        assertThat(totals.distinctStudents()).isEqualTo(3);
        assertThat(totals.seatsInService()).isGreaterThanOrEqualTo(3); // our 3 active seats, plus any from other tests
    }

    @Test
    void theDailySeriesCoversEveryDayAndIgnoresCancelledBookings() {
        var daily = report().daily();

        assertThat(daily).hasSize(5);
        assertThat(daily).extracting(SeatStatsResponse.Day::bookings).containsExactly(0L, 2L, 0L, 2L, 0L);
        assertThat(daily.get(1).bookedHours()).isEqualByComparingTo("3.50");
        assertThat(daily.get(3).bookedHours()).isEqualByComparingTo("3.00");
        assertThat(daily.get(2).bookedHours()).isEqualByComparingTo("0.00");
    }

    @Test
    void usageIsBrokenDownPerZoneUsingSeatsInService() {
        List<SeatStatsResponse.Zone> zones = report().byZone();

        var q = zones.stream().filter(z -> z.zone().equals(quiet)).findFirst().orElseThrow();
        assertThat(q.seatsInService()).isEqualTo(2); // the out-of-service seat is not counted
        assertThat(q.bookings()).isEqualTo(3);
        assertThat(q.bookedHours()).isEqualByComparingTo("5.50");
        assertThat(q.averageHoursPerSeatPerDay()).isEqualByComparingTo("0.55"); // 5.5 / (2 seats * 5 days)

        var g = zones.stream().filter(z -> z.zone().equals(group)).findFirst().orElseThrow();
        assertThat(g.seatsInService()).isEqualTo(1);
        assertThat(g.bookings()).isEqualTo(1);
        assertThat(g.bookedHours()).isEqualByComparingTo("1.00");
        assertThat(g.averageHoursPerSeatPerDay()).isEqualByComparingTo("0.20");
    }

    @Test
    void zonesWithNoBookingsInTheRangeStillAppearWithZeros() {
        var report = statistics.seats(LocalDate.of(2019, 1, 1), LocalDate.of(2019, 1, 2));

        var q = report.byZone().stream().filter(z -> z.zone().equals(quiet)).findFirst().orElseThrow();
        assertThat(q.bookings()).isZero();
        assertThat(q.bookedHours()).isEqualByComparingTo("0");
        assertThat(q.averageHoursPerSeatPerDay()).isEqualByComparingTo("0");
    }

    @Test
    void peakHoursCountBookingsActiveInEachHourOfTheDay() {
        var hours = report().peakHours();

        assertThat(hours).hasSize(24);
        assertThat(hours).extracting(SeatStatsResponse.Hour::hour).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, 24).boxed().toList());
        // 09:00-11:00 -> hours 9,10; 10:30-12:00 -> 10,11; 14:00-15:00 -> 14; 14:00-16:00 -> 14,15
        long[] expected = new long[24];
        expected[9] = 1;
        expected[10] = 2;
        expected[11] = 1;
        expected[14] = 2;
        expected[15] = 1;
        assertThat(hours).extracting(SeatStatsResponse.Hour::bookings)
                .containsExactly(java.util.Arrays.stream(expected).boxed().toArray(Long[]::new));
    }

    @Test
    void aNarrowRangeOnlyCountsThatDay() {
        var report = statistics.seats(LocalDate.of(2020, 3, 4), LocalDate.of(2020, 3, 4));

        assertThat(report.totals().bookings()).isEqualTo(2);
        assertThat(report.totals().cancelled()).isZero();
        assertThat(report.daily()).hasSize(1);
    }

    @Test
    void theEndpointIsAdminOnlyAndValidatesTheRange() throws Exception {
        String admin = auth.bearerFor(Role.ADMIN);
        mvc.perform(get("/api/v1/statistics/seats").param("from", "2020-03-01").param("to", "2020-03-05")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.bookings").value(4))
                .andExpect(jsonPath("$.peakHours", hasSize(24)))
                .andExpect(jsonPath("$.daily", hasSize(5)));
        mvc.perform(get("/api/v1/statistics/seats").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.daily", hasSize(30)));
        mvc.perform(get("/api/v1/statistics/seats").param("from", "2020-03-05").param("to", "2020-03-01")
                        .header("Authorization", admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/statistics/seats").header("Authorization", auth.bearerFor(Role.LIBRARIAN)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/statistics/seats").header("Authorization", auth.bearerFor(Role.STUDENT)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/statistics/seats")).andExpect(status().isUnauthorized());
    }
}
