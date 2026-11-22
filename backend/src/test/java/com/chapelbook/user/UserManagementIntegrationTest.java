package com.chapelbook.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.common.BusinessRuleException;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
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
@Import({PostgresTestContainerConfig.class, TestAuth.class})
@Transactional
class UserManagementIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TestAuth auth;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    private User admin;
    private User otherAdmin;
    private User librarian;
    private User student;
    private String adminToken;
    private String librarianToken;
    private String studentToken;
    private String tag;

    @BeforeEach
    void setUp() {
        tag = "um" + SEQ.incrementAndGet() + System.nanoTime();
        admin = auth.createUser(Role.ADMIN);
        otherAdmin = auth.createUser(Role.ADMIN);
        librarian = auth.createUser(Role.LIBRARIAN);
        student = users.saveAndFlush(new User("ann." + tag + "@example.com", encoder.encode("student-pass-1"),
                "Ann Marie " + tag, Role.STUDENT, "SN-" + tag));
        adminToken = auth.bearer(admin);
        librarianToken = auth.bearer(librarian);
        studentToken = auth.bearer(student);
    }

    private ResultActions patchUser(String token, long id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/users/" + id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ------------------------------------------------------------ search and get

    @Test
    void staffSearchByNameEmailOrStudentNumberAndEveryWordMustMatch() throws Exception {
        mvc.perform(get("/api/v1/users").param("q", "marie " + tag).header("Authorization", librarianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(student.getId()));
        mvc.perform(get("/api/v1/users").param("q", "ANN." + tag).header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/users").param("q", "sn-" + tag).header("Authorization", adminToken))
                .andExpect(jsonPath("$.content[0].studentNumber").value("SN-" + tag));
        mvc.perform(get("/api/v1/users").param("q", "marie nonexistentword " + tag).header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchTreatsPercentLiterally() throws Exception {
        mvc.perform(get("/api/v1/users").param("q", "%").param("role", "STUDENT").param("active", "true")
                        .header("Authorization", adminToken))
                .andExpect(jsonPath("$.content[?(@.id==" + student.getId() + ")]").doesNotExist());
    }

    @Test
    void filterByRoleAndActive() throws Exception {
        student.setActive(false);
        users.saveAndFlush(student);

        mvc.perform(get("/api/v1/users").param("q", tag).param("active", "false").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/users").param("q", tag).param("active", "true").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/users").param("q", tag).param("role", "LIBRARIAN").header("Authorization", adminToken))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void staffCanGetOneUserButStudentsCannotAndAMissingUserIs404() throws Exception {
        mvc.perform(get("/api/v1/users/" + student.getId()).header("Authorization", librarianToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(student.getEmail()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/users/" + student.getId()).header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/999999").header("Authorization", adminToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/me").header("Authorization", studentToken)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ editing

    @Test
    void adminRenamesAndDeactivatesAStudentWhoCanThenNoLongerUseTheirToken() throws Exception {
        patchUser(adminToken, student.getId(), "{\"fullName\":\"Renamed Student\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Renamed Student"));

        patchUser(adminToken, student.getId(), "{\"active\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/v1/users/me").header("Authorization", studentToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + student.getEmail() + "\",\"password\":\"student-pass-1\"}"))
                .andExpect(status().isUnauthorized());

        patchUser(adminToken, student.getId(), "{\"active\":true}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization", studentToken)).andExpect(status().isOk());
    }

    @Test
    void onlyAdminsMayEdit() throws Exception {
        patchUser(librarianToken, student.getId(), "{\"active\":false}").andExpect(status().isForbidden());
        patchUser(studentToken, student.getId(), "{\"fullName\":\"Hacked\"}").andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/users/" + student.getId()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(users.findById(student.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void validationAndUnknownUsers() throws Exception {
        patchUser(adminToken, student.getId(), "{\"fullName\":\"   \"}").andExpect(status().isBadRequest());
        patchUser(adminToken, student.getId(), "{\"role\":\"SUPERUSER\"}").andExpect(status().isBadRequest());
        patchUser(adminToken, 999_999L, "{\"active\":false}").andExpect(status().isNotFound());
    }

    @Test
    void studentAccountsStayStudentsAndStaffCannotBecomeStudents() throws Exception {
        patchUser(adminToken, student.getId(), "{\"role\":\"LIBRARIAN\"}").andExpect(status().isUnprocessableEntity());
        patchUser(adminToken, librarian.getId(), "{\"role\":\"STUDENT\"}").andExpect(status().isUnprocessableEntity());
        assertThat(users.findById(student.getId()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void aLibrarianCanBePromotedAndAnotherAdminDemoted() throws Exception {
        patchUser(adminToken, librarian.getId(), "{\"role\":\"ADMIN\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        patchUser(adminToken, otherAdmin.getId(), "{\"role\":\"LIBRARIAN\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("LIBRARIAN"));
    }

    @Test
    void anAdminCannotDeactivateOrDemoteThemselves() throws Exception {
        patchUser(adminToken, admin.getId(), "{\"active\":false}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("your own account")));
        patchUser(adminToken, admin.getId(), "{\"role\":\"LIBRARIAN\"}").andExpect(status().isUnprocessableEntity());
        // renaming yourself is fine
        patchUser(adminToken, admin.getId(), "{\"fullName\":\"Still Admin\"}").andExpect(status().isOk());
    }

    @Test
    void theLastActiveAdminCanNeverBeDemotedOrDeactivated() {
        // Leave otherAdmin as the only active admin in the database.
        users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN && !u.getId().equals(otherAdmin.getId()))
                .forEach(u -> {
                    u.setActive(false);
                    users.saveAndFlush(u);
                });

        // The caller here is a librarian id: the rule is about the target, not about who asks.
        assertThatThrownBy(() -> userService.update(librarian.getId(), otherAdmin.getId(),
                new UpdateUserRequest(null, Role.LIBRARIAN, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("last active admin");
        assertThatThrownBy(() -> userService.update(librarian.getId(), otherAdmin.getId(),
                new UpdateUserRequest(null, null, false)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("last active admin");
        assertThat(users.findById(otherAdmin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(users.findById(otherAdmin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Autowired
    private UserService userService;

    // ------------------------------------------------------------ password reset

    @Test
    void adminResetsAPasswordAndTheOldOneStopsWorking() throws Exception {
        mvc.perform(post("/api/v1/users/" + student.getId() + "/password").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"brand-new-pass-9\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + student.getEmail() + "\",\"password\":\"student-pass-1\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + student.getEmail() + "\",\"password\":\"brand-new-pass-9\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetIsAdminOnlyAndValidated() throws Exception {
        String body = "{\"newPassword\":\"brand-new-pass-9\"}";
        mvc.perform(post("/api/v1/users/" + student.getId() + "/password").header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/" + student.getId() + "/password").header("Authorization", studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/" + student.getId() + "/password").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users/999999/password").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
    }
}
