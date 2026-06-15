package it.water.user.service;

import it.water.core.api.role.RoleManager;
import it.water.core.api.security.Authenticable;
import it.water.core.api.security.AuthenticationProvider;
import it.water.core.api.security.PasswordHashService;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.exceptions.UnauthorizedException;
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
     * Retuns the WaterUser class name as issuer
     *
     * @return
     */
    @Override
    public Collection<String> issuersNames() {
        return List.of(WaterUser.WATER_USER_ISSUER);
    }
}
