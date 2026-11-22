package com.chapelbook.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The API description is public, complete and tells the truth about who may call what. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theDescriptionIsPublicAndDeclaresBearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ChapelBook API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0]", hasKey("bearerAuth")));
    }

    @Test
    void swaggerUiIsReachableWithoutSigningIn() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void everyAreaOfTheApiIsDocumented() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/login")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/users")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/categories")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/books")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/books/{bookId}/copies")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/reservations")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/loans")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/fines")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/seats")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/seat-bookings")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/statistics/overview")))
                .andExpect(jsonPath("$.tags[*].name", hasItem("Loans")))
                .andExpect(jsonPath("$.tags[*].name", hasItem("Seat bookings")));
    }

    @Test
    void loginNeedsNoTokenAndSaysSo() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security", empty()))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.description",
                        containsString("public, no token needed")));
    }

    @Test
    void staffOnlyOperationsShowTheirRoleRuleAndTheErrorsTheyCanReturn() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/loans'].post.description", containsString("LIBRARIAN")))
                .andExpect(jsonPath("$.paths['/api/v1/loans'].post.responses['401'].content['application/problem+json']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/loans'].post.responses['403'].content['application/problem+json']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas", hasKey("ProblemDetail")));
    }
}
