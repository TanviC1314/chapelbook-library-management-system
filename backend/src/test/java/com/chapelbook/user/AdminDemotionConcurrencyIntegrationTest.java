package com.chapelbook.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.chapelbook.common.BusinessRuleException;
import com.chapelbook.support.PostgresTestContainerConfig;
import com.chapelbook.support.TestAuth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Two admins each try to demote the other at the very same moment. Without the lock on admin accounts both
 * checks would see "two admins" and both would succeed, leaving nobody able to administer the system.
 */
@SpringBootTest
@Import({PostgresTestContainerConfig.class, TestAuth.class})
class AdminDemotionConcurrencyIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository users;
    @Autowired
    private TestAuth auth;

    @Test
    void twoAdminsDemotingEachOtherAtOnceLeaveExactlyOneActiveAdmin() throws Exception {
        // Make these two the only active admins in the database.
        User a = auth.createUser(Role.ADMIN);
        User b = auth.createUser(Role.ADMIN);
        users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN && u.isActive()
                && !u.getId().equals(a.getId()) && !u.getId().equals(b.getId())).forEach(u -> {
                    u.setActive(false);
                    users.saveAndFlush(u);
                });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        futures.add(pool.submit(() -> attempt(go, a, b)));
        futures.add(pool.submit(() -> attempt(go, b, a)));
        go.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> f : futures) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        long succeeded = outcomes.stream().filter(o -> o instanceof UserResponse).count();
        long refused = outcomes.stream().filter(o -> o instanceof BusinessRuleException).count();
        assertThat(succeeded).isEqualTo(1);
        assertThat(refused).isEqualTo(1); // refused cleanly: no deadlock, no crash
        long activeAdmins = users.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.isActive()).count();
        assertThat(activeAdmins).isEqualTo(1);
    }

    private Object attempt(CountDownLatch go, User caller, User target) throws InterruptedException {
        go.await();
        try {
            return userService.update(caller.getId(), target.getId(), new UpdateUserRequest(null, Role.LIBRARIAN, null));
        } catch (Exception e) {
            return e;
        }
    }
}
