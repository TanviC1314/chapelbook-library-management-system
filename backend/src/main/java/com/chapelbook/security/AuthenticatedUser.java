package com.chapelbook.security;

import com.chapelbook.user.Role;

/** The caller, as seen by controllers (via @AuthenticationPrincipal). Built from the database, not the token. */
public record AuthenticatedUser(long id, String email, Role role) {
}
