package com.chapelbook.seat;

import static org.assertj.core.api.Assertions.assertThat;

import com.chapelbook.common.ConflictException;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Real threads and real commits. Several requests pass the service's "is it free?" check at the same moment,
 * so what stops the double booking here is the database exclusion constraint, and the service must report
 * the loser as a clean conflict (never a raw database error).
 */
@SpringBootTest
@Import({PostgresTestContainerConfig.class, TestAuth.class})
class SeatBookingConcurrencyIntegrationTest {

    @Autowired
    private SeatService service;
    @Autowired
    private LibrarySeatRepository seats;
    @Autowired
    private SeatBookingRepository bookings;
    @Autowired
    private TestAuth auth;

    private List<Object> race(List<Callable<SeatBookingResponse>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<SeatBookingResponse> task : tasks) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        go.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            outcomes.add(future.get());
        }
        pool.shutdown();
        return outcomes;
    }

    private static Instant nextHour() {
        return Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    @Test
    void tenStudentsRacingForOneSeatExactlyOneGetsItAndTheRestGetACleanConflict() throws Exception {
        LibrarySeat seat = seats.saveAndFlush(new LibrarySeat("RACE-" + System.nanoTime(), "Race zone", true));
        Instant from = nextHour();
        Instant to = from.plus(2, ChronoUnit.HOURS);
        List<Callable<SeatBookingResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User student = auth.createUser(Role.STUDENT);
            tasks.add(() -> service.book(student.getId(), seat.getId(), from, to));
        }

        List<Object> outcomes = race(tasks);

        assertThat(outcomes.stream().filter(o -> o instanceof SeatBookingResponse).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> o instanceof ConflictException).count()).isEqualTo(9);
        assertThat(bookings.findBookedSeatIds(from, to)).containsOnlyOnce(seat.getId());
    }

    @Test
    void oneStudentTryingToBookSeveralSeatsForTheSameTimeGetsOnlyOne() throws Exception {
        User student = auth.createUser(Role.STUDENT);
        Instant from = nextHour().plus(1, ChronoUnit.DAYS);
        Instant to = from.plus(2, ChronoUnit.HOURS);
        List<Callable<SeatBookingResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LibrarySeat seat = seats.saveAndFlush(new LibrarySeat("MULTI-" + System.nanoTime() + "-" + i, "Race zone", false));
            tasks.add(() -> service.book(student.getId(), seat.getId(), from, to));
        }

        List<Object> outcomes = race(tasks);

        assertThat(outcomes.stream().filter(o -> o instanceof SeatBookingResponse).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> o instanceof ConflictException).count()).isEqualTo(5);
    }

    @Test
    void differentSeatsAtTheSameTimeDoNotInterfere() throws Exception {
        Instant from = nextHour().plus(2, ChronoUnit.DAYS);
        Instant to = from.plus(1, ChronoUnit.HOURS);
        List<Callable<SeatBookingResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            User student = auth.createUser(Role.STUDENT);
            LibrarySeat seat = seats.saveAndFlush(new LibrarySeat("OWN-" + System.nanoTime() + "-" + i, "Race zone", false));
            tasks.add(() -> service.book(student.getId(), seat.getId(), from, to));
        }

        List<Object> outcomes = race(tasks);

        assertThat(outcomes).allMatch(o -> o instanceof SeatBookingResponse);
    }
}
