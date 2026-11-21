package com.chapelbook.reservation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookCopy;
import com.chapelbook.book.BookCopyRepository;
import com.chapelbook.book.BookRepository;
import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ReservationApiIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TestAuth auth;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private BookRepository books;
    @Autowired
    private BookCopyRepository copies;

    private Category category;
    private String student;
    private String otherStudent;
    private String librarian;

    @BeforeEach
    void setUp() {
        category = categories.saveAndFlush(new Category("Res API cat " + SEQ.incrementAndGet(), null));
        student = auth.bearerFor(Role.STUDENT);
        otherStudent = auth.bearerFor(Role.STUDENT);
        librarian = auth.bearerFor(Role.LIBRARIAN);
    }

    private Book book(int copyCount) {
        Book book = books.saveAndFlush(new Book("Res API book " + SEQ.incrementAndGet(), "Author", category));
        for (int i = 0; i < copyCount; i++) {
            copies.saveAndFlush(new BookCopy(book, "RA-" + SEQ.incrementAndGet() + "-" + System.nanoTime(), null));
        }
        return book;
    }

    private ResultActions reserve(String token, Object bookId) throws Exception {
        return mvc.perform(post("/api/v1/reservations").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":" + bookId + "}"));
    }

    private long idOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void studentReservesAvailableTitleAndIsHeldACopy() throws Exception {
        Book book = book(1);

        reserve(student, book.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.bookTitle").value(book.getTitle()))
                .andExpect(jsonPath("$.copyBarcode").isNotEmpty())
                .andExpect(jsonPath("$.readyUntil").isNotEmpty())
                .andExpect(jsonPath("$.queuePosition").doesNotExist());
    }

    @Test
    void studentReservesUnavailableTitleAndSeesQueuePosition() throws Exception {
        Book book = book(0);

        reserve(student, book.getId());
        reserve(otherStudent, book.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.queuePosition").value(2))
                .andExpect(jsonPath("$.copyBarcode").doesNotExist());
    }

    @Test
    void theBookDetailShowsTheQueueLength() throws Exception {
        Book book = book(0);
        reserve(student, book.getId());
        reserve(otherStudent, book.getId());

        mvc.perform(get("/api/v1/books/" + book.getId()).header("Authorization", student))
                .andExpect(jsonPath("$.pendingReservations").value(2));
    }

    @Test
    void onlyStudentsCanReserve() throws Exception {
        Book book = book(1);

        reserve(librarian, book.getId()).andExpect(status().isForbidden());
        reserve(auth.bearerFor(Role.ADMIN), book.getId()).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAndUnknownBooksAreRefused() throws Exception {
        reserve(student, "null").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.bookId").exists());
        reserve(student, 999_999).andExpect(status().isNotFound());
    }

    @Test
    void reservingTheSameBookTwiceIsA409() throws Exception {
        Book book = book(0);
        reserve(student, book.getId()).andExpect(status().isCreated());

        reserve(student, book.getId()).andExpect(status().isConflict());
    }

    @Test
    void mineListsOnlyMyReservationsNewestFirst() throws Exception {
        Book first = book(0);
        Book second = book(0);
        reserve(student, first.getId());
        reserve(student, second.getId());
        reserve(otherStudent, first.getId());

        mvc.perform(get("/api/v1/reservations/mine").header("Authorization", student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].bookId").value(second.getId()))
                .andExpect(jsonPath("$.content[1].bookId").value(first.getId()));
        mvc.perform(get("/api/v1/reservations/mine").header("Authorization", librarian)).andExpect(status().isForbidden());
    }

    @Test
    void staffListEveryoneAndFilterByStatus() throws Exception {
        Book available = book(1);
        Book none = book(0);
        reserve(student, available.getId());
        reserve(otherStudent, none.getId());

        mvc.perform(get("/api/v1/reservations").param("status", "PENDING").header("Authorization", librarian))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.bookId==" + none.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.bookId==" + available.getId() + ")]").doesNotExist());
        mvc.perform(get("/api/v1/reservations").header("Authorization", student)).andExpect(status().isForbidden());
    }

    @Test
    void aStudentSeesTheirOwnReservationButAnotherStudentGetsA404() throws Exception {
        long id = idOf(reserve(student, book(0).getId()));

        mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", student)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", otherStudent))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", librarian)).andExpect(status().isOk());
    }

    @Test
    void cancellingByTheOwnerThenAgainIsA409AndFreesTheSlot() throws Exception {
        Book book = book(0);
        long id = idOf(reserve(student, book.getId()));

        mvc.perform(delete("/api/v1/reservations/" + id).header("Authorization", student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete("/api/v1/reservations/" + id).header("Authorization", student))
                .andExpect(status().isConflict());
        reserve(student, book.getId()).andExpect(status().isCreated());
    }

    @Test
    void anotherStudentCannotCancelButStaffCan() throws Exception {
        long id = idOf(reserve(student, book(0).getId()));

        mvc.perform(delete("/api/v1/reservations/" + id).header("Authorization", otherStudent))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/reservations/" + id).header("Authorization", librarian))
                .andExpect(status().isOk());
    }

    @Test
    void cancellingAHeldReservationHandsTheCopyOnAndTheQueueMovesUp() throws Exception {
        Book book = book(1);
        long held = idOf(reserve(student, book.getId()));
        long waiting = idOf(reserve(otherStudent, book.getId()));

        mvc.perform(delete("/api/v1/reservations/" + held).header("Authorization", student)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/reservations/" + waiting).header("Authorization", otherStudent))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.copyBarcode").isNotEmpty());
    }
}
