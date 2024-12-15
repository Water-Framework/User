package it.water.user;

import it.water.core.api.bundle.ApplicationProperties;
import it.water.core.api.bundle.Runtime;
import it.water.core.api.model.PaginableResult;
import it.water.core.api.model.Role;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.repository.query.Query;
import it.water.core.api.role.RoleManager;
import it.water.core.api.security.EncryptionUtil;
import it.water.core.api.service.Service;
import it.water.core.api.service.integration.UserIntegrationClient;
import it.water.core.api.user.UserManager;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.model.exceptions.ValidationException;
import it.water.core.model.exceptions.WaterRuntimeException;
import it.water.core.permission.action.UserActions;
import it.water.core.permission.exceptions.UnauthorizedException;
import it.water.core.testing.utils.bundle.TestRuntimeInitializer;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.runtime.TestRuntimeUtils;
import it.water.repository.entity.model.exceptions.DuplicateEntityException;
import it.water.repository.entity.model.exceptions.EntityNotFound;
import it.water.user.api.UserApi;
import it.water.user.api.UserRepository;
import it.water.user.api.UserSystemApi;
import it.water.user.api.options.UserOptions;
import it.water.user.model.UserConstants;
import it.water.user.model.WaterUser;
import lombok.Setter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Properties;
import java.util.UUID;

import static it.water.core.testing.utils.runtime.TestRuntimeUtils.*;

