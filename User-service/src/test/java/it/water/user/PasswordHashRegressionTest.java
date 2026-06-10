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
import it.water.core.api.security.PasswordHashService;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.exceptions.UnauthorizedException;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.user.api.UserRepository;
import it.water.user.model.WaterUser;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.UUID;

/**
 * Regression tests for H9 — password hashing in the User module: login with PHC and legacy
 * hashes, rehash-on-login upgrade, rejection of wrong passwords.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PasswordHashRegressionTest implements Service {

    /**
     * PHC string prefix as produced by PasswordHashServiceImpl
     */
    private static final String PHC_PREFIX = "$pbkdf2-sha256$";
    private static final String LEGACY_PLAIN_PASSWORD = "LegacyPass1!";

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
    private PasswordHashService passwordHashService;

    @Inject
    @Setter
    private EncryptionUtil encryptionUtil;

    @BeforeAll
    void setup() {
        // Start as admin so we can manipulate user records directly
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    // H9-I1  Login works for a user with a freshly-hashed PHC password

    @Test
    @Order(1)
    void loginPhcPasswordSucceeds() {
        // The admin user is created on startup; its password was hashed via the new PHC path.
        // A successful login proves the PHC path works end-to-end.
        Assertions.assertDoesNotThrow(
                () -> authenticationProvider.login("admin", "admin"),
                "Login with PHC-hashed password must succeed");
    }

    @Test
    @Order(2)
    void loginPhcPasswordWrongPasswordFails() {
        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login("admin", "notTheAdminPassword"),
                "Login with wrong password against a PHC hash must throw UnauthorizedException");
    }

    // H9-I2  Legacy hash backward compatibility — login works without migration

    /**
     * Creates a user whose stored password is a raw legacy digest
     * (PBKDF2-HMAC-SHA1, the format Water used before the H9 fix), then verifies
     * that {@link AuthenticationProvider#login} still accepts it.
     * <p>
     * The legacy format is:
     * WaterUser.password = new String(encryptionUtil.hashPassword(saltBytes, plainPassword))
     * WaterUser.salt     = Base64(saltBytes)
     */
    @Test
    @Order(3)
    void loginLegacyHashSucceeds()
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        String username = "legacyUser_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String plainPassword = LEGACY_PLAIN_PASSWORD;
        byte[] saltBytes = encryptionUtil.generate16BytesSalt();
        String saltBase64 = Base64.getEncoder().encodeToString(saltBytes);

        // Build the legacy hash exactly as the old code did
        String legacyHash = new String(encryptionUtil.hashPassword(saltBytes, plainPassword));

        // Persist the user with a pre-computed legacy hash, bypassing processUserPassword()
        WaterUser user = buildUser(username, legacyHash, saltBase64);
        // Store verbatim so the repository does NOT re-hash the value
        userRepository.persist(user);
        // Now overwrite the stored password with the raw legacy value via the verbatim updater
        // (persist() itself re-hashes, so we use updatePasswordHashVerbatim to place the legacy value)
        WaterUser persisted = userRepository.findByUsername(username);
        Assertions.assertNotNull(persisted, "Test user must have been persisted");
        // Place the legacy hash verbatim in the DB
        userRepository.updatePasswordHashVerbatim(persisted.getId(), legacyHash);
        // Also restore the legacy salt in the entity (updatePasswordHashVerbatim keeps old salt)
        persisted = userRepository.findByUsername(username);
        persisted.setSalt(saltBase64);
        userRepository.update(persisted);

        // Verify: login must succeed with the legacy hash in the DB
        Assertions.assertDoesNotThrow(
                () -> authenticationProvider.login(username, plainPassword),
                "Login must succeed even when the stored hash is in legacy PBKDF2-SHA1 format");

        // Cleanup
        userRepository.remove(persisted.getId());
    }

    @Test
    @Order(4)
    void loginLegacyHashWrongPasswordFails()
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        String username = "legacyUser2_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String plainPassword = LEGACY_PLAIN_PASSWORD;
        byte[] saltBytes = encryptionUtil.generate16BytesSalt();
        String saltBase64 = Base64.getEncoder().encodeToString(saltBytes);
        String legacyHash = new String(encryptionUtil.hashPassword(saltBytes, plainPassword));

        WaterUser user = buildUser(username, legacyHash, saltBase64);
        userRepository.persist(user);
        WaterUser persisted = userRepository.findByUsername(username);
        userRepository.updatePasswordHashVerbatim(persisted.getId(), legacyHash);
        persisted = userRepository.findByUsername(username);
        persisted.setSalt(saltBase64);
        userRepository.update(persisted);

        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login(username, "TotallyWrong99!"),
                "Login with wrong password against a legacy hash must throw UnauthorizedException");

        userRepository.remove(persisted.getId());
    }

    // H9-I3  Rehash-on-login: after a successful login the stored hash
    //        is transparently upgraded to PHC format

    @Test
    @Order(5)
    void loginLegacyHashTriggerRehashOnLogin()
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        String username = "rehashUser_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String plainPassword = "RehashMe1!"; // intentionally different from LEGACY_PLAIN_PASSWORD — tests a distinct rehash scenario
        byte[] saltBytes = encryptionUtil.generate16BytesSalt();
        String saltBase64 = Base64.getEncoder().encodeToString(saltBytes);
        String legacyHash = new String(encryptionUtil.hashPassword(saltBytes, plainPassword));

        WaterUser user = buildUser(username, legacyHash, saltBase64);
        userRepository.persist(user);
        WaterUser persisted = userRepository.findByUsername(username);
        // Put the legacy hash into the DB
        userRepository.updatePasswordHashVerbatim(persisted.getId(), legacyHash);
        persisted = userRepository.findByUsername(username);
        persisted.setSalt(saltBase64);
        userRepository.update(persisted);

        // Pre-condition: stored hash must be legacy (not PHC)
        WaterUser beforeLogin = userRepository.findByUsername(username);
        Assertions.assertFalse(beforeLogin.getPassword().startsWith("$"),
                "Pre-condition: stored password must be legacy (non-PHC) before first login");
        Assertions.assertTrue(passwordHashService.needsRehash(beforeLogin.getPassword()),
                "Pre-condition: needsRehash() must return true for the stored legacy hash");

        // Perform successful login — this triggers rehash-on-login in UserAuthenticationProvider
        Assertions.assertDoesNotThrow(
                () -> authenticationProvider.login(username, plainPassword),
                "Login must succeed for legacy hash");

        // Post-condition: stored hash must now be PHC format
        WaterUser afterLogin = userRepository.findByUsername(username);
        String storedAfterLogin = afterLogin.getPassword();
        Assertions.assertTrue(storedAfterLogin.startsWith(PHC_PREFIX),
                "After a successful login with a legacy hash, the stored password must be upgraded to PHC format; " +
                        "actual value: " + storedAfterLogin);
        Assertions.assertFalse(passwordHashService.needsRehash(storedAfterLogin),
                "After rehash-on-login, needsRehash() must return false for the new stored hash");

        // Second login must still work with the now-PHC hash
        Assertions.assertDoesNotThrow(
                () -> authenticationProvider.login(username, plainPassword),
                "Login must also succeed after the password has been rehashed to PHC format");

        // Cleanup
        userRepository.remove(afterLogin.getId());
    }

    // H9-I4  Login with null/blank password is always rejected

    @Test
    @Order(6)
    void loginNullPasswordRejected() {
        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login("admin", null),
                "Login with null password must throw UnauthorizedException");
    }

    @Test
    @Order(7)
    void loginBlankPasswordRejected() {
        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login("admin", "   "),
                "Login with blank password must throw UnauthorizedException");
    }

    // H9-I5  Login with non-existent user is rejected

    @Test
    @Order(8)
    void loginUnknownUserRejected() {
        long nanoTime = System.nanoTime();
        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.login("ghost_user_" + nanoTime, "SomePass1!"),
                "Login for a non-existent user must throw UnauthorizedException");
    }

    // Helpers

    private WaterUser buildUser(String username, String password, String salt) {
        // Use password / passwordConfirm matching — validation only runs on persist
        WaterUser u = new WaterUser(
                "Test", "User", username, password, salt, false,
                username + "@test-regression.com");
        u.setPasswordConfirm(password);
        u.setActive(true);
        return u;
    }
}
