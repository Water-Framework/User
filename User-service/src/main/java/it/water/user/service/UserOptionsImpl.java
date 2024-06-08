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

import it.water.core.api.bundle.ApplicationProperties;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.user.api.options.UserOptions;
import it.water.user.model.UserConstants;
import lombok.Setter;

/**
 * @Author Aristide Cittadino.
 * User options component.
 */
@FrameworkComponent
public class UserOptionsImpl implements UserOptions {

    @Inject
    @Setter
    private ApplicationProperties applicationProperties;

    @Override
    public boolean isRegistrationEnabled() {
        if (applicationProperties.getProperty(UserConstants.USER_OPT_REGISTRATION_ENABLED) != null)
            return Boolean.parseBoolean((String) applicationProperties.getProperty(UserConstants.USER_OPT_REGISTRATION_ENABLED));
        return false;
    }

    @Override
    public String getUserActivationUrl() {
        if (applicationProperties.getProperty(UserConstants.USER_OPT_ACTIVATION_URL) != null)
            return String.valueOf(applicationProperties.getProperty(UserConstants.USER_OPT_ACTIVATION_URL));
        return "localhost:8080/water/users/activation";
    }

    @Override
    public String getPasswordResetUrl() {
        if (applicationProperties.getProperty(UserConstants.USER_OPT_PASSWORD_RESET_URL) != null)
            return String.valueOf(applicationProperties.getProperty(UserConstants.USER_OPT_PASSWORD_RESET_URL));
        return "localhost:8080/water/users/password-reset";
    }

    @Override
    public boolean isPhysicalDeletionEnabled() {
        if (applicationProperties.getProperty(UserConstants.USER_OPT_PHYSICAL_DELETION_ENABLED) != null)
            return Boolean.parseBoolean((String) applicationProperties.getProperty(UserConstants.USER_OPT_PHYSICAL_DELETION_ENABLED));
        return false;
    }

    @Override
    public String getUserRegistrationEmailTemplateName() {
        if (applicationProperties.getProperty(UserConstants.USER_OPT_REGISTRATION_EMAIL_TEMPLATE_NAME) != null)
            return (String) applicationProperties.getProperty(UserConstants.USER_OPT_REGISTRATION_EMAIL_TEMPLATE_NAME);
        return null;
    }
}
