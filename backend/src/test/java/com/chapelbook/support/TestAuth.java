package com.chapelbook.support;

import com.chapelbook.security.JwtService;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test helper: creates a real user in the database and returns a real signed bearer token for it,
 * so tests exercise the genuine security chain instead of faking authentication.
 * Import it with {@code @Import(TestAuth.class)} and autowire it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestAuth {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwt;
    @Autowired
    private PasswordEncoder encoder;

    public User createUser(Role role) {
        int n = SEQUENCE.incrementAndGet();
        String studentNumber = role == Role.STUDENT ? "T-" + System.nanoTime() + "-" + n : null;
        return users.saveAndFlush(new User("test-" + role.name().toLowerCase() + "-" + System.nanoTime() + "-" + n
                + "@example.com", encoder.encode("password-123"), "Test " + role, role, studentNumber));
    }

    public String bearer(User user) {
        return "Bearer " + jwt.generateToken(user.getId(), user.getEmail(), user.getRole());
    }

    public String bearerFor(Role role) {
        return bearer(createUser(role));
    }
}
