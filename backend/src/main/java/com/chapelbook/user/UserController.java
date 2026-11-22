package com.chapelbook.user;

import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Users", description = "Accounts and roles. Mostly administrator only.")
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

    /**
     * Staff look students up to issue books; admins manage everyone. {@code q} matches name, email or student
     * number; {@code role} and {@code active} filter.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Role role, @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.list(q, role, active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    /** Only admins create accounts with a chosen role, e.g. librarians. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    /** Admin: rename, deactivate or reactivate, or move a staff account between LIBRARIAN and ADMIN. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(caller.id(), id, request);
    }

    /** Admin: set a new password for an account. */
    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
    }
}
