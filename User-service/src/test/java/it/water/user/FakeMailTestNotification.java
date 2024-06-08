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

import it.water.core.api.notification.email.EmailNotificationService;
import it.water.core.interceptors.annotations.FrameworkComponent;

import java.util.List;

@FrameworkComponent(priority = 0)
public class FakeMailTestNotification implements EmailNotificationService {
    @Override
    public String getSystemSenderName() {
        return "User test";
    }

    @Override
    public void sendMail(String s, List<String> list, List<String> list1, List<String> list2, String s1, String s2, List<byte[]> list3) {
        //not sending anything
    }

    @Override
    public void sendMail(String s, String s1, List<String> list, List<String> list1, List<String> list2, String s2, List<byte[]> list3) {
        //not sending anything
    }
}
