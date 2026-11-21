package com.chapelbook.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.book.CopyStatus;
import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.common.ConflictException;
import com.chapelbook.security.AuthenticatedUser;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Real threads, real connections, real commits. These tests deliberately do NOT use a rolled-back test
 * transaction, because the point is to prove the locking works when requests genuinely overlap.
 */
@SpringBootTest
@Import({PostgresTestContainerConfig.class, TestAuth.class})
class ReservationConcurrencyIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private ReservationService service;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private BookRepository books;
    @Autowired
    private BookCopyRepository copies;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private TestAuth auth;

    private Category category;

    @BeforeEach
    void setUp() {
        category = categories.saveAndFlush(new Category("Concurrency cat " + SEQ.incrementAndGet() + "-" + System.nanoTime(), null));
    }

    private Book bookWithCopies(int copyCount) {
        Book book = books.saveAndFlush(new Book("Race " + System.nanoTime(), "Author", category));
        for (int i = 0; i < copyCount; i++) {
            copies.saveAndFlush(new BookCopy(book, "RACE-" + System.nanoTime() + "-" + i, null));
        }
        return book;
    }

    private List<User> students(int count) {
        List<User> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(auth.createUser(Role.STUDENT));
        }
        return list;
    }

    private static AuthenticatedUser as(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }

    /** Runs every task at the same instant on its own thread and returns each outcome (or the exception thrown). */
    private <T> List<Object> raceAll(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
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

    private long count(List<Object> outcomes, Class<?> type) {
        return outcomes.stream().filter(type::isInstance).count();
    }

    @Test
    void aDoubleClickBySameStudentCreatesExactlyOneReservation() throws Exception {
        Book book = bookWithCopies(0);
        User student = students(1).get(0);
        List<Callable<ReservationResponse>> clicks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            clicks.add(() -> service.reserve(student.getId(), book.getId()));
        }

        List<Object> outcomes = raceAll(clicks);

        assertThat(count(outcomes, ReservationResponse.class)).isEqualTo(1);
        assertThat(count(outcomes, ConflictException.class)).isEqualTo(7);
        assertThat(reservations.countByBookIdAndStatus(book.getId(), ReservationStatus.PENDING)).isEqualTo(1);
    }

    @Test
    void sixStudentsRacingForTheLastCopyOneGetsItAndTheRestQueueInDistinctPlaces() throws Exception {
        Book book = bookWithCopies(1);
        List<Callable<ReservationResponse>> tasks = new ArrayList<>();
        for (User student : students(6)) {
            tasks.add(() -> service.reserve(student.getId(), book.getId()));
        }

        List<Object> outcomes = raceAll(tasks);

        assertThat(outcomes).allMatch(o -> o instanceof ReservationResponse); // nobody failed or deadlocked
        assertThat(reservations.countByBookIdAndStatus(book.getId(), ReservationStatus.READY)).isEqualTo(1);
        assertThat(reservations.countByBookIdAndStatus(book.getId(), ReservationStatus.PENDING)).isEqualTo(5);
        assertThat(copies.countByBookIdAndStatus(book.getId(), CopyStatus.HELD)).isEqualTo(1);
        assertThat(copies.countByBookIdAndStatus(book.getId(), CopyStatus.AVAILABLE)).isZero();
        List<Long> positions = reservations.findAll().stream()
                .filter(r -> r.getBook().getId().equals(book.getId()) && r.getStatus() == ReservationStatus.PENDING)
                .map(r -> reservations.countPendingAhead(book.getId(), r.getReservedAt(), r.getId()) + 1)
                .sorted().toList();
        assertThat(positions).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void manyReservationsAndCancellationsAtOnceNeverHandOutACopyTwiceOrLeaveOneIdle() throws Exception {
        int copyCount = 3;
        Book book = bookWithCopies(copyCount);
        List<User> people = students(12);

        // Phase 1: twelve students reserve at the same moment.
        List<Callable<ReservationResponse>> reserve = new ArrayList<>();
        for (User student : people) {
            reserve.add(() -> service.reserve(student.getId(), book.getId()));
        }
        List<Object> reserved = raceAll(reserve);
        assertThat(reserved).allMatch(o -> o instanceof ReservationResponse);

        // Phase 2: the three students currently holding a copy cancel at the same moment.
        List<ReservationResponse> holders = reserved.stream().map(o -> (ReservationResponse) o)
                .filter(r -> r.status() == ReservationStatus.READY).toList();
        assertThat(holders).hasSize(copyCount);
        List<Callable<ReservationResponse>> cancels = new ArrayList<>();
        for (ReservationResponse holder : holders) {
            User owner = people.stream().filter(p -> p.getId().equals(holder.userId())).findFirst().orElseThrow();
            cancels.add(() -> service.cancel(holder.id(), as(owner)));
        }
        List<Object> cancelled = raceAll(cancels);
        assertThat(cancelled).allMatch(o -> o instanceof ReservationResponse);

        // Invariants: still three holds, each on a different copy, no copy sitting idle while students wait.
        List<Reservation> ready = reservations.findAll().stream()
                .filter(r -> r.getBook().getId().equals(book.getId()) && r.getStatus() == ReservationStatus.READY).toList();
        assertThat(ready).hasSize(copyCount);
        assertThat(ready.stream().map(r -> r.getCopy().getId()).collect(Collectors.toSet())).hasSize(copyCount);
        assertThat(copies.countByBookIdAndStatus(book.getId(), CopyStatus.HELD)).isEqualTo(copyCount);
        assertThat(copies.countByBookIdAndStatus(book.getId(), CopyStatus.AVAILABLE)).isZero();
        assertThat(reservations.countByBookIdAndStatus(book.getId(), ReservationStatus.PENDING)).isEqualTo(12 - 3 - copyCount);
        assertThat(reservations.countByBookIdAndStatus(book.getId(), ReservationStatus.CANCELLED)).isEqualTo(copyCount);
    }

    @Test
    void differentTitlesDoNotBlockEachOther() throws Exception {
        List<Book> titles = List.of(bookWithCopies(1), bookWithCopies(1), bookWithCopies(1), bookWithCopies(1));
        List<User> people = students(4);
        List<Callable<ReservationResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Book title = titles.get(i);
            User person = people.get(i);
            tasks.add(() -> service.reserve(person.getId(), title.getId()));
        }

        List<Object> outcomes = raceAll(tasks);

        assertThat(outcomes).allMatch(o -> o instanceof ReservationResponse r && r.status() == ReservationStatus.READY);
    }
}
