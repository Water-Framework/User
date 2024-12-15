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

package it.water.user.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserConstants {
    public static final String USER_OPT_REGISTRATION_ENABLED = "it.water.user.registration.enabled";
    public static final String USER_OPT_ACTIVATION_URL = "it.water.user.activation.url";
    public static final String USER_OPT_PASSWORD_RESET_URL = "it.water.user.password.reset.url";
    public static final String USER_OPT_DEFAULT_ADMIN_PWD = "it.water.user.admin.default.password";
    public static final String USER_OPT_PHYSICAL_DELETION_ENABLED = "it.water.user.physical.deletion.enabled";
    public static final String USER_OPT_REGISTRATION_EMAIL_TEMPLATE_NAME = "it.water.user.registration.email.template.name";
    public static final String USER_MSG_PASSWORD_DO_NOT_MATCH = "it.water.user.msg.error.password.not.match";
    public static final String USER_MSG_PASSWORD_NOT_NULL = "it.acsoftware.user.msg.error.password.not.null";
}
