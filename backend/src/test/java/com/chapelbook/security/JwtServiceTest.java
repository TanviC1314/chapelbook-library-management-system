package com.chapelbook.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chapelbook.security.JwtService.TokenClaims;
import com.chapelbook.user.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-comfortably-over-32-bytes";
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    private static JwtService service(String secret, Duration lifetime, Instant at) {
        return new JwtService(new JwtProperties(secret, lifetime), Clock.fixed(at, ZoneOffset.UTC));
    }

    @Test
    void roundTripsIdEmailAndRole() {
        JwtService jwt = service(SECRET, Duration.ofHours(1), NOW);

        String token = jwt.generateToken(42, "ann@example.com", Role.LIBRARIAN);

        assertThat(jwt.parse(token)).contains(new TokenClaims(42, "ann@example.com", Role.LIBRARIAN));
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = service(SECRET, Duration.ofHours(1), NOW).generateToken(1, "a@example.com", Role.STUDENT);

        JwtService later = service(SECRET, Duration.ofHours(1), NOW.plus(Duration.ofHours(1)).plusSeconds(1));

        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void acceptsATokenJustBeforeItExpires() {
        String token = service(SECRET, Duration.ofHours(1), NOW).generateToken(1, "a@example.com", Role.STUDENT);

        JwtService almost = service(SECRET, Duration.ofHours(1), NOW.plus(Duration.ofMinutes(59)));

        assertThat(almost.parse(token)).isPresent();
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String forged = service("a-completely-different-secret-of-32-plus-bytes", Duration.ofHours(1), NOW)
                .generateToken(1, "a@example.com", Role.ADMIN);

        assertThat(service(SECRET, Duration.ofHours(1), NOW).parse(forged)).isEmpty();
    }

    @Test
    void rejectsATamperedPayload() {
        JwtService jwt = service(SECRET, Duration.ofHours(1), NOW);
        String token = jwt.generateToken(1, "a@example.com", Role.STUDENT);
        String[] parts = token.split("\\.");
        // swap in the payload of an admin token but keep the student token's signature
        String adminPayload = jwt.generateToken(1, "a@example.com", Role.ADMIN).split("\\.")[1];
        String tampered = parts[0] + "." + adminPayload + "." + parts[2];

        assertThat(jwt.parse(tampered)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        JwtService jwt = service(SECRET, Duration.ofHours(1), NOW);

        assertThat(jwt.parse("not-a-jwt")).isEmpty();
        assertThat(jwt.parse("")).isEmpty();
        assertThat(jwt.parse("a.b.c")).isEmpty();
    }

    @Test
    void refusesToStartWithAMissingOrShortSecret() {
        assertThatThrownBy(() -> service(null, Duration.ofHours(1), NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> service("too-short", Duration.ofHours(1), NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
    }

    @Test
    void usesAOneHourLifetimeWhenNoneIsConfigured() {
        assertThat(service(SECRET, null, NOW).getLifetime()).isEqualTo(Duration.ofHours(1));
    }
}
