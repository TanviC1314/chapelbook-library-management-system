package com.chapelbook.user;

import com.chapelbook.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "student_number")
    private String studentNumber;

    @Column(nullable = false)
    private boolean active = true;

    protected User() {
        // for JPA
    }

    public User(String email, String passwordHash, String fullName, Role role, String studentNumber) {
        this.email = normaliseEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.role = role;
        this.studentNumber = studentNumber;
    }

    /** Emails are compared ignoring case, so they are stored trimmed and lower-cased. */
    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName.trim();
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
