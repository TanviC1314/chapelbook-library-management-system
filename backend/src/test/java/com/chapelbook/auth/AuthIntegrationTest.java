package com.chapelbook.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions register(String email, String password, String name, String studentNumber) throws Exception {
        String number = studentNumber == null ? "null" : "\"" + studentNumber + "\"";
        return postJson("/api/v1/auth/register", """
                {"email":"%s","password":"%s","fullName":"%s","studentNumber":%s}
                """.formatted(email, password, name, number));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password));
    }

    private String tokenFrom(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    // ------------------------------------------------------------ register

    @Test
    void registeringCreatesAStudentAndReturnsAUsableToken() throws Exception {
        ResultActions result = register("New.Student@Example.com", "correct-horse", "New Student", "S-1001")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("new.student@example.com"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.studentNumber").value("S-1001"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist());

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenFrom(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new.student@example.com"));
    }

    @Test
    void thePasswordIsStoredAsABcryptHashNeverAsPlainText() throws Exception {
        register("hash-check@example.com", "correct-horse", "Hash Check", null).andExpect(status().isCreated());

        User stored = users.findByEmailIgnoreCase("hash-check@example.com").orElseThrow();

        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo("correct-horse");
        assertThat(encoder.matches("correct-horse", stored.getPasswordHash())).isTrue();
    }

    @Test
    void aCallerCannotChooseTheirOwnRole() throws Exception {
        postJson("/api/v1/auth/register", """
                {"email":"sneaky@example.com","password":"correct-horse","fullName":"Sneaky","role":"ADMIN"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("STUDENT"));
    }

    @Test
    void duplicateEmailIgnoringCaseIsA409() throws Exception {
        register("dupe@example.com", "correct-horse", "First", null).andExpect(status().isCreated());

        register("DUPE@example.com", "another-pass1", "Second", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void duplicateStudentNumberIsA409() throws Exception {
        register("a1@example.com", "correct-horse", "A", "S-2002").andExpect(status().isCreated());

        register("a2@example.com", "correct-horse", "B", "S-2002").andExpect(status().isConflict());
    }

    @Test
    void invalidInputIsA400ListingEachBadField() throws Exception {
        register("not-an-email", "short", " ", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    void aPasswordLongerThanBcryptCanHandleIsRejectedNotTruncated() throws Exception {
        register("long@example.com", "x".repeat(73), "Long", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ------------------------------------------------------------ login

    @Test
    void loginWithTheRightPasswordReturnsAToken() throws Exception {
        register("login-ok@example.com", "correct-horse", "Login Ok", null).andExpect(status().isCreated());

        ResultActions result = login("LOGIN-OK@example.com", "correct-horse")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokenFrom(result)))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        register("login-bad@example.com", "correct-horse", "Login Bad", null).andExpect(status().isCreated());

        String wrongPassword = login("login-bad@example.com", "wrong-password")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", "wrong-password")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void aDeactivatedUserCannotLogIn() throws Exception {
        register("login-off@example.com", "correct-horse", "Login Off", null).andExpect(status().isCreated());
        User user = users.findByEmailIgnoreCase("login-off@example.com").orElseThrow();
        user.setActive(false);
        users.saveAndFlush(user);

        login("login-off@example.com", "correct-horse").andExpect(status().isUnauthorized());
    }

    @Test
    void staffAccountsCanLogInToo() throws Exception {
        users.saveAndFlush(new User("librarian@example.com", encoder.encode("staff-password"), "Lib", Role.LIBRARIAN, null));

        login("librarian@example.com", "staff-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("LIBRARIAN"));
    }

    @Test
    void loginRequiresBothFields() throws Exception {
        postJson("/api/v1/auth/login", "{\"email\":\"\",\"password\":\"\"}").andExpect(status().isBadRequest());
    }

    @Test
    void profileEndpointRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
}
