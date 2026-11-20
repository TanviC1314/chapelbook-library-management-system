package com.chapelbook.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.security.JwtService;
import com.chapelbook.support.PostgresTestContainerConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Who may call what: the role rules on the user endpoints, checked for every role and for no login. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
@Transactional
class RoleAccessIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwt;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JdbcTemplate jdbc;
    @PersistenceContext
    private EntityManager entityManager;

    private String tokenFor(Role role) {
        User user = users.saveAndFlush(new User("rbac-" + role + "-" + SEQ.incrementAndGet() + "@example.com",
                encoder.encode("password-123"), "RBAC " + role, role, null));
        return "Bearer " + jwt.generateToken(user.getId(), user.getEmail(), user.getRole());
    }

    private ResultActions createStaff(String authorization, String email, String role) throws Exception {
        var request = post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"password-123","fullName":"New Staff","role":"%s"}
                """.formatted(email, role));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mvc.perform(request);
    }

    // ------------------------------------------------------------ creating users: admin only

    @Test
    void adminCanCreateALibrarianWhoCanThenLogIn() throws Exception {
        createStaff(tokenFor(Role.ADMIN), "new.librarian@example.com", "LIBRARIAN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("LIBRARIAN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.librarian@example.com\",\"password\":\"password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("LIBRARIAN"));
    }

    @Test
    void librarianCannotCreateUsers() throws Exception {
        createStaff(tokenFor(Role.LIBRARIAN), "x1@example.com", "LIBRARIAN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        assertThat(users.existsByEmailIgnoreCase("x1@example.com")).isFalse();
    }

    @Test
    void studentCannotCreateUsers() throws Exception {
        createStaff(tokenFor(Role.STUDENT), "x2@example.com", "ADMIN").andExpect(status().isForbidden());
        assertThat(users.existsByEmailIgnoreCase("x2@example.com")).isFalse();
    }

    @Test
    void anonymousCannotCreateUsers() throws Exception {
        createStaff(null, "x3@example.com", "ADMIN").andExpect(status().isUnauthorized());
    }

    @Test
    void creatingAUserValidatesTheRequest() throws Exception {
        createStaff(tokenFor(Role.ADMIN), "bad-email", "LIBRARIAN").andExpect(status().isBadRequest());
        createStaff(tokenFor(Role.ADMIN), "ok@example.com", "SUPERUSER").andExpect(status().isBadRequest());
    }

    @Test
    void creatingADuplicateEmailIsA409() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        createStaff(admin, "twice@example.com", "LIBRARIAN").andExpect(status().isCreated());

        createStaff(admin, "TWICE@example.com", "LIBRARIAN").andExpect(status().isConflict());
    }

    @Test
    void aStudentNumberOnAStaffAccountIsRefused() throws Exception {
        mvc.perform(post("/api/v1/users").header("Authorization", tokenFor(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"staff-num@example.com\",\"password\":\"password-123\","
                                + "\"fullName\":\"Staff\",\"role\":\"LIBRARIAN\",\"studentNumber\":\"S-9\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------ listing users: staff only

    @Test
    void adminAndLibrarianCanListUsersAndFilterByRole() throws Exception {
        tokenFor(Role.STUDENT);
        for (Role role : new Role[] {Role.ADMIN, Role.LIBRARIAN}) {
            mvc.perform(get("/api/v1/users").param("role", "STUDENT").header("Authorization", tokenFor(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].role").value("STUDENT"))
                    .andExpect(jsonPath("$.totalElements").isNumber())
                    .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        }
    }

    @Test
    void studentCannotListUsers() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", tokenFor(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotListUsers() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ own profile: everyone signed in

    @Test
    void everyRoleCanReadItsOwnProfile() throws Exception {
        for (Role role : Role.values()) {
            mvc.perform(get("/api/v1/users/me").header("Authorization", tokenFor(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role.name()));
        }
    }

    @Test
    void aStudentWhoIsPromotedKeepsTheirOldTokenButGetsTheNewRoleImmediately() throws Exception {
        User student = users.saveAndFlush(new User("promote@example.com", encoder.encode("password-123"),
                "Promote Me", Role.STUDENT, null));
        String oldToken = "Bearer " + jwt.generateToken(student.getId(), student.getEmail(), Role.STUDENT);
        mvc.perform(get("/api/v1/users").header("Authorization", oldToken)).andExpect(status().isForbidden());

        // The role in the database changes, the token is the same. Access follows the database.
        jdbc.update("UPDATE users SET role = 'LIBRARIAN' WHERE id = ?", student.getId());
        entityManager.clear(); // forget the cached entity so the filter re-reads the row

        mvc.perform(get("/api/v1/users").header("Authorization", oldToken)).andExpect(status().isOk());
    }
}
