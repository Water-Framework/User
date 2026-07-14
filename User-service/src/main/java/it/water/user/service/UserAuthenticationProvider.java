package it.water.user.service;

import it.water.core.api.action.Action;
import it.water.core.api.action.ActionList;
import it.water.core.api.action.ActionsManager;
import it.water.core.api.permission.PermissionManager;
import it.water.core.api.role.RoleManager;
import it.water.core.api.security.Authenticable;
import it.water.core.api.security.AuthenticationProvider;
import it.water.core.api.security.PasswordHashService;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.action.UserActions;
import it.water.core.permission.exceptions.UnauthorizedException;
import it.water.user.api.UserCompanySystemApi;
import it.water.user.api.UserRepository;
import it.water.user.api.UserSystemApi;
import it.water.user.model.WaterUser;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@FrameworkComponent(services = AuthenticationProvider.class)
public class UserAuthenticationProvider implements AuthenticationProvider {
    private static final Logger logger = LoggerFactory.getLogger(UserAuthenticationProvider.class);
    public static final String WRONG_USER_OR_PWD_MESSAGE = "username or password incorrect!";
    @Inject
    @Setter
    private UserSystemApi userSystemApi;

    @Inject
    @Setter
    private PasswordHashService passwordHashService;

    @Inject
    @Setter
    private UserRepository userRepository;

    @Inject
    @Setter
    private RoleManager roleManager;

    //Membership lookups for the multi-tenant login gate. SystemApi bypasses permissions: this is
    //trusted internal code running as part of credential validation, never serving an external request.
    @Inject
    @Setter
    private UserCompanySystemApi userCompanySystemApi;

    //Impersonation gate: the caller must hold the IMPERSONATE action on WaterUser (admins pass by
    //construction). The permission check + target load live here (User-service) for module isolation:
    //Authentication only passes callerUsername + targetUsername.
    @Inject
    @Setter
    private PermissionManager permissionManager;

    @Inject
    @Setter
    private ActionsManager actionsManager;

