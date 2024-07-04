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

package it.water.user;

import it.water.core.api.notification.email.EmailContentBuilder;
import it.water.core.interceptors.annotations.FrameworkComponent;

import java.util.HashMap;

@FrameworkComponent(priority = 0)
public class FakeEmailContentBuilder implements EmailContentBuilder {
    @Override
    public String createBodyFromTemplate(String s, HashMap<String, Object> hashMap) {
        return "empty body";
    }

    @Override
    public void saveOrUpdateTemplate(String s, String s1) {
        //do nothing
    }
}
