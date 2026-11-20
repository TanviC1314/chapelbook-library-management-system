package com.chapelbook.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-registration. There is deliberately no role field: anyone who registers is a student.
 * Librarians and admins are created by an admin.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes, so a longer password would be silently truncated.
        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String password,
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 30) String studentNumber) {
}
