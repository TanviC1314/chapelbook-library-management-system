package com.chapelbook.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chapelbook.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainerConfig.class)
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository users;

    @Test
    void savesAUserAndSetsTimestamps() {
        User saved = users.saveAndFlush(new User("Ann@Example.com ", "hash", " Ann Student ", Role.STUDENT, "S1"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("ann@example.com");
        assertThat(saved.getFullName()).isEqualTo("Ann Student");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsByEmailIgnoringCase() {
        users.saveAndFlush(new User("lib@example.com", "hash", "Lib", Role.LIBRARIAN, null));

        assertThat(users.findByEmailIgnoreCase("LIB@EXAMPLE.COM")).isPresent();
        assertThat(users.existsByEmailIgnoreCase("Lib@Example.com")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("nobody@example.com")).isFalse();
    }

    @Test
    void checksStudentNumberExists() {
        users.saveAndFlush(new User("s2@example.com", "hash", "S2", Role.STUDENT, "S-77"));

        assertThat(users.existsByStudentNumber("S-77")).isTrue();
        assertThat(users.existsByStudentNumber("S-78")).isFalse();
    }

    @Test
    void duplicateEmailIsRejectedByTheDatabase() {
        users.saveAndFlush(new User("dup@example.com", "hash", "A", Role.STUDENT, null));

        assertThatThrownBy(() -> users.saveAndFlush(new User("DUP@example.com", "hash", "B", Role.STUDENT, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
