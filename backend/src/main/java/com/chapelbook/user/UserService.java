package com.chapelbook.user;

import com.chapelbook.common.BusinessRuleException;
import com.chapelbook.common.ConflictException;
import com.chapelbook.common.PageResponse;
import com.chapelbook.common.ResourceNotFoundException;
import java.util.List;
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
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    /** Staff search: text matches name, email or student number (every word must match); role and active filter. */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String text, Role role, Boolean active, Pageable pageable) {
        Specification<User> filter = Specification.where(matchesText(text));
        if (role != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (active != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        return PageResponse.of(users.findAll(filter, pageable), UserResponse::from);
    }

    /**
     * Edits an account. Rules: students stay students; an admin cannot deactivate or demote themselves; and the
     * system always keeps at least one active admin. Changes to admins lock all admin accounts first, so two
     * admins acting on each other at the same moment are handled one after the other.
     */
    @Transactional
    public UserResponse update(long callerId, long targetId, UpdateUserRequest request) {
        User target = find(targetId);
        boolean touchesAnAdmin = target.getRole() == Role.ADMIN;
        List<User> admins = touchesAnAdmin ? users.lockAllByRole(Role.ADMIN) : List.of();
        if (touchesAnAdmin) {
            target = admins.stream().filter(a -> a.getId().equals(targetId)).findFirst()
                    .orElseGet(() -> find(targetId)); // lost admin status while we waited for the lock
        }

        boolean demoting = request.role() != null && request.role() != target.getRole();
        boolean deactivating = Boolean.FALSE.equals(request.active()) && target.isActive();
        if (demoting && (request.role() == Role.STUDENT || target.getRole() == Role.STUDENT)) {
            throw new BusinessRuleException("Student accounts stay students; only LIBRARIAN and ADMIN can change role.");
        }
        if (target.getId().equals(callerId) && (deactivating || (demoting && target.getRole() == Role.ADMIN))) {
            throw new BusinessRuleException("You cannot deactivate or demote your own account.");
        }
        boolean losesAdminAccess = target.getRole() == Role.ADMIN && target.isActive()
                && ((demoting && request.role() != Role.ADMIN) || deactivating);
        if (losesAdminAccess && admins.stream().filter(User::isActive).count() <= 1) {
            throw new BusinessRuleException("The last active admin cannot be demoted or deactivated.");
        }

        if (request.fullName() != null) {
            target.setFullName(request.fullName());
        }
        if (demoting) {
            target.setRole(request.role());
        }
        if (request.active() != null) {
            target.setActive(request.active());
        }
        return UserResponse.from(users.saveAndFlush(target));
    }

    /**
     * Sets a new password for an account. Tokens already issued stay valid until they expire (one hour), because
     * tokens are stateless; deactivating the account is what cuts access off immediately.
     */
    @Transactional
    public void resetPassword(long targetId, String newPassword) {
        User target = find(targetId);
        target.setPasswordHash(encoder.encode(newPassword));
        users.saveAndFlush(target);
    }

    private User find(long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    /** Every typed word must appear in the name, email or student number (case-insensitive, literal). */
    private static Specification<User> matchesText(String text) {
        if (text == null || text.isBlank()) {
            return Specification.where(null);
        }
        String[] terms = text.trim().toLowerCase().split("\\s+");
        return (root, query, cb) -> {
            var perTerm = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            for (int i = 0; i < Math.min(terms.length, 5); i++) {
                String pattern = "%" + terms[i].replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                perTerm.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("studentNumber"), "")), pattern, '\\')));
            }
            return cb.and(perTerm.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
