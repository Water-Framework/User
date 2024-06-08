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

package it.water.user.api.options;

import it.water.core.api.service.Service;

/**
 * Properties exposed by the user module.
 */
public interface UserOptions extends Service {
    /**
     * Enable or disable user registration process
     * @return true if registration is enabled
     */
    boolean isRegistrationEnabled();

    /**
     *
     * @return the activation url of the frontend or backend services
     */
    String getUserActivationUrl();

    /**
     *
     * @return password reset url of the frontend or backend services
     */
    String getPasswordResetUrl();

    /**
     * If true when a user is removed it is phisically removed from the database.
     * Default is false, so logical deletion is enabled
     * @return
     */
    boolean isPhysicalDeletionEnabled();

    /**
     * If null the component will try to use default template for registration.
     * if specified the component will invoke the EmailNotificationService passing the template name
     * Default is NULL
     * @return
     */
    String getUserRegistrationEmailTemplateName();

}
