package com.chapelbook.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByStudentNumber(String studentNumber);

    boolean existsByRole(Role role);

    /**
     * Locks every account with this role until the transaction ends. Changes to admin accounts take this lock
     * so two admins cannot demote each other at the same moment and leave the system with no admin.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role order by u.id")
    List<User> lockAllByRole(@Param("role") Role role);

    /**
     * Loads a user and locks their row until the transaction ends. Used to make one student's seat bookings
     * happen one at a time, so two of their requests cannot deadlock on the database's exclusion constraints.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
