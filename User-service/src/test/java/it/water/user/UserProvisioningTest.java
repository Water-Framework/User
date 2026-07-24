package it.water.user;

import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.user.api.UserCompanySystemApi;
import it.water.user.api.UserSystemApi;
import it.water.user.model.WaterUser;
import lombok.Setter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Verifies the Water-owned provisioning boundary used by Knowesis.
 */
@ExtendWith(WaterTestExtension.class)
class UserProvisioningTest implements Service {

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Test
    void provisionUserCreatesActiveUserAndCompanyMembership() {
        UserSystemApi userSystemApi = componentRegistry.findComponent(UserSystemApi.class, null);
        UserCompanySystemApi membershipApi = componentRegistry.findComponent(UserCompanySystemApi.class, null);
        String suffix = Long.toString(System.nanoTime());
        WaterUser created = userSystemApi.createUserForCompany(
                "Mario",
                "Rossi",
                "mario." + suffix,
                "mario." + suffix + "@example.test",
                "Password1!",
                42L,
                true,
                true);

        Assertions.assertTrue(created.getId() > 0);
        Assertions.assertTrue(created.isActive());
        Assertions.assertTrue(membershipApi.existsByUserAndCompany(created.getId(), 42L));
        Assertions.assertEquals(42L, membershipApi.findPrimaryCompanyId(created.getId()));
    }
}
