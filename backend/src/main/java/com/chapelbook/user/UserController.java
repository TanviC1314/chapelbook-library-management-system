package com.chapelbook.user;

import com.chapelbook.common.ResourceNotFoundException;
import com.chapelbook.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    /** The signed-in user's own profile. Any role. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return users.findById(caller.id()).map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}
