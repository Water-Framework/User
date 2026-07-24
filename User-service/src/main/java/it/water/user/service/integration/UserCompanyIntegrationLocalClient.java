package it.water.user.service.integration;

import it.water.core.api.model.User;
import it.water.core.api.service.integration.UserCompanyIntegrationClient;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.user.api.UserCompanySystemApi;
import it.water.user.api.UserSystemApi;
import lombok.Setter;

import java.util.List;

/**
 * Local adapter used when User and its caller share the same Water runtime.
 */
@FrameworkComponent(priority = 1, services = UserCompanyIntegrationClient.class)
public class UserCompanyIntegrationLocalClient implements UserCompanyIntegrationClient {

    @Inject
    @Setter
    private UserSystemApi userSystemApi;

    @Inject
    @Setter
    private UserCompanySystemApi userCompanySystemApi;

    @Override
    public User createUserForCompany(String name, String lastname, String username, String email,
                                     String password, long companyId, boolean primary, boolean active) {
        return userSystemApi.createUserForCompany(
                name, lastname, username, email, password, companyId, primary, active);
    }

    @Override
    public List<Long> findCompanyIdsByUser(long userId) {
        return userCompanySystemApi.findCompanyIdsByUser(userId);
    }

    @Override
    public boolean hasMembership(long userId, long companyId) {
        return userCompanySystemApi.existsByUserAndCompany(userId, companyId);
    }

    @Override
    public Long findPrimaryCompanyId(long userId) {
        return userCompanySystemApi.findPrimaryCompanyId(userId);
    }

    @Override
    public List<Long> findUserIdsByCompany(long companyId) {
        return userCompanySystemApi.findUserIdsByCompany(companyId);
    }

    @Override
    public List<Long> findPrimaryUserIdsByCompany(long companyId) {
        return userCompanySystemApi.findPrimaryUserIdsByCompany(companyId);
    }
}
