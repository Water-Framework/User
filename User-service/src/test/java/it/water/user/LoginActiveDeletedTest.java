/*
 * Copyright 2024 Aristide Cittadino
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.water.user;

import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.security.AuthenticationProvider;
import it.water.core.api.security.EncryptionUtil;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.exceptions.UnauthorizedException;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.user.api.UserRepository;
import it.water.user.service.UserAuthenticationProvider;
import it.water.user.model.WaterUser;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

/**
 * Regression tests for H30 — {@link it.water.user.service.UserAuthenticationProvider#login}
 * now rejects accounts that are inactive ({@code active=false}) or soft-deleted
 * ({@code deleted=true}), using the same generic error message as for wrong credentials.
 * <p>
 * Also verifies that {@link it.water.user.repository.UserRepositoryImpl#findByUsername} does NOT
 * return soft-deleted users.
 * <p>
 * Security note: all "invalid state" responses use the same message
 * {@link UserAuthenticationProvider#WRONG_USER_OR_PWD_MESSAGE} to prevent account-state oracle
 * leakage to the caller.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoginActiveDeletedTest implements Service {

    private static final String TEST_PLAIN_PASSWORD = "H30TestPwd1_.";
    private static final String WRONG_USER_OR_PWD_MSG = "username or password incorrect!";

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private AuthenticationProvider authenticationProvider;

    @Inject
    @Setter
    private UserRepository userRepository;

    @Inject
    @Setter
    private EncryptionUtil encryptionUtil;

    @BeforeAll
    void setupAdmin() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    // H30-1: user registered but NOT yet activated → login must fail

    @Test
    @Order(1)
    void loginInactiveUserIsRejected() {
        WaterUser user = buildAndPersistUser("h30inactive_", false, false);
        String username = user.getUsername();

        UnauthorizedException ex = Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login(username, TEST_PLAIN_PASSWORD),
                "An inactive (not activated) user must not be able to log in (H30)");

        Assertions.assertEquals(WRONG_USER_OR_PWD_MSG, ex.getMessage(),
                "The rejection message for an inactive account must be identical to the " +
                "wrong-credentials message (no account-state oracle)");

        // Cleanup
        userRepository.remove(user.getId());
    }

    // H30-2: user deactivated (active=false, deleted=false) → login must fail

    @Test
    @Order(2)
    void loginDeactivatedUserIsRejected() {
        WaterUser user = buildAndPersistUser("h30deact_", true, false);

        // Deactivate the user
        userRepository.deactivateUser(user.getId());
        String username = user.getUsername();

        UnauthorizedException ex = Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login(username, TEST_PLAIN_PASSWORD),
                "A deactivated user (active=false, deleted=false) must not be able to log in (H30)");

        Assertions.assertEquals(WRONG_USER_OR_PWD_MSG, ex.getMessage(),
                "Deactivated-account rejection message must be identical to wrong-credentials message");

        // Cleanup
        userRepository.remove(user.getId());
    }

    // H30-3: user soft-deleted (deleted=true) → login must fail

    @Test
    @Order(3)
    void loginSoftDeletedUserIsRejected() {
        WaterUser user = buildAndPersistUser("h30del_", true, false);

        // Soft-delete by flipping the deleted flag directly
        WaterUser fromDb = userRepository.findByUsername(user.getUsername());
        Assertions.assertNotNull(fromDb, "Pre-condition: user must be findable before soft-delete");
        fromDb.setDeleted(true);
        userRepository.update(fromDb);

        // findByUsername must now return null (deleted=false filter)
        WaterUser afterDelete = userRepository.findByUsername(user.getUsername());
        Assertions.assertNull(afterDelete,
                "findByUsername must return null for a soft-deleted user (H30 filter: deleted=false)");

        // login must return the same generic error — the user effectively disappears
        String username = user.getUsername();
        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login(username, TEST_PLAIN_PASSWORD),
                "A soft-deleted user must not be able to log in (H30)");

        // Physical cleanup — find by id since findByUsername excludes deleted
        userRepository.remove(fromDb.getId());
    }

    // H30-4: active, non-deleted user → login succeeds (regression check)

    @Test
    @Order(4)
    void loginActiveNonDeletedUserSucceeds() {
        WaterUser user = buildAndPersistUser("h30active_", true, false);

        Assertions.assertDoesNotThrow(
                () -> authenticationProvider.login(user.getUsername(), TEST_PLAIN_PASSWORD),
                "An active, non-deleted user must be able to log in (H30 regression)");

        // Cleanup
        userRepository.remove(user.getId());
    }

    // H30-5: admin (pre-created, active) → login succeeds

    @Test
    @Order(5)
    void loginAdminUserSucceeds() {
        Assertions.assertDoesNotThrow(
                () -> authenticationProvider.login("admin", "admin"),
                "The built-in admin account must be able to log in");
    }

    // H30-6: findByUsername excludes deleted users

    @Test
    @Order(6)
    void findByUsernameExcludesDeletedUsers() {
        WaterUser user = buildAndPersistUser("h30findDel_", true, false);
        String username = user.getUsername();

        // Confirm findable before deletion
        Assertions.assertNotNull(userRepository.findByUsername(username),
                "Pre-condition: user must be findable before soft-delete");

        // Soft-delete
        WaterUser fromDb = userRepository.findByUsername(username);
        fromDb.setDeleted(true);
        userRepository.update(fromDb);

        // Must now be invisible to findByUsername
        WaterUser result = userRepository.findByUsername(username);
        Assertions.assertNull(result,
                "findByUsername must return null for a soft-deleted user (H30 filter)");

        // Physical cleanup
        userRepository.remove(fromDb.getId());
    }

    // H30-7: inactive AND deleted → login fails (both conditions contribute)

    @Test
    @Order(7)
    void loginInactiveAndDeletedUserIsRejected() {
        WaterUser user = buildAndPersistUser("h30inactdel_", false, false);

        // Soft-delete as well
        WaterUser fromDb = userRepository.find(user.getId());
        fromDb.setDeleted(true);
        userRepository.update(fromDb);

        // findByUsername returns null → UnauthorizedException (user not found path)
        String username = user.getUsername();
        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login(username, TEST_PLAIN_PASSWORD),
                "An inactive AND soft-deleted user must not be able to log in");

        // Physical cleanup
        userRepository.remove(fromDb.getId());
    }

    // H30-8: login with wrong password for an active user → same generic message as inactive

    @Test
    @Order(8)
    void loginWrongPasswordForActiveUserUsesGenericMessage() {
        WaterUser user = buildAndPersistUser("h30wrongpwd_", true, false);
        String username = user.getUsername();

        UnauthorizedException ex = Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login(username, "TotallyWrong99!"),
                "Wrong password for an active user must throw UnauthorizedException");

        Assertions.assertEquals(WRONG_USER_OR_PWD_MSG, ex.getMessage(),
                "Wrong-credentials message must be identical to the inactive-account message " +
                "(no state oracle)");

        // Cleanup
        userRepository.remove(user.getId());
    }

    // Helper: build + persist a user with explicit active/deleted flags
    private WaterUser buildAndPersistUser(String prefix, boolean active, boolean deleted) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String salt = new String(encryptionUtil.generate16BytesSalt());
        WaterUser user = new WaterUser(
                "H30", "Test", prefix + suffix, TEST_PLAIN_PASSWORD, salt, false,
                prefix + suffix + "@test.water.it");
        user.setPasswordConfirm(TEST_PLAIN_PASSWORD);
        user.setActive(active);
        user.setDeleted(deleted);
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        return userRepository.persist(user);
    }
}
