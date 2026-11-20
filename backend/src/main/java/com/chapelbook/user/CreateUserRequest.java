package com.chapelbook.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An admin creating an account with a chosen role (typically a librarian). */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String password,
        @NotBlank @Size(max = 100) String fullName,
        @NotNull Role role,
        @Size(max = 30) String studentNumber) {
}
