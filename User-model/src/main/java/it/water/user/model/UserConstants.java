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
    public static final String USER_OPT_REGISTRATION_ENABLED = "water.user.registration.enabled";
    public static final String USER_OPT_ACTIVATION_URL = "water.user.activation.url";
    public static final String USER_OPT_PASSWORD_RESET_URL = "water.user.password.reset.url";
    public static final String USER_OPT_DEFAULT_ADMIN_PWD = "water.user.admin.default.password";
    public static final String USER_OPT_PHYSICAL_DELETION_ENABLED = "water.user.physical.deletion.enabled";
    public static final String USER_OPT_REGISTRATION_EMAIL_TEMPLATE_NAME = "water.user.registration.email.template.name";
    public static final String USER_MSG_PASSWORD_DO_NOT_MATCH = "water.user.msg.error.password.not.match";
    public static final String USER_MSG_PASSWORD_NOT_NULL = "water.user.msg.error.password.not.null";
    public static final String USER_OPT_PASSWORD_RESET_CODE_TTL_MILLIS = "water.user.password.reset.code.ttl.millis";
    public static final String USER_OPT_DELETION_CODE_TTL_MILLIS = "water.user.deletion.code.ttl.millis";
    public static final String USER_OPT_ACTIVATION_CODE_TTL_MILLIS = "water.user.activation.code.ttl.millis";
    //Framework-wide test mode flag (e.g. relaxes JWT validation). The bootstrap admin password is NOT derived
    //from this flag: set water.user.admin.default.password explicitly (see USER_OPT_DEFAULT_ADMIN_PWD) to pin it.
    public static final String WATER_TEST_MODE = "water.testMode";
}
