package com.chapelbook.book;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
class BookIntegrationTest {

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
    private String librarian;

    @BeforeEach
    void setUp() {
        category = categories.saveAndFlush(new Category("Book-test " + SEQ.incrementAndGet(), null));
        librarian = auth.bearerFor(Role.LIBRARIAN);
    }

    private String body(String isbn, String title, Object year, Long categoryId) throws Exception {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("isbn", isbn);
        m.put("title", title);
        m.put("author", "Some Author");
        m.put("publisher", "Some Press");
        m.put("publicationYear", year);
        m.put("description", "A description");
        m.put("categoryId", categoryId);
        return json.writeValueAsString(m);
    }

    private ResultActions create(String token, String isbn, String title) throws Exception {
        return mvc.perform(post("/api/v1/books").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body(isbn, title, 2020, category.getId())));
    }

    private long idOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void librarianCreatesABookWithItsCategoryAndZeroAvailability() throws Exception {
        create(librarian, "978-0-13-235088-4", "Clean Code")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.isbn").value("9780132350884"))
                .andExpect(jsonPath("$.category.name").value(category.getName()))
                .andExpect(jsonPath("$.totalCopies").value(0))
                .andExpect(jsonPath("$.availableCopies").value(0))
                .andExpect(jsonPath("$.pendingReservations").value(0));
    }

    @Test
    void aBookWithoutAnIsbnIsAllowed() throws Exception {
        create(librarian, null, "Old pamphlet").andExpect(status().isCreated()).andExpect(jsonPath("$.isbn").doesNotExist());
    }

    @Test
    void studentCannotWriteBooksButCanRead() throws Exception {
        String student = auth.bearerFor(Role.STUDENT);
        create(student, null, "Nope").andExpect(status().isForbidden());

        long id = idOf(create(librarian, null, "Readable"));
        mvc.perform(get("/api/v1/books/" + id).header("Authorization", student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Readable"));
    }

    @Test
    void sameIsbnIsA409EvenWhenFormattedDifferently() throws Exception {
        create(librarian, "9780132350884", "First").andExpect(status().isCreated());

        create(librarian, "978-0-13-235088-4", "Second").andExpect(status().isConflict());
    }

    @Test
    void invalidIsbnLengthIsRefused() throws Exception {
        // 11 digits once the hyphens are stripped: matches the allowed characters but is not an ISBN
        create(librarian, "12345-6789-01", "Bad isbn").andExpect(status().isUnprocessableEntity());
        create(librarian, "abc", "Worse isbn").andExpect(status().isBadRequest());
    }

    @Test
    void validationReportsEachBadField() throws Exception {
        mvc.perform(post("/api/v1/books").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, " ", 5, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.publicationYear").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists());
    }

    @Test
    void anUnknownCategoryIsRefused() throws Exception {
        mvc.perform(post("/api/v1/books").header("Authorization", librarian).contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "Orphan", 2020, 999_999L)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateChangesFieldsAndMovesCategory() throws Exception {
        long id = idOf(create(librarian, null, "Old title"));
        Category other = categories.saveAndFlush(new Category("Book-test other " + SEQ.incrementAndGet(), null));

        mvc.perform(put("/api/v1/books/" + id).header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON).content(body(null, "New title", 2021, other.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.publicationYear").value(2021))
                .andExpect(jsonPath("$.category.id").value(other.getId()));
    }

    @Test
    void updatingToAnotherBooksIsbnIsA409ButKeepingYourOwnIsFine() throws Exception {
        create(librarian, "9780132350884", "Taken").andExpect(status().isCreated());
        long id = idOf(create(librarian, "9780201633610", "Mine"));

        mvc.perform(put("/api/v1/books/" + id).header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("9780132350884", "Mine", 2020, category.getId())))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/books/" + id).header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("9780201633610", "Mine renamed", 2020, category.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void missingBookIsA404() throws Exception {
        mvc.perform(get("/api/v1/books/999999").header("Authorization", librarian)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/books/999999").header("Authorization", librarian)).andExpect(status().isNotFound());
    }

    @Test
    void detailShowsLiveAvailabilityCountingOnlyCopiesInCirculation() throws Exception {
        long id = idOf(create(librarian, null, "Counted"));
        Book book = books.findById(id).orElseThrow();
        copies.saveAndFlush(new BookCopy(book, "AV-1", null));
        copies.saveAndFlush(new BookCopy(book, "AV-2", null));
        BookCopy loaned = copies.saveAndFlush(new BookCopy(book, "AV-3", null));
        loaned.setStatus(CopyStatus.ON_LOAN);
        BookCopy withdrawn = copies.saveAndFlush(new BookCopy(book, "AV-4", null));
        withdrawn.setStatus(CopyStatus.WITHDRAWN);
        copies.flush();

        mvc.perform(get("/api/v1/books/" + id).header("Authorization", auth.bearerFor(Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopies").value(3))
                .andExpect(jsonPath("$.availableCopies").value(2));
    }

    @Test
    void aBookWithoutCopiesCanBeDeletedButOneWithCopiesCannot() throws Exception {
        long plain = idOf(create(librarian, null, "Deletable"));
        mvc.perform(delete("/api/v1/books/" + plain).header("Authorization", librarian))
                .andExpect(status().isNoContent());

        long withCopy = idOf(create(librarian, null, "Has history"));
        copies.saveAndFlush(new BookCopy(books.findById(withCopy).orElseThrow(), "DEL-1", null));
        mvc.perform(delete("/api/v1/books/" + withCopy).header("Authorization", librarian))
                .andExpect(status().isConflict());
    }

    @Test
    void anonymousIsRefused() throws Exception {
        mvc.perform(get("/api/v1/books/1")).andExpect(status().isUnauthorized());
    }
}
