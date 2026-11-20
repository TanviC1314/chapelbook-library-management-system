package com.chapelbook.security;

import com.chapelbook.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Creates and verifies signed JWT access tokens (HMAC-SHA256). Tokens are stateless: everything
 * the API needs to identify the caller (id, email, role) is inside the signed token.
 */
@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration lifetime;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set to at least " + MIN_SECRET_BYTES + " characters "
                            + "(generate one with: openssl rand -base64 48)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.lifetime = properties.expiration();
        this.clock = clock;
    }

    public String generateToken(long userId, String email, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies the signature and expiry. Returns empty for anything wrong (tampered, expired,
     * signed with another key, malformed, unknown role) so callers never see a half-trusted token.
     */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new TokenClaims(
                    Long.parseLong(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Duration getLifetime() {
        return lifetime;
    }

    /** What a valid token says about its owner. */
    public record TokenClaims(long userId, String email, Role role) {
    }
}
