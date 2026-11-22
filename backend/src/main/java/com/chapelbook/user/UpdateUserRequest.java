package com.chapelbook.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An admin edits an account. Every field is optional; only the ones sent change. Student accounts always stay
 * students: {@code role} may only move an account between LIBRARIAN and ADMIN.
 */
public record UpdateUserRequest(
        @Size(max = 100) @jakarta.validation.constraints.Pattern(regexp = ".*\\S.*", message = "must not be blank") String fullName,
        Role role,
        Boolean active) {
}
