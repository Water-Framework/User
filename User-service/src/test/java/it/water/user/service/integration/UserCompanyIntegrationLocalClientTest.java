package it.water.user.service.integration;

import it.water.core.api.model.PaginableResult;
import it.water.core.api.repository.query.Query;
import it.water.core.api.repository.query.QueryBuilder;
import it.water.user.api.UserCompanySystemApi;
import it.water.user.model.UserCompany;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserCompanyIntegrationLocalClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void removeMembershipsByCompanyRemovesEachMembershipInIdOrderLeavingUsersUntouched() {
        UserCompanySystemApi systemApi = mock(UserCompanySystemApi.class);
        QueryBuilder queryBuilder = mock(QueryBuilder.class, RETURNS_DEEP_STUBS);
        Query query = mock(Query.class);
        PaginableResult<UserCompany> page = mock(PaginableResult.class);
        UserCompany second = membership(20L);
        UserCompany first = membership(10L);
        when(systemApi.getQueryBuilderInstance()).thenReturn(queryBuilder);
        when(queryBuilder.field(anyString()).equalTo(7L)).thenReturn(query);
        when(systemApi.findAll(query, -1, 1, null)).thenReturn(page);
        when(page.getResults()).thenReturn(List.of(second, first));
        UserCompanyIntegrationLocalClient client = new UserCompanyIntegrationLocalClient();
        client.setUserCompanySystemApi(systemApi);

        client.removeMembershipsByCompany(7L);

        InOrder order = inOrder(systemApi);
        order.verify(systemApi).remove(10L);
        order.verify(systemApi).remove(20L);
    }

    private static UserCompany membership(long id) {
        UserCompany membership = mock(UserCompany.class);
        when(membership.getId()).thenReturn(id);
        return membership;
    }
}
