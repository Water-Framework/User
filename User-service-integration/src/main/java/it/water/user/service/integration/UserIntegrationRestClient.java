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

package it.water.user.service.integration;

import it.water.core.api.model.User;
import it.water.core.api.service.integration.EntityRemoteIntegrationClient;
import it.water.core.api.service.integration.UserIntegrationClient;
import it.water.core.api.service.integration.discovery.DiscoverableServiceInfo;
import it.water.core.interceptors.annotations.FrameworkComponent;

@FrameworkComponent
public class UserIntegrationRestClient implements EntityRemoteIntegrationClient, UserIntegrationClient {
    //TODO: add caching mechanism

    @Override
    public void setup(DiscoverableServiceInfo discoverableServiceInfo) {

    }

    @Override
    public User fetchUserByUsername(String username) {
        //todo rest call
        return null;
    }

    @Override
    public User fetchUserByEmailAddress(String s) {
        //todo rest call
        return null;
    }

    @Override
    public User fetchUserByUserId(long l) {
        //todo rest call
        return null;
    }
}
