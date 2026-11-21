package com.chapelbook.fine;

import static org.assertj.core.api.Assertions.assertThat;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.borrowing.BorrowRecord;
import com.chapelbook.borrowing.BorrowRecordRepository;
import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.common.ConflictException;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/** Two librarians click "paid" on the same fine at the same moment: exactly one of them can win. */
@SpringBootTest
@Import({PostgresTestContainerConfig.class, TestAuth.class})
class FinePaymentConcurrencyIntegrationTest {

    @Autowired
    private FineService fineService;
    @Autowired
    private FineRepository fines;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private BookRepository books;
    @Autowired
    private BookCopyRepository copies;
    @Autowired
    private BorrowRecordRepository loans;
    @Autowired
    private TestAuth auth;

    @Test
    void whenSeveralLibrariansPayTheSameFineAtOnceExactlyOnePaymentIsRecorded() throws Exception {
        Category category = categories.saveAndFlush(new Category("Fine race " + System.nanoTime(), null));
        Book book = books.saveAndFlush(new Book("Fine race book " + System.nanoTime(), "Author", category));
        BookCopy copy = copies.saveAndFlush(new BookCopy(book, "FR-" + System.nanoTime(), null));
        User student = auth.createUser(Role.STUDENT);
        User librarian = auth.createUser(Role.LIBRARIAN);
        Instant due = Instant.now().minus(10, ChronoUnit.DAYS);
        BorrowRecord loan = loans.saveAndFlush(new BorrowRecord(copy, student, librarian, null, due.minus(14, ChronoUnit.DAYS), due));
        loan.markReturned(Instant.now());
        loans.saveAndFlush(loan);
        Fine fine = fines.saveAndFlush(new Fine(loan, student, new BigDecimal("3.00")));

        int clicks = 6;
        ExecutorService pool = Executors.newFixedThreadPool(clicks);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < clicks; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    return fineService.pay(fine.getId());
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

        long winners = outcomes.stream().filter(o -> o instanceof FineResponse).count();
        long refused = outcomes.stream()
                .filter(o -> o instanceof ConflictException || o instanceof ObjectOptimisticLockingFailureException).count();
        assertThat(winners).isEqualTo(1);
        assertThat(refused).isEqualTo(clicks - 1); // every other click was refused cleanly, none crashed
        assertThat(fines.findById(fine.getId()).orElseThrow().getStatus()).isEqualTo(FineStatus.PAID);
    }
}
