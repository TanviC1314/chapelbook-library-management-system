package com.chapelbook.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional first-admin setup from {@code app.bootstrap.admin-email} and {@code admin-password}
 * (environment: BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD). Both are empty by default, so no
 * admin exists unless you ask for one.
 */
@ConfigurationProperties("app.bootstrap")
public record BootstrapAdminProperties(String adminEmail, String adminPassword, String adminName) {

    public BootstrapAdminProperties {
        if (adminName == null || adminName.isBlank()) {
            adminName = "Administrator";
        }
    }

    public boolean isConfigured() {
        return adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank();
    }
}
