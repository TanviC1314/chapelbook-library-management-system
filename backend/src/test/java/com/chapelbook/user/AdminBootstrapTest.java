package com.chapelbook.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapTest {

    private UserRepository users;
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode("long-enough-pw")).thenReturn("ENCODED");
    }

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(new BootstrapAdminProperties(email, password, null), users, encoder);
    }

    @Test
    void createsTheFirstAdminWhenConfiguredAndNoneExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);

        bootstrap("Admin@Example.com", "long-enough-pw").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("ENCODED");
        assertThat(saved.getValue().getFullName()).isEqualTo("Administrator");
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrap("admin@example.com", "long-enough-pw").run(null);

        verify(users, never()).save(any());
    }

    @Test
    void doesNothingWhenNotConfigured() {
        bootstrap("", "").run(null);
        bootstrap(null, null).run(null);

        verify(users, never()).save(any());
        verify(users, never()).existsByRole(any());
    }

    @Test
    void refusesAWeakBootstrapPassword() {
        when(users.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> bootstrap("admin@example.com", "short").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 8");
        verify(users, never()).save(any());
    }
}
