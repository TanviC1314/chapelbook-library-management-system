package com.chapelbook.user;

import com.chapelbook.common.PageResponse;
import com.chapelbook.common.ResourceNotFoundException;
import com.chapelbook.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository users;
    private final UserService userService;

    public UserController(UserRepository users, UserService userService) {
        this.users = users;
        this.userService = userService;
    }

    /** The signed-in user's own profile. Any role. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return users.findById(caller.id()).map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    /** Staff look students up to issue books; admins manage everyone. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public PageResponse<UserResponse> list(@RequestParam(required = false) Role role,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.list(role, pageable);
    }

    /** Only admins create accounts with a chosen role, e.g. librarians. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }
}
