package it.water.user;

import it.water.core.api.bundle.Runtime;
import it.water.core.api.model.PaginableResult;
import it.water.core.api.model.User;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.repository.query.Query;
import it.water.core.api.service.Service;
import it.water.core.api.user.UserManager;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.core.testing.utils.security.TestSecurityContext;
import it.water.user.api.UserApi;
import it.water.user.api.UserCompanySystemApi;
import it.water.user.api.UserRepository;
import it.water.user.model.UserCompany;
import it.water.user.model.WaterUser;
import lombok.Setter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Multitenancy "Tassello 4" — behavioral enforcement tests for the tenant filter on
 * {@code WaterUser}, a {@code MultiTenantResource} (M:N membership via {@code UserCompany}, no
 * companyId column). See {@code the `multitenancy-knowledge` skill} &sect;1/&sect;6.
 * <p>
 * Covers:
 * <ul>
 *     <li>findAll scoped to an active company returns only the users that are members of that
 *     company (resolved via {@code UserTenantMembershipResolver} querying {@code UserCompany});</li>
 *     <li>with no active company, behaviour is unfiltered (backward compatible);</li>
 *     <li>a company with zero memberships resolves to a never-true filter (empty result), not a
 *     fail-open "show everyone".</li>
 * </ul>
 * {@code WaterUser} is NOT an {@code OwnedResource}, so there is no owner-filter interaction to
 * worry about here (unlike {@code Document}) — the tenant filter is the only enforcement in play
 * for the generic find/findAll/countAll path. There is no by-id scenario in scope for this entity
 * (not requested for User in the Tassello 4 task).
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserTenantFilterTest implements Service {

    private static final long COMPANY_A = 4100L;
    private static final long COMPANY_B = 4200L;
    //a company id that intentionally has zero UserCompany membership rows
    private static final long COMPANY_EMPTY = 4999L;

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;

    @Inject
    @Setter
    private UserApi userApi;

    @Inject
    @Setter
    private UserCompanySystemApi userCompanySystemApi;

    @Inject
    @Setter
    private UserRepository userRepository;

    @Inject
    @Setter
    private Runtime runtime;

    @Inject
    @Setter
    private UserManager userManager;

    private long adminId;
    private User u1;
    private User u2;
    private User u3;

    @BeforeAll
    void beforeAll() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        adminId = userManager.findUser("admin").getId();
        u1 = userManager.addUser("tftUser1", "name", "lastname", "tftuser1@a.com", "TempPassword1_", "salt", false);
        u2 = userManager.addUser("tftUser2", "name", "lastname", "tftuser2@a.com", "TempPassword1_", "salt", false);
        u3 = userManager.addUser("tftUser3", "name", "lastname", "tftuser3@a.com", "TempPassword1_", "salt", false);
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    /**
     * B1: seeds UserCompany membership rows directly via the SystemApi (bypasses permissions):
     * u1 and u2 in company A, u3 in company B.
     */
    @Test
    @Order(1)
    void seedMembership_viaSystemApi_bypassesPermissions() {
        userCompanySystemApi.save(new UserCompany(u1.getId(), COMPANY_A));
        userCompanySystemApi.save(new UserCompany(u2.getId(), COMPANY_A));
        userCompanySystemApi.save(new UserCompany(u3.getId(), COMPANY_B));

        Assertions.assertTrue(userCompanySystemApi.existsByUserAndCompany(u1.getId(), COMPANY_A));
        Assertions.assertTrue(userCompanySystemApi.existsByUserAndCompany(u2.getId(), COMPANY_A));
        Assertions.assertTrue(userCompanySystemApi.existsByUserAndCompany(u3.getId(), COMPANY_B));
        Assertions.assertFalse(userCompanySystemApi.existsByUserAndCompany(u3.getId(), COMPANY_A));
    }

    /**
     * B2: scoped to company A, findAll must return only u1 and u2 (members of A), not u3.
     */
    @Test
    @Order(2)
    void findAll_scopedToCompanyA_returnsOnlyMembers() {
        runtime.fillSecurityContext(TestSecurityContext.createContext(adminId, "admin", true, COMPANY_A));
        try {
            Query filter = filterOnIds(u1.getId(), u2.getId(), u3.getId());
            PaginableResult<WaterUser> result = userApi.findAll(filter, -1, -1, null);
            Set<Long> ids = idsOf(result);
            Assertions.assertTrue(ids.contains(u1.getId()), "member of the active company must be visible");
            Assertions.assertTrue(ids.contains(u2.getId()), "member of the active company must be visible");
            Assertions.assertFalse(ids.contains(u3.getId()), "non-member of the active company must NOT be visible");
        } finally {
            TestRuntimeUtils.impersonateAdmin(componentRegistry);
        }
    }

    /**
     * B3 / D: backward compatibility — with no active company, all users are returned unfiltered.
     */
    @Test
    @Order(3)
    void findAll_noActiveCompany_backwardCompatibleReturnsAll() {
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
        Query filter = filterOnIds(u1.getId(), u2.getId(), u3.getId());
        PaginableResult<WaterUser> result = userApi.findAll(filter, -1, -1, null);
        Set<Long> ids = idsOf(result);
        Assertions.assertTrue(ids.containsAll(List.of(u1.getId(), u2.getId(), u3.getId())),
                "with no active company, the tenant filter must not apply (backward compatible)");
    }

    /**
     * B4: a company with zero memberships must resolve to a never-true condition (empty result),
     * not a fail-open "everyone visible".
     */
    @Test
    @Order(4)
    void findAll_companyWithNoMembers_returnsEmpty() {
        runtime.fillSecurityContext(TestSecurityContext.createContext(adminId, "admin", true, COMPANY_EMPTY));
        try {
            Query filter = filterOnIds(u1.getId(), u2.getId(), u3.getId());
            PaginableResult<WaterUser> result = userApi.findAll(filter, -1, -1, null);
            Assertions.assertTrue(result.getResults().isEmpty(),
                    "a company with zero UserCompany memberships must resolve to a never-true filter (no users visible)");
        } finally {
            TestRuntimeUtils.impersonateAdmin(componentRegistry);
        }
    }

    private Query filterOnIds(Long... ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        return userRepository.getQueryBuilderInstance().createQueryFilter("id IN (" + sb + ")");
    }

    private Set<Long> idsOf(PaginableResult<WaterUser> result) {
        return result.getResults().stream().map(WaterUser::getId).collect(Collectors.toSet());
    }
}