    //M11: lazily-computed, cached dummy PHC hash used to equalize login timing for unknown users.
    //The PHC hash self-contains its salt, so the salt argument to matches() is unused on the PHC path.
    private volatile String dummyHash;

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            synchronized (this) {
                if (dummyHash == null)
                    dummyHash = passwordHashService.hash("dummy-anti-enumeration-password".toCharArray());
                h = dummyHash;
            }
        }
        return h;
    }

    @Override
    public Authenticable login(String username, String password) {
        WaterUser u = userSystemApi.findByUsername(username);
        char[] clearText = (password == null) ? new char[0] : password.toCharArray();
        boolean passwordOk;
        if (u == null) {
            //the result is discarded, we only care that the expensive verify always runs
            passwordHashService.matches(clearText, dummyHash(), "");
            passwordOk = false;
        } else {
            //constant-time check supporting both PHC hashes and legacy salted digests
            passwordOk = password != null && !password.isBlank()
                    && passwordHashService.matches(clearText, u.getPassword(), u.getSalt());
        }
        if (u == null || !passwordOk)
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);

        //inactive or (soft-)deleted accounts must not log in; reuse the generic credentials
        //message so no account-state oracle leaks to the caller
        if (!u.isActive() || u.isDeleted())
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);

        //rehash-on-login upgrade, best-effort: a failure must never break a successful login
        if (passwordHashService.needsRehash(u.getPassword())) {
            try {
                String newPhc = passwordHashService.hash(clearText);
                userRepository.updatePasswordHashVerbatim(u.getId(), newPhc);
            } catch (RuntimeException e) {
                logger.warn("Best-effort password rehash failed for user {}: {}", username, e.getMessage());
            }
        }

        if (roleManager != null) {
            //loading roles in order to setup authenticable correctly
            u.setRoles(roleManager.getUserRoles(u.getId()));
        } else {
            u.setRoles(Collections.emptySet());
        }
        return u;
    }

    /**
     * Multi-tenant login gate. Validates credentials via the existing 2-arg login, then resolves and
     * validates the active company for the session and pins it on the returned WaterUser. This is the
     * ONLY place membership is validated at token-issuance time (§5.3), so skipping it = tenant escalation.
     *
     * @param username
     * @param password
     * @param companyId requested active company (may be null)
     * @return the authenticated user with its active company resolved
     */
    @Override
    public Authenticable login(String username, String password, Long companyId) {
        WaterUser u = (WaterUser) login(username, password);
        u.setActiveCompanyId(resolveActiveCompany(u, companyId));
        return u;
    }

    /**
     * Resolves the active company for a just-authenticated user:
     * <ul>
     *   <li>admin: always non-scoped (null); an admin does not scope itself via companyId — it
     *       operates cross-tenant for provisioning and enters a tenant only via user impersonation;</li>
     *   <li>non-admin with a requested company: it MUST be an existing membership, otherwise UnauthorizedException;</li>
     *   <li>non-admin with no requested company: returns the primary company (may be null).</li>
     * </ul>
     */
    private Long resolveActiveCompany(WaterUser u, Long requested) {
        if (u.isAdmin())
            return null;
        if (requested != null) {
            if (!userCompanySystemApi.existsByUserAndCompany(u.getId(), requested))
                throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);
            return requested;
        }
        return userCompanySystemApi.findPrimaryCompanyId(u.getId());
    }

    /**
     * User-level impersonation. Mints an Authenticable carrying the TARGET's identity on behalf of a
     * caller. Permission-gated (NOT gated by the multi-tenant flag): the caller must hold the
     * IMPERSONATE action on WaterUser; an admin passes by construction. No password is verified for
     * the target (the caller is already authenticated and authorized). The resulting Authenticable is
     * marked with impersonatedBy = callerUsername so the token issuer emits the impersonation claim.
     *
     * @param targetUsername username of the user to impersonate
     * @param callerUsername username of the authorized caller
     * @param companyId      requested active company for the target (may be null → target's primary)
     * @return the target WaterUser with active company + roles resolved and impersonatedBy set
     */
    @Override
    public Authenticable impersonate(String targetUsername, String callerUsername, Long companyId) {
        //1. permission gate: caller must hold IMPERSONATE on WaterUser (admin passes by construction)
        Action impersonateAction = resolveImpersonateAction();
        if (impersonateAction == null || !permissionManager.checkPermission(callerUsername, WaterUser.class, impersonateAction))
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);

        //2. load the target without verifying a password; reject inactive/deleted with the generic message
        WaterUser target = userSystemApi.findByUsername(targetUsername);
        if (target == null || !target.isActive() || target.isDeleted())
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);

        //3. resolve the target's active company using the same rules as a normal non-admin login:
        //   requested company must be a membership, otherwise fall back to the target's primary company
        target.setActiveCompanyId(resolveImpersonatedCompany(target, companyId));

        //4. load the target's roles exactly as the 2-arg login does
        if (roleManager != null)
            target.setRoles(roleManager.getUserRoles(target.getId()));
        else
            target.setRoles(Collections.emptySet());

        //5. mark the session as impersonated by the caller (audit-only marker, no operational restriction)
        target.setImpersonatedBy(callerUsername);
        return target;
    }

    /**
     * Resolves the IMPERSONATE Action registered for WaterUser via the ActionsManager, or null if
     * it is not available (e.g. actions not yet registered).
     */
    private Action resolveImpersonateAction() {
        if (actionsManager == null)
            return null;
        ActionList<?> waterUserActions = actionsManager.getActions().get(WaterUser.class.getName());
        return (waterUserActions != null) ? waterUserActions.getAction(UserActions.IMPERSONATE) : null;
    }

    /**
     * Resolves the active company for the impersonated target with the normal non-admin login rules:
     * a requested company MUST be an existing membership (else UnauthorizedException); otherwise the
     * target's primary company is used (may be null).
     */
    private Long resolveImpersonatedCompany(WaterUser target, Long requested) {
        if (requested != null) {
            if (!userCompanySystemApi.existsByUserAndCompany(target.getId(), requested))
                throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);
            return requested;
        }
        return userCompanySystemApi.findPrimaryCompanyId(target.getId());
    }

    /**
     * Retuns the WaterUser class name as issuer
     *
     * @return
     */
    @Override
    public Collection<String> issuersNames() {
        return List.of(WaterUser.WATER_USER_ISSUER);
    }
}
