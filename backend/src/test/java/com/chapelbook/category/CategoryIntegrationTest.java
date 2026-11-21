package com.chapelbook.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.book.Book;
import com.chapelbook.book.BookRepository;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import com.chapelbook.user.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class CategoryIntegrationTest {

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

    private ResultActions create(String token, String name) throws Exception {
        return mvc.perform(post("/api/v1/categories").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"About " + name + "\"}"));
    }

    private long createdId(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void librarianCreatesACategoryAndEveryoneCanSeeIt() throws Exception {
        create(auth.bearerFor(Role.LIBRARIAN), "Astronomy")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Astronomy"))
                .andExpect(jsonPath("$.id").isNumber());

        mvc.perform(get("/api/v1/categories").header("Authorization", auth.bearerFor(Role.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Astronomy')]").exists());
    }

    @Test
    void listIsSortedByName() throws Exception {
        String librarian = auth.bearerFor(Role.LIBRARIAN);
        create(librarian, "Zoology-sort");
        create(librarian, "Anatomy-sort");

        mvc.perform(get("/api/v1/categories").header("Authorization", librarian))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Anatomy-sort')]").exists())
                .andExpect(jsonPath("$[0].name").value(org.hamcrest.Matchers.lessThanOrEqualTo("Zoology-sort")));
    }

    @Test
    void studentCannotWriteCategories() throws Exception {
        String student = auth.bearerFor(Role.STUDENT);
        create(student, "Hacked").andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/categories/1").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/categories/1").header("Authorization", student)).andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRefused() throws Exception {
        mvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateNameIgnoringCaseIsA409() throws Exception {
        String librarian = auth.bearerFor(Role.LIBRARIAN);
        create(librarian, "Poetry").andExpect(status().isCreated());

        create(librarian, "poetry").andExpect(status().isConflict());
    }

    @Test
    void blankNameIsA400() throws Exception {
        create(auth.bearerFor(Role.LIBRARIAN), "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void updateRenamesAndKeepsTheSameNameWithoutConflict() throws Exception {
        String librarian = auth.bearerFor(Role.LIBRARIAN);
        long id = createdId(create(librarian, "Drama"));

        mvc.perform(put("/api/v1/categories/" + id).header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"drama\",\"description\":\"Plays\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("drama"))
                .andExpect(jsonPath("$.description").value("Plays"));
    }

    @Test
    void renamingToAnotherCategorysNameIsA409() throws Exception {
        String librarian = auth.bearerFor(Role.LIBRARIAN);
        create(librarian, "Cooking");
        long id = createdId(create(librarian, "Baking"));

        mvc.perform(put("/api/v1/categories/" + id).header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"COOKING\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updatingOrDeletingAMissingCategoryIsA404() throws Exception {
        String librarian = auth.bearerFor(Role.LIBRARIAN);
        mvc.perform(put("/api/v1/categories/999999").header("Authorization", librarian)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/categories/999999").header("Authorization", librarian))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnusedCategoryCanBeDeleted() throws Exception {
        String librarian = auth.bearerFor(Role.LIBRARIAN);
        long id = createdId(create(librarian, "Temporary"));

        mvc.perform(delete("/api/v1/categories/" + id).header("Authorization", librarian))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(categories.findById(id)).isEmpty();
    }

    @Test
    void aCategoryWithBooksCannotBeDeleted() throws Exception {
        String librarian = auth.bearerFor(Role.LIBRARIAN);
        long id = createdId(create(librarian, "In use"));
        books.saveAndFlush(new Book("Some book", "Author", categories.findById(id).orElseThrow()));

        mvc.perform(delete("/api/v1/categories/" + id).header("Authorization", librarian))
                .andExpect(status().isConflict());
    }
}
