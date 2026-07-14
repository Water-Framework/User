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

import it.water.core.api.action.Action;
import it.water.core.api.action.ActionList;
import it.water.core.api.action.ActionsManager;
import it.water.core.api.model.Role;
import it.water.core.api.permission.PermissionManager;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.role.RoleManager;
import it.water.core.api.security.Authenticable;
import it.water.core.api.security.AuthenticationProvider;
import it.water.core.api.security.EncryptionUtil;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.action.UserActions;
import it.water.core.permission.exceptions.UnauthorizedException;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.user.api.UserRepository;
import it.water.user.model.WaterUser;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

/**
 * Multitenancy Tassello 3 (see multitenancy-analysis-proposal.md, section 5.4 + 9) — regression
 * tests for {@link it.water.user.service.UserAuthenticationProvider#impersonate(String, String, Long)}.
 * <p>
 * Covers:
 * <ul>
 *     <li>an admin caller (passes the permission gate by construction) successfully mints an
 *     Authenticable carrying the TARGET's identity, marked with {@code impersonatedBy};</li>
 *     <li>a non-admin caller WITHOUT the {@code IMPERSONATE} permission (granted to NO default
 *     role) is rejected with the same generic credentials message;</li>
 *     <li>a non-admin caller explicitly GRANTED the {@code IMPERSONATE} permission succeeds;</li>
 *     <li>target resolution failures (not found / inactive / soft-deleted) are all rejected with
 *     the same generic message (no account-state oracle).</li>
 * </ul>
 * All scenarios drive the provider directly with a plain {@code callerUsername} String, since
 * {@link PermissionManager#checkPermission(String, Class, Action)} resolves permissions purely by
 * username (no dependency on the calling thread's SecurityContext).
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImpersonationTest implements Service {

    private static final String TEST_PLAIN_PASSWORD = "ImpersonateTestPwd1_.";
    private static final String WRONG_USER_OR_PWD_MSG = "username or password incorrect!";
    private static final String ADMIN_USERNAME = "admin";

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

    @Inject
    @Setter
    private RoleManager roleManager;

    @Inject
    @Setter
    private PermissionManager permissionManager;

    @Inject
    @Setter
    private ActionsManager actionsManager;

    @BeforeAll
    void setupAdmin() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    // ------------------------------------------------------------------
    // Admin caller — happy path
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void impersonate_adminCaller_targetActive_returnsTargetIdentityMarkedAsImpersonated() {
        WaterUser target = buildAndPersistUser("impTarget1_", true, false);

        Authenticable result = authenticationProvider.impersonate(target.getUsername(), ADMIN_USERNAME, null);

        Assertions.assertNotNull(result, "impersonate must return a non-null Authenticable");
        Assertions.assertEquals(target.getUsername(), result.getScreenName(),
                "the impersonation token must carry the TARGET's screenName, not the caller's");
        Assertions.assertEquals(target.getId(), result.getLoggedEntityId(),
                "the impersonation token must carry the TARGET's loggedEntityId");
        Assertions.assertEquals(ADMIN_USERNAME, result.getImpersonatedBy(),
                "impersonatedBy must be set to the caller's username");
        Assertions.assertFalse(result.isAdmin(), "the impersonated target is a regular (non-admin) user");
        Assertions.assertNotNull(result.getRoles(), "roles must be resolved (possibly empty) on the impersonated identity");

        userRepository.remove(target.getId());
    }

    // ------------------------------------------------------------------
    // Non-admin caller WITHOUT permission — permission gate
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void impersonate_nonAdminCallerWithoutPermission_throwsUnauthorized() {
        WaterUser bob = buildAndPersistUser("impBob_", true, false);
        WaterUser target = buildAndPersistUser("impTarget2_", true, false);
        String targetUsername = target.getUsername();
        String bobUsername = bob.getUsername();

        UnauthorizedException ex = Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.impersonate(targetUsername, bobUsername, null),
                "A caller without the IMPERSONATE permission (granted to no default role) must be rejected");
        Assertions.assertEquals(WRONG_USER_OR_PWD_MSG, ex.getMessage(),
                "Permission-gate rejection must use the same generic credentials message (no oracle)");

        userRepository.remove(bob.getId());
        userRepository.remove(target.getId());
    }

    // ------------------------------------------------------------------
    // Target resolution failures
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void impersonate_targetNotFound_throwsUnauthorized() {
        String unknownTarget = "impUnknownTarget_" + UUID.randomUUID();

        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.impersonate(unknownTarget, ADMIN_USERNAME, null),
                "Impersonating a non-existent username must throw UnauthorizedException");
    }

    @Test
    @Order(4)
    void impersonate_targetInactive_throwsUnauthorized() {
        WaterUser inactiveTarget = buildAndPersistUser("impInactive_", false, false);
        String username = inactiveTarget.getUsername();

        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.impersonate(username, ADMIN_USERNAME, null),
                "Impersonating an inactive target must throw UnauthorizedException");

        userRepository.remove(inactiveTarget.getId());
    }

    @Test
    @Order(5)
    void impersonate_targetSoftDeleted_throwsUnauthorized() {
        WaterUser deletedTarget = buildAndPersistUser("impDeleted_", true, true);
        String username = deletedTarget.getUsername();

        Assertions.assertThrows(UnauthorizedException.class,
                () -> authenticationProvider.impersonate(username, ADMIN_USERNAME, null),
                "Impersonating a soft-deleted target must throw UnauthorizedException");

        userRepository.remove(deletedTarget.getId());
    }

    // ------------------------------------------------------------------
    // Non-admin caller EXPLICITLY GRANTED the IMPERSONATE permission — success
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void impersonate_nonAdminCallerGrantedImpersonatePermission_succeeds() {
        WaterUser carol = buildAndPersistUser("impCarol_", true, false);
        WaterUser target = buildAndPersistUser("impTarget6_", true, false);

        Role impersonatorRole = roleManager.createIfNotExists("userImpersonatorTestRole");
        Action impersonateAction = resolveImpersonateAction();
        Assertions.assertNotNull(impersonateAction, "IMPERSONATE action must be registered for WaterUser");
        permissionManager.addPermissionIfNotExists(impersonatorRole, WaterUser.class, impersonateAction);
        Assertions.assertTrue(roleManager.addRole(carol.getId(), impersonatorRole),
                "granting the test role to carol must succeed");

        Authenticable result = authenticationProvider.impersonate(target.getUsername(), carol.getUsername(), null);

        Assertions.assertNotNull(result, "a caller explicitly granted IMPERSONATE must succeed");
        Assertions.assertEquals(target.getUsername(), result.getScreenName());
        Assertions.assertEquals(carol.getUsername(), result.getImpersonatedBy(),
                "impersonatedBy must reflect the granted non-admin caller, not admin");

        roleManager.removeRole(carol.getId(), impersonatorRole);
        userRepository.remove(carol.getId());
        userRepository.remove(target.getId());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private WaterUser buildAndPersistUser(String prefix, boolean active, boolean deleted) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String salt = new String(encryptionUtil.generate16BytesSalt());
        WaterUser user = new WaterUser(
                "Impersonation", "Test", prefix + suffix, TEST_PLAIN_PASSWORD, salt, false,
                prefix + suffix + "@test.water.it");
        user.setPasswordConfirm(TEST_PLAIN_PASSWORD);
        user.setActive(active);
        user.setDeleted(deleted);
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        return userRepository.persist(user);
    }

    /**
     * Mirrors {@code UserAuthenticationProvider.resolveImpersonateAction()}: resolves the
     * IMPERSONATE action registered for WaterUser via the ActionsManager.
     */
    private Action resolveImpersonateAction() {
        ActionList<?> waterUserActions = actionsManager.getActions().get(WaterUser.class.getName());
        return (waterUserActions != null) ? waterUserActions.getAction(UserActions.IMPERSONATE) : null;
    }
}
