package com.chapelbook.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainerConfig.class)
@Transactional
class SecurityChainIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwt;
    @Autowired
    private UserRepository users;

    private User saveUser(String email, boolean active) {
        User user = new User(email, "hash", "Test", Role.STUDENT, null);
        user.setActive(active);
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwt.generateToken(user.getId(), user.getEmail(), user.getRole());
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutATokenIsA401ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void garbageTokenIsA401() throws Exception {
        mvc.perform(get("/api/v1/anything").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenPassesTheSecurityChain() throws Exception {
        User user = saveUser("chain-ok@example.com", true);

        // The path does not exist, so the answer is 404 from MVC: security let the request through.
        mvc.perform(get("/api/v1/does-not-exist").header("Authorization", bearer(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void aDeactivatedUsersTokenStopsWorkingImmediately() throws Exception {
        User user = saveUser("chain-off@example.com", true);
        String token = bearer(user);
        user.setActive(false);
        users.saveAndFlush(user);

        mvc.perform(get("/api/v1/does-not-exist").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenForAUserThatNoLongerExistsIsA401() throws Exception {
        String token = "Bearer " + jwt.generateToken(987_654_321L, "ghost@example.com", Role.ADMIN);

        mvc.perform(get("/api/v1/does-not-exist").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }
}
