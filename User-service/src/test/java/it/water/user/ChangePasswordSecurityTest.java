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

import it.water.core.api.bundle.Runtime;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.security.EncryptionUtil;
import it.water.core.api.security.PasswordHashService;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.model.exceptions.WaterRuntimeException;
import it.water.core.testing.utils.bundle.TestRuntimeInitializer;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.user.api.UserApi;
import it.water.user.api.UserRepository;
import it.water.user.model.WaterUser;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

// ValidationException extends RuntimeException (not WaterRuntimeException); import for clarity

/**
 * Regression tests for H29 — changePassword now verifies the old password via
 * passwordHashService.matches() (constant-time, PHC-aware) instead of the
 * broken plaintext-equality check that always failed.
 *
 * <p>Key scenarios:
 * <ul>
 *   <li>Correct plaintext old password → changePassword succeeds.</li>
 *   <li>Wrong old password → rejected with WaterRuntimeException.</li>
 *   <li>PHC hash string passed as oldPassword (pass-the-hash attempt) → rejected.</li>
 *   <li>null / blank oldPassword / newPassword / passwordConfirm → null-check branch fires.</li>
 * </ul>
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChangePasswordSecurityTest implements Service {

    private static final String PLAIN_PASSWORD = "H29TestPass1_.";
    private static final String NEW_PASSWORD = "H29NewPass1_.";
    private static final String PHC_PREFIX = "$pbkdf2-sha256$";

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private UserApi userApi;

    @Inject
    @Setter
    private UserRepository userRepository;

    @Inject
    @Setter
    private PasswordHashService passwordHashService;

    @Inject
    @Setter
    private EncryptionUtil encryptionUtil;

    @Inject
    @Setter
    private Runtime runtime;

    private WaterUser testUser;

    @BeforeAll
    void setupAdmin() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    /**
     * Creates and persists a fresh user for each test so tests are independent.
     * Returns the user with its id set.
     */
    private WaterUser persistNewUser(String plainPassword) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String salt = new String(encryptionUtil.generate16BytesSalt());
        WaterUser user = new WaterUser(
                "H29", "Test", "h29user_" + suffix, plainPassword, salt, false,
                "h29_" + suffix + "@test.water.it");
        user.setPasswordConfirm(plainPassword);
        user.setActive(true);
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        return userApi.save(user);
    }

    // H29-1: correct old password → changePassword succeeds

    @Test
    @Order(1)
    void changePasswordCorrectOldPasswordSucceeds() {
        WaterUser user = persistNewUser(PLAIN_PASSWORD);
        TestRuntimeInitializer.getInstance().impersonate(user, runtime);

        WaterUser updated = Assertions.assertDoesNotThrow(
                () -> userApi.changePassword(user.getId(), PLAIN_PASSWORD, NEW_PASSWORD, NEW_PASSWORD),
                "changePassword must succeed when the correct plaintext old password is supplied");

        Assertions.assertNotNull(updated, "changePassword must return the updated WaterUser");

        // The stored password must be a PHC hash, not the plaintext new password
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        WaterUser fromDb = userRepository.findByUsername(user.getUsername());
        Assertions.assertNotNull(fromDb);
        Assertions.assertTrue(fromDb.getPassword().startsWith(PHC_PREFIX),
                "The stored password must be in PHC format after a successful changePassword");
        Assertions.assertNotEquals(NEW_PASSWORD, fromDb.getPassword(),
                "The stored password must be the PHC hash, not the plaintext new password");

        // Cleanup
        userRepository.remove(fromDb.getId());
        runtime.fillSecurityContext(null);
    }

    // H29-2: wrong old password → rejected

    @Test
    @Order(2)
    void changePasswordWrongOldPasswordRejected() {
        WaterUser user = persistNewUser(PLAIN_PASSWORD);
        TestRuntimeInitializer.getInstance().impersonate(user, runtime);

        Assertions.assertThrows(WaterRuntimeException.class,
                () -> userApi.changePassword(user.getId(), "TotallyWrong99!", NEW_PASSWORD, NEW_PASSWORD),
                "changePassword must reject an incorrect old password (H29)");

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        userRepository.remove(user.getId());
        runtime.fillSecurityContext(null);
    }

    // H29-3: pass-the-hash: the stored PHC hash itself as oldPassword must be rejected

    @Test
    @Order(3)
    void changePasswordPhcHashAsOldPasswordRejected() {
        WaterUser user = persistNewUser(PLAIN_PASSWORD);

        // Read the stored PHC hash
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        WaterUser fromDb = userRepository.findByUsername(user.getUsername());
        Assertions.assertNotNull(fromDb);
        String storedPhcHash = fromDb.getPassword();
        Assertions.assertTrue(storedPhcHash.startsWith(PHC_PREFIX),
                "Pre-condition: stored password must be in PHC format");

        // Attempt pass-the-hash: supply the PHC string as the old password
        TestRuntimeInitializer.getInstance().impersonate(user, runtime);
        Assertions.assertThrows(WaterRuntimeException.class,
                () -> userApi.changePassword(user.getId(), storedPhcHash, NEW_PASSWORD, NEW_PASSWORD),
                "Supplying the stored PHC hash as oldPassword (pass-the-hash) must be rejected");

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        userRepository.remove(user.getId());
        runtime.fillSecurityContext(null);
    }

    // H29-4: null oldPassword → null-guard branch

    @Test
    @Order(4)
    void changePasswordNullOldPasswordRejected() {
        WaterUser user = persistNewUser(PLAIN_PASSWORD);
        TestRuntimeInitializer.getInstance().impersonate(user, runtime);

        Assertions.assertThrows(WaterRuntimeException.class,
                () -> userApi.changePassword(user.getId(), null, NEW_PASSWORD, NEW_PASSWORD),
                "null oldPassword must be rejected (null-check branch in changePassword)");

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        userRepository.remove(user.getId());
        runtime.fillSecurityContext(null);
    }

    // H29-5: null newPassword → null-guard branch

    @Test
    @Order(5)
    void changePasswordNullNewPasswordRejected() {
        WaterUser user = persistNewUser(PLAIN_PASSWORD);
        TestRuntimeInitializer.getInstance().impersonate(user, runtime);

        Assertions.assertThrows(WaterRuntimeException.class,
                () -> userApi.changePassword(user.getId(), PLAIN_PASSWORD, null, null),
                "null newPassword must be rejected (null-check branch in changePassword)");

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        userRepository.remove(user.getId());
        runtime.fillSecurityContext(null);
    }

    // H29-6: null passwordConfirm → null-guard branch

    @Test
    @Order(6)
    void changePasswordNullPasswordConfirmRejected() {
        WaterUser user = persistNewUser(PLAIN_PASSWORD);
        TestRuntimeInitializer.getInstance().impersonate(user, runtime);

        Assertions.assertThrows(WaterRuntimeException.class,
                () -> userApi.changePassword(user.getId(), PLAIN_PASSWORD, NEW_PASSWORD, null),
                "null passwordConfirm must be rejected (null-check branch in changePassword)");

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        userRepository.remove(user.getId());
        runtime.fillSecurityContext(null);
    }

    // H29-7: newPassword and passwordConfirm mismatch → rejected even with correct old password
    // Note: WaterUser.updatePassword() throws ValidationException (extends RuntimeException, not
    // WaterRuntimeException) when passwords do not match. We assert the common RuntimeException
    // ancestor to be robust against framework wrapping behaviour.

    @Test
    @Order(7)
    void changePasswordMismatchedNewPasswordRejected() {
        WaterUser user = persistNewUser(PLAIN_PASSWORD);
        TestRuntimeInitializer.getInstance().impersonate(user, runtime);

        Assertions.assertThrows(RuntimeException.class,
                () -> userApi.changePassword(user.getId(), PLAIN_PASSWORD, NEW_PASSWORD, NEW_PASSWORD + "X"),
                "Mismatched newPassword / passwordConfirm must be rejected (throws ValidationException or WaterRuntimeException)");

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        userRepository.remove(user.getId());
        runtime.fillSecurityContext(null);
    }

    // H29-8: changePassword is idempotent for successive correct-password calls
    // (first change succeeds; second call with the NEW password as old password also succeeds)

    @Test
    @Order(8)
    void changePasswordSecondChangeWithNewPasswordAsOldSucceeds() {
        WaterUser user = persistNewUser(PLAIN_PASSWORD);
        TestRuntimeInitializer.getInstance().impersonate(user, runtime);

        // First change
        Assertions.assertDoesNotThrow(
                () -> userApi.changePassword(user.getId(), PLAIN_PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

        // Second change: old password is now NEW_PASSWORD
        String newerPassword = "H29Newer2_.";
        Assertions.assertDoesNotThrow(
                () -> userApi.changePassword(user.getId(), NEW_PASSWORD, newerPassword, newerPassword),
                "A successive changePassword using the recently-set password as old password must succeed");

        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        userRepository.remove(user.getId());
        runtime.fillSecurityContext(null);
    }
}
