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

    @Override
    public Authenticable login(String username, String password) {
        WaterUser u = userSystemApi.findByUsername(username);
        if (u == null || password == null || password.isBlank())
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);

        char[] clearText = password.toCharArray();
        //constant-time check supporting both PHC hashes and legacy salted digests
        if (!passwordHashService.matches(clearText, u.getPassword(), u.getSalt()))
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
