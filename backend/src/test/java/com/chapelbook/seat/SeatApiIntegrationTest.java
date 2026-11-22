package com.chapelbook.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class SeatApiIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TestAuth auth;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private LibrarySeatRepository seats;
    @Autowired
    private SeatBookingRepository bookings;

    private String tag;
    private LibrarySeat seatA;
    private LibrarySeat seatB;
    private User student;
    private User other;
    private String studentToken;
    private String otherToken;
    private String librarian;
    private Instant base;

    @BeforeEach
    void setUp() {
        tag = "t" + SEQ.incrementAndGet() + "-" + System.nanoTime();
        seatA = seats.saveAndFlush(new LibrarySeat("A-" + tag, "Zone " + tag, true));
        seatB = seats.saveAndFlush(new LibrarySeat("B-" + tag, "Zone " + tag, false));
        student = auth.createUser(Role.STUDENT);
        other = auth.createUser(Role.STUDENT);
        studentToken = auth.bearer(student);
        otherToken = auth.bearer(other);
        librarian = auth.bearerFor(Role.LIBRARIAN);
        // a whole hour two days from now, so the window is always in the future and inside the booking horizon
        base = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    private ResultActions book(String token, long seatId, Instant from, Instant to) throws Exception {
        return mvc.perform(post("/api/v1/seat-bookings").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatId\":" + seatId + ",\"startsAt\":\"" + from + "\",\"endsAt\":\"" + to + "\"}"));
    }

    private long idOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    // ------------------------------------------------------------ seats

    @Test
    void studentsSeeOnlySeatsInServiceAndStaffSeeAll() throws Exception {
        LibrarySeat broken = seats.saveAndFlush(new LibrarySeat("X-" + tag, "Zone " + tag, false));
        broken.setStatus(SeatStatus.OUT_OF_SERVICE);
        seats.saveAndFlush(broken);

        mvc.perform(get("/api/v1/seats").param("zone", "zone " + tag).header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].available").doesNotExist());
        mvc.perform(get("/api/v1/seats").param("zone", "zone " + tag).header("Authorization", librarian))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void asksAboutAWindowAndEachSeatSaysWhetherItIsFree() throws Exception {
        book(studentToken, seatA.getId(), base, base.plus(2, ChronoUnit.HOURS)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/seats").param("zone", "Zone " + tag)
                        .param("from", base.plus(1, ChronoUnit.HOURS).toString())
                        .param("to", base.plus(3, ChronoUnit.HOURS).toString())
                        .header("Authorization", otherToken))
                .andExpect(jsonPath("$[?(@.label=='A-" + tag + "')].available").value(false))
                .andExpect(jsonPath("$[?(@.label=='B-" + tag + "')].available").value(true));
        // a window that starts exactly when the booking ends is free
        mvc.perform(get("/api/v1/seats").param("zone", "Zone " + tag)
                        .param("from", base.plus(2, ChronoUnit.HOURS).toString())
                        .param("to", base.plus(3, ChronoUnit.HOURS).toString())
                        .header("Authorization", otherToken))
                .andExpect(jsonPath("$[?(@.label=='A-" + tag + "')].available").value(true));
    }

    @Test
    void aHalfGivenOrBackwardsWindowIsRefused() throws Exception {
        mvc.perform(get("/api/v1/seats").param("from", base.toString()).header("Authorization", studentToken))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/seats").param("from", base.toString()).param("to", base.minusSeconds(60).toString())
                        .header("Authorization", studentToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void staffCreateAndEditSeatsAndStudentsCannot() throws Exception {
        String body = "{\"label\":\"N-" + tag + "\",\"zone\":\"Quiet\",\"hasPower\":true}";
        mvc.perform(post("/api/v1/seats").header("Authorization", studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        ResultActions created = mvc.perform(post("/api/v1/seats").header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.hasPower").value(true));
        long id = idOf(created);

        mvc.perform(put("/api/v1/seats/" + id).header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"N-" + tag + "\",\"zone\":\"Group\",\"hasPower\":false,\"status\":\"OUT_OF_SERVICE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("Group"))
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
    }

    @Test
    void aSeatLabelMustBeUniqueIgnoringCaseAndAnEditCanKeepItsOwn() throws Exception {
        mvc.perform(post("/api/v1/seats").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + seatA.getLabel().toLowerCase() + "\",\"zone\":\"Quiet\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/seats/" + seatA.getId()).header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + seatA.getLabel() + "\",\"zone\":\"Renamed zone\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/seats").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\" \",\"zone\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aSeatWithUpcomingBookingsCannotBeTakenOutOfService() throws Exception {
        book(studentToken, seatA.getId(), base, base.plus(1, ChronoUnit.HOURS)).andExpect(status().isCreated());

        mvc.perform(put("/api/v1/seats/" + seatA.getId()).header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + seatA.getLabel() + "\",\"zone\":\"Z\",\"status\":\"OUT_OF_SERVICE\"}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------ booking

    @Test
    void aStudentBooksASeat() throws Exception {
        book(studentToken, seatA.getId(), base, base.plus(2, ChronoUnit.HOURS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.seatLabel").value("A-" + tag))
                .andExpect(jsonPath("$.userId").value(student.getId()));
    }

    @Test
    void backToBackBookingsOfTheSameSeatAreFine() throws Exception {
        book(studentToken, seatA.getId(), base, base.plus(1, ChronoUnit.HOURS)).andExpect(status().isCreated());

        book(otherToken, seatA.getId(), base.plus(1, ChronoUnit.HOURS), base.plus(2, ChronoUnit.HOURS))
                .andExpect(status().isCreated());
    }

    @Test
    void onlyStudentsBook() throws Exception {
        book(librarian, seatA.getId(), base, base.plus(1, ChronoUnit.HOURS)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/seat-bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookingRulesAreEnforcedWithClearErrors() throws Exception {
        // ends before it starts, too short, too long, in the past, too far ahead
        book(studentToken, seatA.getId(), base.plus(1, ChronoUnit.HOURS), base).andExpect(status().isUnprocessableEntity());
        book(studentToken, seatA.getId(), base, base.plus(5, ChronoUnit.MINUTES)).andExpect(status().isUnprocessableEntity());
        book(studentToken, seatA.getId(), base, base.plus(9, ChronoUnit.HOURS))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("at most 8 hours")));
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        book(studentToken, seatA.getId(), past, past.plus(1, ChronoUnit.HOURS))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("past")));
        Instant far = Instant.now().plus(30, ChronoUnit.DAYS);
        book(studentToken, seatA.getId(), far, far.plus(1, ChronoUnit.HOURS)).andExpect(status().isUnprocessableEntity());
        // an exactly 8 hour booking is allowed
        book(studentToken, seatA.getId(), base, base.plus(8, ChronoUnit.HOURS)).andExpect(status().isCreated());
    }

    @Test
    void anUnknownOrOutOfServiceSeatIsRefused() throws Exception {
        book(studentToken, 999_999L, base, base.plus(1, ChronoUnit.HOURS)).andExpect(status().isNotFound());
        seatB.setStatus(SeatStatus.OUT_OF_SERVICE);
        seats.saveAndFlush(seatB);

        book(studentToken, seatB.getId(), base, base.plus(1, ChronoUnit.HOURS)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aStudentCanHoldOnlyAFewUpcomingBookings() throws Exception {
        for (int i = 0; i < 3; i++) {
            book(studentToken, seatA.getId(), base.plus(i * 2L, ChronoUnit.HOURS), base.plus(i * 2L + 1, ChronoUnit.HOURS))
                    .andExpect(status().isCreated());
        }

        book(studentToken, seatA.getId(), base.plus(10, ChronoUnit.HOURS), base.plus(11, ChronoUnit.HOURS))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("at most 3")));
    }

    @Test
    void overlappingBookingsOfTheSameSeatOrBySameStudentAreA409() throws Exception {
        book(studentToken, seatA.getId(), base, base.plus(2, ChronoUnit.HOURS)).andExpect(status().isCreated());

        book(otherToken, seatA.getId(), base.plus(1, ChronoUnit.HOURS), base.plus(3, ChronoUnit.HOURS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("seat is already booked")));
        book(studentToken, seatB.getId(), base.plus(1, ChronoUnit.HOURS), base.plus(3, ChronoUnit.HOURS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("You already have")));
    }

    // ------------------------------------------------------------ cancelling and listing

    @Test
    void cancellingFreesTheSeatForSomeoneElse() throws Exception {
        long id = idOf(book(studentToken, seatA.getId(), base, base.plus(2, ChronoUnit.HOURS)));

        mvc.perform(delete("/api/v1/seat-bookings/" + id).header("Authorization", studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        book(otherToken, seatA.getId(), base, base.plus(2, ChronoUnit.HOURS)).andExpect(status().isCreated());
    }

    @Test
    void cancellingTwiceOrSomeoneElsesBookingIsRefusedButStaffCanCancel() throws Exception {
        long id = idOf(book(studentToken, seatA.getId(), base, base.plus(2, ChronoUnit.HOURS)));

        mvc.perform(delete("/api/v1/seat-bookings/" + id).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/seat-bookings/" + id).header("Authorization", librarian))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/seat-bookings/" + id).header("Authorization", studentToken))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/seat-bookings/999999").header("Authorization", librarian))
                .andExpect(status().isNotFound());
    }

    @Test
    void aBookingThatHasAlreadyEndedCannotBeCancelled() throws Exception {
        Instant ended = Instant.now().minus(3, ChronoUnit.HOURS);
        SeatBooking old = bookings.saveAndFlush(new SeatBooking(seatA, student, ended, ended.plus(1, ChronoUnit.HOURS)));

        mvc.perform(delete("/api/v1/seat-bookings/" + old.getId()).header("Authorization", studentToken))
                .andExpect(status().isUnprocessableEntity());
        assertThat(bookings.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(SeatBookingStatus.BOOKED);
    }

    @Test
    void myBookingsAreListedLatestFirstAndCanShowOnlyUpcomingOnes() throws Exception {
        Instant ended = Instant.now().minus(3, ChronoUnit.HOURS);
        bookings.saveAndFlush(new SeatBooking(seatA, student, ended, ended.plus(1, ChronoUnit.HOURS)));
        long soon = idOf(book(studentToken, seatA.getId(), base, base.plus(1, ChronoUnit.HOURS)));
        long later = idOf(book(studentToken, seatB.getId(), base.plus(5, ChronoUnit.HOURS), base.plus(6, ChronoUnit.HOURS)));
        book(otherToken, seatB.getId(), base, base.plus(1, ChronoUnit.HOURS));

        mvc.perform(get("/api/v1/seat-bookings/mine").header("Authorization", studentToken))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(later))
                .andExpect(jsonPath("$.content[1].id").value(soon));
        mvc.perform(get("/api/v1/seat-bookings/mine").param("upcomingOnly", "true").header("Authorization", studentToken))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/seat-bookings/mine").header("Authorization", librarian)).andExpect(status().isForbidden());
    }

    @Test
    void staffMonitorBookingsInAWindow() throws Exception {
        long inWindow = idOf(book(studentToken, seatA.getId(), base, base.plus(2, ChronoUnit.HOURS)));
        book(otherToken, seatB.getId(), base.plus(6, ChronoUnit.HOURS), base.plus(7, ChronoUnit.HOURS));

        mvc.perform(get("/api/v1/seat-bookings").param("from", base.toString())
                        .param("to", base.plus(3, ChronoUnit.HOURS).toString()).header("Authorization", librarian))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inWindow));
        mvc.perform(get("/api/v1/seat-bookings").header("Authorization", librarian)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/seat-bookings").header("Authorization", studentToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/seat-bookings").param("from", base.toString()).header("Authorization", librarian))
                .andExpect(status().isUnprocessableEntity());
    }
}
