package com.chapelbook.user;

import com.chapelbook.common.BusinessRuleException;
import com.chapelbook.common.ConflictException;
import com.chapelbook.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = User.normaliseEmail(request.email());
        String studentNumber = request.studentNumber() == null || request.studentNumber().isBlank()
                ? null : request.studentNumber().trim();
        if (studentNumber != null && request.role() != Role.STUDENT) {
            throw new BusinessRuleException("Only students have a student number.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        if (studentNumber != null && users.existsByStudentNumber(studentNumber)) {
            throw new ConflictException("This student number is already registered.");
        }
        User saved = users.saveAndFlush(new User(
                email, encoder.encode(request.password()), request.fullName(), request.role(), studentNumber));
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Role role, Pageable pageable) {
        Specification<User> filter = role == null ? Specification.where(null)
                : (root, query, cb) -> cb.equal(root.get("role"), role);
        return PageResponse.of(users.findAll(filter, pageable), UserResponse::from);
    }
}
