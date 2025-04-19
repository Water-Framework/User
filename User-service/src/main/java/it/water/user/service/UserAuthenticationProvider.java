package it.water.user.service;

import it.water.core.api.role.RoleManager;
import it.water.core.api.security.Authenticable;
import it.water.core.api.security.AuthenticationProvider;
import it.water.core.api.security.EncryptionUtil;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.exceptions.UnauthorizedException;
import it.water.user.api.UserSystemApi;
import it.water.user.model.WaterUser;
import lombok.Setter;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@FrameworkComponent(services = AuthenticationProvider.class)
public class UserAuthenticationProvider implements AuthenticationProvider {
    public static final String WRONG_USER_OR_PWD_MESSAGE = "username or password incorrect!";
    @Inject
    @Setter
    private UserSystemApi userSystemApi;

    @Inject
    @Setter
    private EncryptionUtil encryptionUtil;

    @Inject
    @Setter
    private RoleManager roleManager;

    @Override
    public Authenticable login(String username, String password) {
        WaterUser u = userSystemApi.findByUsername(username);
        if (u == null || password == null || password.isBlank())
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);

        try {
            byte[] salt = Base64.getDecoder().decode(u.getSalt());
            password = new String(encryptionUtil.hashPassword(salt, password));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);
        }

        if (!u.getPassword().equals(password)) throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);

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