@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserApiTest implements Service {

    @Inject
    @Setter
    private ComponentRegistry componentRegistry;
    @Inject
    @Setter
    private EncryptionUtil encryptionUtil;
    @Inject
    @Setter
    private UserApi userApi;
    @Inject
    @Setter
    private UserSystemApi userSystemApi;
    @Inject
    @Setter
    private UserIntegrationClient userIntegrationClient;
    @Inject
    @Setter
    private UserRepository userRepository;
    @Inject
    @Setter
    //test role manager
    private RoleManager roleManager;
    @Inject
    @Setter
    private UserManager userManager;
    @Inject
    @Setter
    private Runtime runtime;
    private it.water.core.api.model.User adminUser;
    private it.water.core.api.model.User managerUser;
    private it.water.core.api.model.User viewerUser;
    private it.water.core.api.model.User editorUser;
    private Role manager;
    private Role viewer;
    private Role editor;

    @BeforeAll
    void beforeAll() {
        //getting user
        manager = roleManager.getRole("userManager");
        viewer = roleManager.getRole("userViewer");
        editor = roleManager.getRole("userEditor");
        Assertions.assertNotNull(manager);
        Assertions.assertNotNull(viewer);
        Assertions.assertNotNull(editor);
        //impersonate admin so we can test the happy path
        adminUser = userManager.findUser("admin");
        managerUser = userManager.addUser("manager", "name", "lastname", "manager@a.com", "Password1_.", "PasswordSalt", false);
        viewerUser = userManager.addUser("viewer", "name", "lastname", "viewer@a.com", "Password1_.", "PasswordSalt", false);
        editorUser = userManager.addUser("editor", "name", "lastname", "editor@a.com", "Password1_.", "PasswordSalt", false);
        //starting with admin permissions
        roleManager.addRole(managerUser.getId(), manager);
        roleManager.addRole(viewerUser.getId(), viewer);
        roleManager.addRole(editorUser.getId(), editor);
        //starting with admin
        TestRuntimeUtils.impersonateAdmin(componentRegistry);
    }

    @Test
    @Order(1)
    void componentsInsantiatedCorrectly() {
        this.userApi = this.componentRegistry.findComponent(UserApi.class, null);
        this.userRepository = this.componentRegistry.findComponent(UserRepository.class, null);
        Assertions.assertNotNull(this.userApi);
        Assertions.assertNotNull(this.componentRegistry.findComponent(UserSystemApi.class, null));
        Assertions.assertNotNull(userRepository);
    }

    /**
     * Testing saving logic, basic test
     */
    @Test
    @Order(2)
    void saveShouldWork() {
        WaterUser u = createUser(0);
        u = this.userApi.save(u);
        Assertions.assertNull(userRepository.findByUsername("NotExistingUsername"));
        Assertions.assertNotNull(userIntegrationClient.fetchUserByUserId(u.getId()));
        Assertions.assertNotNull(userIntegrationClient.fetchUserByUsername(u.getUsername()));
        Assertions.assertNotNull(userIntegrationClient.fetchUserByEmailAddress(u.getEmail()));
        Assertions.assertEquals(1, u.getEntityVersion());
        Assertions.assertTrue(u.getId() > 0);
        Assertions.assertEquals("username0", u.getUsername());
    }

    /**
     * Testing update logic, basic test
     */
    @Test
    @Order(3)
    void updateShouldWork() {
        Query q = this.userRepository.getQueryBuilderInstance().createQueryFilter("username=username0");
        WaterUser u = this.userApi.find(q);
        Assertions.assertNotNull(u);
        u.setActivateCode("123");
        u = this.userApi.update(u);
        Assertions.assertEquals("123", u.getActivateCode());
        Assertions.assertEquals(2, u.getEntityVersion());
    }

    /**
     * Testing update logic, basic test
     */
    @Test
    @Order(4)
    void updateShouldFailWithWrongVersion() {
        Query q = this.userRepository.getQueryBuilderInstance().createQueryFilter("username=username0");
        final WaterUser errorEntity = this.userApi.find(q);
        Assertions.assertEquals("username0", errorEntity.getUsername());
        Assertions.assertEquals(2, errorEntity.getEntityVersion());
        errorEntity.setEntityVersion(1);
        Assertions.assertThrows(WaterRuntimeException.class, () -> this.userApi.update(errorEntity));
    }

    /**
     * Testing finding all entries with no pagination
     */
    @Test
    @Order(5)
    void findAllShouldWork() {
        PaginableResult<WaterUser> all = this.userApi.findAll(null, -1, -1, null);
        //there's one more user created automatically , the admin
        Assertions.assertEquals(5, all.getResults().size());
    }

    /**
     * Testing finding all entries with settings related to pagination.
     * Searching with 5 items per page starting from page 1.
     */
    @Test
    @Order(6)
    void findAllPaginatedShouldWork() {
        for (int i = 2; i < 11; i++) {
            WaterUser u = createUser(i);
            this.userApi.save(u);
        }
        PaginableResult<WaterUser> paginated = this.userApi.findAll(null, 7, 1, null);
        Assertions.assertEquals(7, paginated.getResults().size());
        Assertions.assertEquals(1, paginated.getCurrentPage());
        Assertions.assertEquals(2, paginated.getNextPage());
        paginated = this.userApi.findAll(null, 7, 2, null);
        //there's one more user since there's admin, who is automatically created
        Assertions.assertEquals(7, paginated.getResults().size());
        Assertions.assertEquals(2, paginated.getCurrentPage());
        Assertions.assertEquals(1, paginated.getNextPage());
    }

    @Test
    @Order(7)
    void removeAllShouldWork() {
        //removing everything but admin
        Query notRemoveAdmin = this.userRepository.getQueryBuilderInstance().field("admin").equalTo(true).not();
        Query notRemoveViewer = this.userRepository.getQueryBuilderInstance().field("username").equalTo("viewer").not();
        Query notRemoveManager = this.userRepository.getQueryBuilderInstance().field("username").equalTo("manager").not();
        Query notRemoveEditor = this.userRepository.getQueryBuilderInstance().field("username").equalTo("editor").not();
        Query q = notRemoveAdmin.and(notRemoveViewer).and(notRemoveManager).and(notRemoveEditor);
        PaginableResult<WaterUser> paginated = this.userApi.findAll(q, -1, -1, null);
        paginated.getResults().forEach(user -> {
            this.userApi.remove(user.getId());
        });
        Assertions.assertEquals(4, this.userApi.countAll(null));
    }

    /**
     * Testing failure on duplicated entity
     */
    @Test
    @Order(8)
    void saveShouldFailOnDuplicatedEntity() {
        String salt = new String(encryptionUtil.generate16BytesSalt());
        WaterUser u = new WaterUser("user", "user", "user", "user1S7.", salt, false, "userY@mail.com");
        this.userApi.save(u);
        WaterUser duplicated = new WaterUser("user", "user", "user", "user1S7.", salt, false, "userY@mail.com");
        //cannot insert new entity wich breaks unique constraint
        Assertions.assertThrows(DuplicateEntityException.class, () -> this.userApi.save(duplicated));
        WaterUser second = new WaterUser("user2", "user2", "user2", "user1S7.", salt, false, "userX@mail.com");
        this.userApi.save(second);
        u.updateAccountInfo("user2", "user2", "userX@mail.com", "user");
        //cannot update an entity colliding with other entity on unique constraint
        Assertions.assertThrows(DuplicateEntityException.class, () -> this.userApi.update(u));
    }

    /**
     * Testing failure on validation failure for example code injection
     */
    @Test
    @Order(9)
    void updateShouldFailOnValidationFailure() {
        String salt = new String(encryptionUtil.generate16BytesSalt());
        WaterUser newUser = new WaterUser("<script>function(){alert('ciao')!}</script>", "lastname", "username", "Password1._", salt, false, "mail@mail.com");
        Assertions.assertThrows(ValidationException.class, () -> this.userApi.save(newUser));
    }

    /**
     * Testing a save,update, find and delete request with a user who does not have "SAVE" permission
     */
    @Order(10)
    @Test
    void managerCanDoEverything() {
        TestRuntimeInitializer.getInstance().impersonate(managerUser, runtime);
        final WaterUser entity = createUser(301);
        WaterUser savedEntity = Assertions.assertDoesNotThrow(() -> this.userApi.save(entity));
        savedEntity.setActivateCode("newSavedEntity");
        Assertions.assertDoesNotThrow(() -> this.userApi.update(entity));
        long savedEntityId = savedEntity.getId();
        Assertions.assertDoesNotThrow(() -> this.userApi.find(savedEntityId));
        Assertions.assertDoesNotThrow(() -> this.userApi.remove(savedEntityId));
    }

    @Order(11)
    @Test
    void viewerCannotSaveOrUpdateOrRemove() {
        TestRuntimeInitializer.getInstance().impersonate(viewerUser, runtime);
        final WaterUser entity = createUser(201);
        Assertions.assertThrows(UnauthorizedException.class, () -> this.userApi.save(entity));
        //viewer can search
        WaterUser found = Assertions.assertDoesNotThrow(() -> this.userApi.findAll(null, -1, -1, null).getResults().stream().findFirst()).get();
        Assertions.assertDoesNotThrow(() -> this.userApi.find(found.getId()));
        //viewer cannot update or remove
        found.setActivateCode("changeIt!");
        long foundEntityId = found.getId();
        Assertions.assertThrows(UnauthorizedException.class, () -> this.userApi.update(entity));
        Assertions.assertThrows(UnauthorizedException.class, () -> this.userApi.remove(foundEntityId));
    }

    @Order(12)
    @Test
    void editorCannotRemove() {
        TestRuntimeInitializer.getInstance().impersonate(editorUser, runtime);
        final WaterUser entity = createUser(101);
        WaterUser savedEntity = Assertions.assertDoesNotThrow(() -> this.userApi.save(entity));
        savedEntity.setActivateCode("newSavedEntity");
        Assertions.assertDoesNotThrow(() -> this.userApi.update(entity));
        long savedEntityId = savedEntity.getId();
        Assertions.assertDoesNotThrow(() -> this.userApi.find(savedEntityId));
        Assertions.assertThrows(UnauthorizedException.class, () -> this.userApi.remove(savedEntityId));
    }

    @Test
    @Order(13)
    void userRegistrationSuccess() {
        // the following call register a new HUser
        // response status code '200'
        runtime.fillSecurityContext(null);
        WaterUser registeredUser = createUser(102);
        Assertions.assertNull(registeredUser.getActivateCode());
        Assertions.assertNull(registeredUser.getPasswordResetCode());
        userApi.register(registeredUser);
        Assertions.assertTrue(registeredUser.getId() > 0);
        Assertions.assertFalse(registeredUser.isActive());
        Assertions.assertNotNull(registeredUser.getActivateCode());
        Assertions.assertNull(registeredUser.getPasswordResetCode());
        //unregistering user
        runAs(registeredUser, () -> userApi.unregisterAccountRequest());
        String deletionCode = getAs(adminUser, () -> userApi.findByUsername(registeredUser.getUsername()).getDeletionCode());
        runAs(registeredUser, () -> userApi.unregister(registeredUser.getEmail(), deletionCode));
        Assertions.assertEquals(1, getAs(adminUser, () -> userApi.findAllDeleted(10, 1, null, null).getResults().size()));
    }

    @Test
    @Order(14)
    void userRegistrationValidationFailures() {
        runtime.fillSecurityContext(null);
        WaterUser registeredUser = createUser(102);
        //username validation
        registeredUser.updateAccountInfo("name", "lastname", "email@email.com", "username&&&&");
        assertValidationException(() -> userApi.register(registeredUser), "username");
        registeredUser.updateAccountInfo("name", "lastname", "email@email.com", null);
        assertValidationException(() -> userApi.register(registeredUser), "username");
        registeredUser.updateAccountInfo("name", "lastname", "email@email.com", "");
        assertValidationException(() -> userApi.register(registeredUser), "username");
        registeredUser.updateAccountInfo("name", "lastname", "email@email.com", "<script>console.log()</script>");
        assertValidationException(() -> userApi.register(registeredUser), "username");
        //email validation
        registeredUser.updateAccountInfo("name", "lastname", "wrongMail", "username");
        assertValidationException(() -> userApi.register(registeredUser), "email");
        registeredUser.updateAccountInfo("name", "lastname", "<script>console.log()</script>", "username");
        assertValidationException(() -> userApi.register(registeredUser), "email");
        registeredUser.updateAccountInfo("name", "lastname", null, "username");
        assertValidationException(() -> userApi.register(registeredUser), "email");
        registeredUser.updateAccountInfo("name", "lastname", "", "username");
        assertValidationException(() -> userApi.register(registeredUser), "email");
        //restoring email value
        registeredUser.updateAccountInfo("name", "lastname", "email@email.com", "username");
        byte[] salt = encryptionUtil.generate16BytesSalt();
        //password validation
        registeredUser.updatePassword(salt, "malfo", "malfo");
        assertValidationException(() -> userApi.register(registeredUser), "password", "passwordConfirm");
        registeredUser.updatePassword(salt, "<script>console.log()</script>", "<script>console.log()</script>");
        assertValidationException(() -> userApi.register(registeredUser), "password", "passwordConfirm");
        Assertions.assertThrows(ValidationException.class, () -> registeredUser.updatePassword(salt, null, null));
        Assertions.assertThrows(ValidationException.class, () -> registeredUser.updatePassword(salt, "", ""));
        assertValidationException(() -> userApi.register(registeredUser), "password", "passwordConfirm");
        Assertions.assertThrows(ValidationException.class, () -> registeredUser.updatePassword(salt, "CorrectPassword1_.", "WrongPassword1_."));
        assertValidationException(() -> userApi.register(registeredUser), "password", "passwordConfirm");
        Assertions.assertThrows(ValidationException.class, () -> registeredUser.updatePassword(salt, "CorrectPassword1_.", ""));
        assertValidationException(() -> userApi.register(registeredUser), "password", "passwordConfirm");
    }

    @Test
    @Order(15)
    void userActivation() {
        final WaterUser registeredUser = createUser(203);
        // Activate  huser
        Assertions.assertFalse(registeredUser.isActive());
        userApi.register(registeredUser);
        String activationCode = registeredUser.getActivateCode();
        String wrongActivationCode = "wrongActivationCode";
        String userEmail = registeredUser.getEmail();
        Assertions.assertThrows(UnauthorizedException.class, () -> userApi.activate(userEmail, wrongActivationCode));
        Assertions.assertFalse(registeredUser.isActive());
        userApi.activate(registeredUser.getEmail(), activationCode);
        //Checking admin functions
        TestRuntimeInitializer.getInstance().impersonate(adminUser, runtime);
        WaterUser toDeactivateUser = userApi.find(registeredUser.getId());
        Assertions.assertTrue(toDeactivateUser.isActive());
        userApi.deactivate(toDeactivateUser.getId());
        WaterUser toActivateUser = userApi.find(registeredUser.getId());
        final String activateUserEmail = toActivateUser.getEmail();
        final String activatedUserUsername = toActivateUser.getUsername();
        Assertions.assertFalse(toActivateUser.isActive());
        userApi.activate(toActivateUser.getId());
        WaterUser activatedUser = userApi.find(registeredUser.getId());
        Assertions.assertTrue(activatedUser.isActive());
        runtime.fillSecurityContext(null);
        //email not found, nothing happens for security purpose
        Assertions.assertDoesNotThrow(() -> userApi.activate("wrongMail@wrong.com", wrongActivationCode));
        //already activated
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.activate(activateUserEmail, activationCode));
        //starting deletion request
        runAs(activatedUser, () -> userApi.unregisterAccountRequest());
        String deletionCode = getAs(adminUser, () -> userApi.findByUsername(activatedUserUsername).getDeletionCode());
        Assertions.assertNotNull(deletionCode);
        Assertions.assertDoesNotThrow(() -> runAs(activatedUser, () -> userApi.unregister(activateUserEmail, deletionCode)));
    }

    @Test
    @Order(16)
    void resetPassword() {
        final WaterUser registeredUser = createUser(204);
        // Activate  huser
        userApi.register(registeredUser);
        String activationCode = registeredUser.getActivateCode();
        userApi.activate(registeredUser.getEmail(), activationCode);
        userApi.passwordResetRequest(registeredUser.getEmail());
        WaterUser activatedUser = getAs(adminUser, () -> userApi.find(registeredUser.getId()));
        String passwordResetCode = activatedUser.getPasswordResetCode();
        Assertions.assertNotNull(passwordResetCode);
        String newPassword = "newPassword@1_";
        String registeredUserEmail = registeredUser.getEmail();
        //wrong code
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.resetPassword(registeredUserEmail, "wrongPwdResetCode", newPassword, newPassword));
        //null code
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.resetPassword(registeredUserEmail, null, newPassword, newPassword));
        //empty code
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.resetPassword(registeredUserEmail, "", newPassword, newPassword));
        //Malitious code
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.resetPassword(registeredUserEmail, "<script>console.log()</script>", newPassword, newPassword));
        //New password is null
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.resetPassword(registeredUserEmail, passwordResetCode, null, null));
        //New password is empty
        Assertions.assertThrows(ValidationException.class, () -> userApi.resetPassword(registeredUserEmail, passwordResetCode, "", ""));
        //Password does not match
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.resetPassword(registeredUserEmail, passwordResetCode, newPassword, newPassword + "!23"));
        //Password confirm is null
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.resetPassword(registeredUserEmail, passwordResetCode, newPassword, null));
        //Password insecure
        Assertions.assertThrows(ValidationException.class, () -> userApi.resetPassword(registeredUserEmail, passwordResetCode, "newpassword", "newpassword"));
        //New password is malitious code
        Assertions.assertThrows(ValidationException.class, () -> userApi.resetPassword(registeredUserEmail, passwordResetCode, "<script>console.log()</script>", "<script>console.log()</script>"));
        //success
        Assertions.assertDoesNotThrow(() -> userApi.resetPassword(registeredUserEmail, passwordResetCode, newPassword, newPassword));
        //password should not be the same because the system saves the hash
        String password = getAs(adminUser, () -> userApi.find(registeredUser.getId()).getPassword());
        Assertions.assertNotEquals(newPassword, password);
        //fail because already reset without a new request
        Assertions.assertThrows(UnauthorizedException.class, () -> userApi.resetPassword(registeredUserEmail, passwordResetCode, newPassword, newPassword));
        //Wrong email for pwdRequest
        Assertions.assertThrows(EntityNotFound.class, () -> userApi.passwordResetRequest("wrongResetPwd@mail.com"));
    }

    @Test
    @Order(17)
    void changePassword() {
        final WaterUser registeredUser = createUser(304);
        runAs(adminUser, () -> userApi.save(registeredUser));
        final WaterUser attackerUser = createUser(404);
        runAs(adminUser, () -> userApi.save(attackerUser));
        String newPassword = "newPassw0rd._";
        TestRuntimeInitializer.getInstance().impersonate(registeredUser, runtime);
        userApi.changePassword(registeredUser.getId(), registeredUser.getPassword(), newPassword, newPassword);
        Assertions.assertEquals(registeredUser.getPassword(), newPassword);
        String registeredUserPassword = registeredUser.getPassword();
        long registeredUserId = registeredUser.getId();
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.changePassword(-1, registeredUserPassword, newPassword, newPassword));
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.changePassword(registeredUserId, registeredUserPassword, newPassword, newPassword + "-wrong"));
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.changePassword(registeredUserId, null, newPassword, newPassword + "-wrong"));
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.changePassword(registeredUserId, "", newPassword, newPassword + "-wrong"));
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.changePassword(registeredUserId, "<script>console.log()</script>", newPassword, newPassword + "-wrong"));
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.changePassword(registeredUserId, registeredUserPassword, null, null));
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.changePassword(registeredUserId, registeredUserPassword, "", ""));
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.changePassword(registeredUserId, registeredUserPassword, "<script>console.log()</script>", "<script>console.log()</script>"));
        runtime.fillSecurityContext(null);
        TestRuntimeInitializer.getInstance().impersonate(attackerUser, runtime);
        //trying to change password of another user
        Assertions.assertThrows(WaterRuntimeException.class, () -> userApi.changePassword(registeredUserId, registeredUserPassword, newPassword, newPassword));
        runtime.fillSecurityContext(null);
    }

    @Test
    @Order(18)
    void changeAccountInfo() {
        final WaterUser user = createUser(504);
        runAs(adminUser, () -> userApi.save(user));
        user.updateAccountInfo("newName", "newLastName", user.getEmail(), user.getUsername());
        runAs(user, () -> userApi.updateAccountInfo(user));
        Assertions.assertEquals("newName", getAs(adminUser, () -> userApi.findByUsername(user.getUsername()).getName()));
        Assertions.assertEquals("newLastName", getAs(adminUser, () -> userApi.findByUsername(user.getUsername()).getLastname()));
    }

    @Test
    @Order(19)
    void assertConstants() {
        Assertions.assertEquals("it.water.user.activation.url", UserConstants.USER_OPT_ACTIVATION_URL);
        Assertions.assertEquals("it.water.user.registration.enabled", UserConstants.USER_OPT_REGISTRATION_ENABLED);
        Assertions.assertEquals("it.water.user.msg.error.password.not.match", UserConstants.USER_MSG_PASSWORD_DO_NOT_MATCH);
        Assertions.assertEquals("it.acsoftware.user.msg.error.password.not.null", UserConstants.USER_MSG_PASSWORD_NOT_NULL);
        Assertions.assertEquals("it.water.user.password.reset.url", UserConstants.USER_OPT_PASSWORD_RESET_URL);
        Assertions.assertEquals("it.water.user.physical.deletion.enabled", UserConstants.USER_OPT_PHYSICAL_DELETION_ENABLED);
        Assertions.assertEquals("it.water.user.registration.email.template.name", UserConstants.USER_OPT_REGISTRATION_EMAIL_TEMPLATE_NAME);
    }

    @Test
    @Order(20)
    void assertActions() {
        Assertions.assertEquals("activate", UserActions.ACTIVATE);
        Assertions.assertEquals("deactivate", UserActions.DEACTIVATE);
        Assertions.assertEquals("impersonate", UserActions.IMPERSONATE);

    }

    @Test
    @Order(21)
    void assertDefaultPropertiesValues() {
        ApplicationProperties appProps = componentRegistry.findComponent(ApplicationProperties.class, null);
        UserOptions userOpts = componentRegistry.findComponent(UserOptions.class, null);
        Properties props = new Properties();
        props.put(UserConstants.USER_OPT_DEFAULT_ADMIN_PWD, "admin");
        props.put(UserConstants.USER_OPT_REGISTRATION_ENABLED, "false");
        props.put(UserConstants.USER_OPT_ACTIVATION_URL, "activationUrl");
        props.put(UserConstants.USER_OPT_REGISTRATION_EMAIL_TEMPLATE_NAME, "registrationTemplate");
        props.put(UserConstants.USER_OPT_PASSWORD_RESET_URL, "pwdRestUrl");
        props.put(UserConstants.USER_OPT_PHYSICAL_DELETION_ENABLED, "true");
        appProps.loadProperties(props);
        Assertions.assertEquals("admin", appProps.getProperty(UserConstants.USER_OPT_DEFAULT_ADMIN_PWD));
        Assertions.assertFalse(userOpts.isRegistrationEnabled());
        Assertions.assertTrue(userOpts.isPhysicalDeletionEnabled());
        Assertions.assertEquals("activationUrl", userOpts.getUserActivationUrl());
        Assertions.assertEquals("registrationTemplate", userOpts.getUserRegistrationEmailTemplateName());
        Assertions.assertEquals("pwdRestUrl", userOpts.getPasswordResetUrl());
        //enabling registration for further tests
        WaterUser user = createUser(504);
        Assertions.assertThrows(UnauthorizedException.class, () -> userSystemApi.register(user));
        Assertions.assertThrows(UnauthorizedException.class, () -> userSystemApi.activateUser("temp@mail.com", "activationCode"));
        Assertions.assertThrows(UnauthorizedException.class, () -> userSystemApi.activateUser(101));
        props.put(UserConstants.USER_OPT_REGISTRATION_ENABLED, "true");
        appProps.loadProperties(props);
        user.setPasswordConfirm("wrongOne");
        Assertions.assertThrows(ValidationException.class, () -> userSystemApi.register(user));
    }


    private WaterUser createUser(int seed) {
        String salt = new String(encryptionUtil.generate16BytesSalt());
        WaterUser user = new WaterUser("name" + seed, "lastname" + seed, "username" + seed, "Password_" + seed, salt, false, "mail" + UUID.randomUUID() + "@mail.com");
        user.setPasswordConfirm(user.getPassword());
        return user;
    }

}
