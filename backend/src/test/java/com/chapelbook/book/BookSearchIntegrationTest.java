package com.chapelbook.book;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class BookSearchIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TestAuth auth;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private BookRepository books;
    @Autowired
    private BookCopyRepository copies;

    /** A word no other test uses, so results are not mixed with other tests' data. */
    private String tag;
    private Category science;
    private Category art;
    private String student;

    @BeforeEach
    void setUp() {
        tag = "zq" + SEQ.incrementAndGet() + "x";
        science = categories.saveAndFlush(new Category("Science " + tag, null));
        art = categories.saveAndFlush(new Category("Art " + tag, null));
        student = auth.bearerFor(Role.STUDENT);
    }

    private Book book(String title, String author, String isbn, Integer year, Category category) {
        Book book = new Book(title, author, category);
        book.setIsbn(isbn);
        book.setPublicationYear(year);
        return books.saveAndFlush(book);
    }

    private void copy(Book book, String barcode, CopyStatus status) {
        BookCopy copy = copies.saveAndFlush(new BookCopy(book, barcode, null));
        copy.setStatus(status);
        copies.saveAndFlush(copy);
    }

    private ResultActions search(String... params) throws Exception {
        var request = get("/api/v1/books").header("Authorization", student);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    @Test
    void findsByTitleIgnoringCaseAndPartialWords() throws Exception {
        book("Quantum " + tag + " Physics", "A. Author", null, 2000, science);
        book("Cooking basics " + tag, "B. Author", null, 2001, art);

        search("q", "QUANTUM").andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.title=='Quantum " + tag + " Physics')]").exists());
        search("q", "physi " + tag).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void findsByAuthorAndByIsbn() throws Exception {
        book("Book one " + tag, "Marie Curie", "9780132350884", 1990, science);

        search("q", "curie " + tag).andExpect(jsonPath("$.totalElements").value(1));
        search("q", "9780132350884").andExpect(jsonPath("$.content[0].title").value("Book one " + tag));
    }

    @Test
    void everyWordMustMatchSomewhere() throws Exception {
        book("Clean Code " + tag, "Robert Martin", null, 2008, science);
        book("Clean Architecture " + tag, "Robert Martin", null, 2017, science);

        search("q", "clean martin " + tag).andExpect(jsonPath("$.totalElements").value(2));
        search("q", "clean architecture " + tag).andExpect(jsonPath("$.totalElements").value(1));
        search("q", "clean nonexistentword " + tag).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void percentAndUnderscoreAreLiteralNotWildcards() throws Exception {
        book("100% Pure " + tag, "Author", null, 2000, science);
        book("Plain " + tag, "Author", null, 2000, science);

        // A real "%" wildcard would match everything; escaped, it matches only the title containing "%".
        search("q", "% " + tag).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("100% Pure " + tag));
        search("q", "_ " + tag).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByCategory() throws Exception {
        book("Atoms " + tag, "A", null, 2000, science);
        book("Paint " + tag, "A", null, 2000, art);

        search("q", tag, "categoryId", art.getId().toString())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Paint " + tag))
                .andExpect(jsonPath("$.content[0].category.name").value("Art " + tag));
    }

    @Test
    void availableOnlyHidesTitlesWithNothingOnTheShelf() throws Exception {
        Book onShelf = book("On shelf " + tag, "A", null, 2000, science);
        Book allLent = book("All lent " + tag, "A", null, 2000, science);
        book("No copies " + tag, "A", null, 2000, science);
        copy(onShelf, tag + "-1", CopyStatus.AVAILABLE);
        copy(allLent, tag + "-2", CopyStatus.ON_LOAN);

        search("q", tag).andExpect(jsonPath("$.totalElements").value(3));
        search("q", tag, "availableOnly", "true")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("On shelf " + tag));
    }

    @Test
    void eachResultShowsItsOwnAvailabilityFromOneGroupedQuery() throws Exception {
        Book first = book("Alpha " + tag, "A", null, 2000, science);
        Book second = book("Beta " + tag, "A", null, 2000, science);
        copy(first, tag + "-a1", CopyStatus.AVAILABLE);
        copy(first, tag + "-a2", CopyStatus.ON_LOAN);
        copy(second, tag + "-b1", CopyStatus.AVAILABLE);
        copy(second, tag + "-b2", CopyStatus.AVAILABLE);
        copy(second, tag + "-b3", CopyStatus.WITHDRAWN);

        search("q", tag)
                .andExpect(jsonPath("$.content[0].title").value("Alpha " + tag))
                .andExpect(jsonPath("$.content[0].totalCopies").value(2))
                .andExpect(jsonPath("$.content[0].availableCopies").value(1))
                .andExpect(jsonPath("$.content[1].totalCopies").value(2))
                .andExpect(jsonPath("$.content[1].availableCopies").value(2));
    }

    @Test
    void sortsByTitleAuthorYearAndNewest() throws Exception {
        book("B title " + tag, "Zed", null, 1999, science);
        book("A title " + tag, "Yan", null, 2015, science);
        book("C title " + tag, "Xi", null, 2005, science);

        search("q", tag, "sort", "TITLE").andExpect(jsonPath("$.content[*].title",
                contains("A title " + tag, "B title " + tag, "C title " + tag)));
        search("q", tag, "sort", "AUTHOR").andExpect(jsonPath("$.content[*].author", contains("Xi", "Yan", "Zed")));
        search("q", tag, "sort", "YEAR").andExpect(jsonPath("$.content[*].publicationYear", contains(2015, 2005, 1999)));
        search("q", tag, "sort", "NEWEST").andExpect(jsonPath("$.content[0].title").value("C title " + tag));
    }

    @Test
    void paginatesAndReportsTotals() throws Exception {
        for (int i = 1; i <= 5; i++) {
            book("Page book " + i + " " + tag, "A", null, 2000, science);
        }

        search("q", tag, "size", "2", "page", "0")
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(0));
        search("q", tag, "size", "2", "page", "2").andExpect(jsonPath("$.content", hasSize(1)));
        search("q", tag, "size", "2", "page", "9").andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void anAbsurdPageSizeIsClampedAndABadSortIsA400() throws Exception {
        search("q", tag, "size", "100000").andExpect(status().isOk()).andExpect(jsonPath("$.size", is(100)));
        search("sort", "PASSWORD").andExpect(status().isBadRequest());
    }

    @Test
    void noFilterReturnsEverythingPaged() throws Exception {
        book("Unfiltered " + tag, "A", null, 2000, science);

        search().andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.content[*].title", org.hamcrest.Matchers.hasItem("Unfiltered " + tag)));
    }

    @Test
    void anonymousIsRefusedAndAnyRoleCanSearch() throws Exception {
        mvc.perform(get("/api/v1/books")).andExpect(status().isUnauthorized());
        for (Role role : Role.values()) {
            mvc.perform(get("/api/v1/books").header("Authorization", auth.bearerFor(role)).param("q", tag))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void resultsContainEveryMatchingTitle() throws Exception {
        book("Order one " + tag, "A", null, 2000, science);
        book("Order two " + tag, "A", null, 2000, science);

        search("q", tag).andExpect(jsonPath("$.content[*].title",
                containsInAnyOrder("Order one " + tag, "Order two " + tag)));
    }
}
