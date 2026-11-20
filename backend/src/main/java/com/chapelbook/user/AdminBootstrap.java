package com.chapelbook.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin on startup when BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD are set
 * and no admin exists yet. Without it a fresh install would have nobody who can create librarians.
 * It never overwrites or resets an existing admin, and never logs the password.
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final BootstrapAdminProperties properties;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminBootstrap(BootstrapAdminProperties properties, UserRepository users, PasswordEncoder encoder) {
        this.properties = properties;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }
        if (users.existsByRole(Role.ADMIN)) {
            log.info("An admin already exists; bootstrap admin settings ignored.");
            return;
        }
        if (properties.adminPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        users.save(new User(properties.adminEmail(), encoder.encode(properties.adminPassword()),
                properties.adminName(), Role.ADMIN, null));
        log.info("Created the first admin account for {}.", User.normaliseEmail(properties.adminEmail()));
    }
}
