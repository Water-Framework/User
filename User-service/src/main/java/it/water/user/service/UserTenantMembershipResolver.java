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

package it.water.user.service;

import it.water.core.api.service.integration.TenantMembershipResolver;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.user.api.UserCompanySystemApi;
import it.water.user.model.WaterUser;
import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @Author Aristide Cittadino.
 * TenantMembershipResolver for the M:N {@link WaterUser} entity. It lives in the User module (which
 * owns the {@code UserCompany} membership table) and is used only by the tenant enforcement seam in
 * the Api layer to scope user query results to the active company.
 * <p>
 * It queries membership through {@link UserCompanySystemApi} (SystemApi = permission-bypass): this is
 * internal enforcement machinery, never a request served on behalf of an end user.
 */
@FrameworkComponent(services = TenantMembershipResolver.class)
public class UserTenantMembershipResolver implements TenantMembershipResolver {

    @Inject
    @Setter
    private UserCompanySystemApi userCompanySystemApi;

    @Override
    public boolean supports(String entityResourceName) {
        return WaterUser.class.getName().equals(entityResourceName);
    }

    @Override
    public Set<Long> getEntityIdsInCompany(String entityResourceName, long companyId) {
        if (!supports(entityResourceName))
            return Collections.emptySet();
        return new HashSet<>(userCompanySystemApi.findUserIdsByCompany(companyId));
    }
}
