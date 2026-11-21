package com.chapelbook.book;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.category.Category;
import com.chapelbook.category.CategoryRepository;
import com.chapelbook.reservation.ReservationResponse;
import com.chapelbook.reservation.ReservationService;
import com.chapelbook.reservation.ReservationStatus;
import com.chapelbook.security.AuthenticatedUser;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
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
class CopyIntegrationTest {

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
    @Autowired
    private ReservationService reservationService;

    private Book book;
    private String librarian;
    private String tag;

    @BeforeEach
    void setUp() {
        tag = "cp" + SEQ.incrementAndGet() + "-" + System.nanoTime();
        Category category = categories.saveAndFlush(new Category("Copy cat " + tag, null));
        book = books.saveAndFlush(new Book("Copy book " + tag, "Author", category));
        librarian = auth.bearerFor(Role.LIBRARIAN);
    }

    private ResultActions addCopy(String token, String barcode) throws Exception {
        return mvc.perform(post("/api/v1/books/" + book.getId() + "/copies").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"" + barcode + "\",\"shelfLocation\":\"Shelf A\"}"));
    }

    private ResultActions patchCopy(String token, long id, String body) throws Exception {
        return mvc.perform(patch("/api/v1/copies/" + id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long idOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void librarianAddsACopyWhichStartsAvailable() throws Exception {
        addCopy(librarian, "B-" + tag)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.bookId").value(book.getId()))
                .andExpect(jsonPath("$.shelfLocation").value("Shelf A"));

        mvc.perform(get("/api/v1/books/" + book.getId()).header("Authorization", auth.bearerFor(Role.STUDENT)))
                .andExpect(jsonPath("$.totalCopies").value(1))
                .andExpect(jsonPath("$.availableCopies").value(1));
    }

    @Test
    void studentsCannotSeeOrManageCopies() throws Exception {
        String student = auth.bearerFor(Role.STUDENT);
        addCopy(student, "S-" + tag).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/books/" + book.getId() + "/copies").header("Authorization", student))
                .andExpect(status().isForbidden());
        patchCopy(student, 1, "{\"status\":\"LOST\"}").andExpect(status().isForbidden());
    }

    @Test
    void listShowsEveryCopyOfTheTitleForStaff() throws Exception {
        addCopy(librarian, "L1-" + tag);
        addCopy(librarian, "L2-" + tag);

        mvc.perform(get("/api/v1/books/" + book.getId() + "/copies").header("Authorization", librarian))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].barcode").value("L1-" + tag));
    }

    @Test
    void duplicateBarcodeIsA409AndBlankIsA400() throws Exception {
        addCopy(librarian, "DUP-" + tag).andExpect(status().isCreated());

        addCopy(librarian, "DUP-" + tag).andExpect(status().isConflict());
        addCopy(librarian, "  ").andExpect(status().isBadRequest());
    }

    @Test
    void unknownBookOrCopyIsA404() throws Exception {
        mvc.perform(post("/api/v1/books/999999/copies").header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"barcode\":\"X\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/books/999999/copies").header("Authorization", librarian))
                .andExpect(status().isNotFound());
        patchCopy(librarian, 999_999L, "{\"status\":\"LOST\"}").andExpect(status().isNotFound());
    }

    @Test
    void addingACopyServesTheStudentWaitingInTheQueue() throws Exception {
        User waiting = auth.createUser(Role.STUDENT);
        ReservationResponse reservation = reservationService.reserve(waiting.getId(), book.getId());
        org.assertj.core.api.Assertions.assertThat(reservation.status()).isEqualTo(ReservationStatus.PENDING);

        addCopy(librarian, "Q-" + tag)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"));

        var served = reservationService.get(reservation.id(),
                new AuthenticatedUser(waiting.getId(), waiting.getEmail(), waiting.getRole()));
        org.assertj.core.api.Assertions.assertThat(served.status()).isEqualTo(ReservationStatus.READY);
        org.assertj.core.api.Assertions.assertThat(served.copyBarcode()).isEqualTo("Q-" + tag);
    }

    @Test
    void withdrawingAndRestoringACopy() throws Exception {
        long id = idOf(addCopy(librarian, "W-" + tag));

        patchCopy(librarian, id, "{\"status\":\"WITHDRAWN\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mvc.perform(get("/api/v1/books/" + book.getId()).header("Authorization", librarian))
                .andExpect(jsonPath("$.totalCopies").value(0))
                .andExpect(jsonPath("$.availableCopies").value(0));

        patchCopy(librarian, id, "{\"status\":\"AVAILABLE\"}").andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void restoringACopyServesTheQueueToo() throws Exception {
        long id = idOf(addCopy(librarian, "R-" + tag));
        patchCopy(librarian, id, "{\"status\":\"LOST\"}").andExpect(status().isOk());
        User waiting = auth.createUser(Role.STUDENT);
        reservationService.reserve(waiting.getId(), book.getId());

        patchCopy(librarian, id, "{\"status\":\"AVAILABLE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"));
    }

    @Test
    void aCopyCannotBeSetToOnLoanOrHeldByHand() throws Exception {
        long id = idOf(addCopy(librarian, "H-" + tag));

        patchCopy(librarian, id, "{\"status\":\"ON_LOAN\"}").andExpect(status().isUnprocessableEntity());
        patchCopy(librarian, id, "{\"status\":\"HELD\"}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aCopyThatIsOnLoanKeepsItsStatusButItsShelfCanBeUpdated() throws Exception {
        long id = idOf(addCopy(librarian, "O-" + tag));
        BookCopy copy = copies.findById(id).orElseThrow();
        copy.setStatus(CopyStatus.ON_LOAN);
        copies.saveAndFlush(copy);

        patchCopy(librarian, id, "{\"status\":\"WITHDRAWN\"}").andExpect(status().isUnprocessableEntity());
        patchCopy(librarian, id, "{\"shelfLocation\":\"Shelf Z\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.shelfLocation").value("Shelf Z"))
                .andExpect(jsonPath("$.status").value("ON_LOAN"));
    }

    @Test
    void aHeldCopyCannotBeWithdrawnFromUnderTheStudent() throws Exception {
        User waiting = auth.createUser(Role.STUDENT);
        reservationService.reserve(waiting.getId(), book.getId());
        long id = idOf(addCopy(librarian, "HD-" + tag));

        patchCopy(librarian, id, "{\"status\":\"WITHDRAWN\"}").andExpect(status().isUnprocessableEntity());
    }
}
