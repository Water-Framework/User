package it.water.user;

import it.water.core.api.model.PaginableResult;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.repository.query.Query;
import it.water.core.api.service.Service;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;

import it.water.user.api.*;
import it.water.user.model.*;

import lombok.Setter;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Generated with Water Generator.
 * Basic CRUD test for the UserCompany membership entity. Runs as admin (permission bypass).
 * Full membership/login scenarios are covered separately.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserCompanyApiTest implements Service {

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private UserCompanyApi userCompanyApi;

    @Inject
    @Setter
    private UserCompanyRepository userCompanyRepository;

    @BeforeAll
    void beforeAll() {
        //default security context is admin so we can exercise the happy path
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(1)
    void componentsInstantiatedCorrectly() {
        this.userCompanyApi = this.componentRegistry.findComponent(UserCompanyApi.class, null);
        Assertions.assertNotNull(this.userCompanyApi);
        Assertions.assertNotNull(this.componentRegistry.findComponent(UserCompanySystemApi.class, null));
        this.userCompanyRepository = this.componentRegistry.findComponent(UserCompanyRepository.class, null);
        Assertions.assertNotNull(this.userCompanyRepository);
    }

    @Test
    @Order(2)
    void saveOk() {
        UserCompany entity = new UserCompany(1L, 100L);
        entity = this.userCompanyApi.save(entity);
        Assertions.assertEquals(1, entity.getEntityVersion());
        Assertions.assertTrue(entity.getId() > 0);
        Assertions.assertEquals(1L, entity.getUserId().longValue());
        Assertions.assertEquals(100L, entity.getCompanyId().longValue());
        Assertions.assertFalse(entity.isPrimary());
    }

    @Test
    @Order(3)
    void updateShouldWork() {
        Query q = this.userCompanyRepository.getQueryBuilderInstance().field("companyId").equalTo(100L);
        UserCompany entity = this.userCompanyApi.find(q);
        Assertions.assertNotNull(entity);
        entity.setPrimary(true);
        entity = this.userCompanyApi.update(entity);
        Assertions.assertTrue(entity.isPrimary());
        Assertions.assertEquals(2, entity.getEntityVersion());
    }

    @Test
    @Order(4)
    void membershipQueriesShouldWork() {
        Assertions.assertTrue(this.userCompanyRepository.existsByUserAndCompany(1L, 100L));
        Assertions.assertFalse(this.userCompanyRepository.existsByUserAndCompany(1L, 999L));
        Assertions.assertEquals(100L, this.userCompanyRepository.findPrimaryCompanyId(1L).longValue());
        Assertions.assertTrue(this.userCompanyRepository.findCompanyIdsByUser(1L).contains(100L));
    }

    @Test
    @Order(5)
    void findAllShouldWork() {
        PaginableResult<UserCompany> all = this.userCompanyApi.findAll(null, -1, -1, null);
        Assertions.assertEquals(1, all.getResults().size());
    }

    @Test
    @Order(6)
    void removeAllShouldWork() {
        PaginableResult<UserCompany> all = this.userCompanyApi.findAll(null, -1, -1, null);
        all.getResults().forEach(entity -> this.userCompanyApi.remove(entity.getId()));
        Assertions.assertEquals(0, this.userCompanyApi.countAll(null));
    }
}
