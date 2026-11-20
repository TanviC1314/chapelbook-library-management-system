package com.chapelbook.user;

/** What the API reveals about a user. Never includes the password hash. */
public record UserResponse(Long id, String email, String fullName, Role role, String studentNumber, boolean active) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getStudentNumber(), user.isActive());
    }
}
