package com.chapelbook.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings from {@code app.jwt.*}. There is deliberately no default secret: set the
 * {@code JWT_SECRET} environment variable (at least 32 bytes, e.g. {@code openssl rand -base64 48}).
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration) {

    public JwtProperties {
        if (expiration == null) {
            expiration = Duration.ofHours(1);
        }
    }
}
