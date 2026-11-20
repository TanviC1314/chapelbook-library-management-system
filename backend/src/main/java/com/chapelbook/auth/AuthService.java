package com.chapelbook.auth;

import com.chapelbook.common.ConflictException;
import com.chapelbook.security.JwtService;
import com.chapelbook.user.Role;
import com.chapelbook.user.User;
import com.chapelbook.user.UserRepository;
import com.chapelbook.user.UserResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_LOGIN = "Invalid email or password.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** A real BCrypt hash that matches nothing, used so unknown emails take as long as wrong passwords. */
    private final String decoyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.decoyHash = encoder.encode("decoy-password-never-used");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = User.normaliseEmail(request.email());
        String studentNumber = blankToNull(request.studentNumber());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        if (studentNumber != null && users.existsByStudentNumber(studentNumber)) {
            throw new ConflictException("This student number is already registered.");
        }
        User user = users.saveAndFlush(
                new User(email, encoder.encode(request.password()), request.fullName(), Role.STUDENT, studentNumber));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        // Always run one BCrypt comparison, and give one message for every failure, so the response
        // does not reveal whether an email is registered.
        boolean passwordMatches = encoder.matches(request.password(), user != null ? user.getPasswordHash() : decoyHash);
        if (user == null || !passwordMatches || !user.isActive()) {
            throw new BadCredentialsException(INVALID_LOGIN);
        }
        return tokenFor(user);
    }

    private AuthResponse tokenFor(User user) {
        String token = jwt.generateToken(user.getId(), user.getEmail(), user.getRole());
        return AuthResponse.bearer(token, jwt.getLifetime().toSeconds(), UserResponse.from(user));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
