# User Module — User Management

## Purpose
Manages the full lifecycle of Water Framework users: registration, activation, password management, deactivation, and deletion. Also provides `UserAuthenticationProvider` (registered with the `Authentication` module) to validate credentials at login time. Does NOT handle login or JWT generation — that is the `Authentication` module's responsibility.

## Sub-modules

| Sub-module | Runtime | Key Classes |
|---|---|---|
| `User-api` | All | `UserApi`, `UserSystemApi`, `UserRestApi`, `UserRepository`, `UserOptions`, `UserManager` |
| `User-model` | All | `WaterUser` (extends `AbstractJpaExpandableEntity`), `UserConstants`, `UserActions` |
| `User-service` | Water/OSGi | Service impl, repository, `UserAuthenticationProvider`, REST controller |
| `User-service-spring` | Spring Boot | Spring MVC REST controllers, Spring Boot app config |
| `User-service-integration` | All | `UserIntegrationRestClient` — cross-service user resolution |

## WaterUser Entity

```java
@Entity
@Table(name = "water_user")
@AccessControl(availableActions = {CrudActions.class, UserActions.class},
               rolesPermissions = {...})
public class WaterUser extends AbstractJpaExpandableEntity
    implements ProtectedEntity, OwnedResource {

    @NotNull @NoMalitiusCode
    private String name;

    @NotNull @NoMalitiusCode
    private String lastname;

    @NotNull @Column(unique = true) @NoMalitiusCode
    private String username;

    @NotNull @Column(unique = true)
    private String email;

    private String password;        // BCrypt hashed
    private String salt;            // random salt

    private boolean admin;
    private boolean active;
    private boolean deleted;

    private String imagePath;
    private String activateCode;           // UUID sent in activation email
    private String passwordResetCode;      // UUID for password reset
    private String deletionCode;           // UUID for account deletion

    @Transient
    private List<Role> roles;             // loaded at runtime from Role module
}
```

## User Lifecycle

```
register()
  │
  └─► Status: INACTIVE (active=false)
       │
       └─ activate(activateCode)
            │
            └─► Status: ACTIVE (active=true)
                 │
                 ├─ deactivate() → Status: INACTIVE
                 │
                 └─ requestDeletion(deletionCode)
                      │
                      └─ deletionConfirm(deletionCode)
                           │
                           └─► Status: DELETED (deleted=true, soft delete)
```

## Key Operations

### UserApi (permission-checked)
```java
WaterUser save(WaterUser user);                    // register new user
WaterUser update(WaterUser user);                  // update profile
WaterUser find(long id);
WaterUser findByUsername(String username);
WaterUser findByEmail(String email);
PaginatedResult<WaterUser> findAll(int delta, int page, Query filter);
void remove(long id);

// Lifecycle
void activate(String activateCode);
void deactivate(long userId);
void changePassword(long userId, String oldPwd, String newPwd);
void resetPassword(String email);
void confirmPasswordReset(String resetCode, String newPassword);
void requestDeletion(long userId);
void deletionConfirm(String deletionCode);
```

### UserSystemApi (bypasses permissions)
Same methods but callable from internal system code without a logged-in user context. Used by `Authentication` module to validate credentials.

### UserManager (cross-module integration interface)
```java
// Used by other modules to resolve/create users without depending on UserApi directly
WaterUser findUser(String username);
WaterUser findUserByEmail(String email);
WaterUser addUser(String name, String lastname, String username, String email,
                  String password, boolean admin);
```

## UserAuthenticationProvider
Registered automatically with the `Authentication` module. Validates `username` + `password`
against the `WaterUser` table via `PasswordHashService` (PBKDF2/PHC, constant-time; legacy salted
digests still supported). The verification order is:

1. resolve the user by username (`findByUsername` already excludes soft-deleted users);
2. verify the password in constant time via `PasswordHashService.matches(...)`;
3. **reject if the account is not active or is (soft-)deleted** — using the SAME generic
   "username or password incorrect!" message so no account-state oracle leaks;
4. best-effort rehash-on-login when `needsRehash(...)` is true (failure never breaks login).

```java
@FrameworkComponent(services = AuthenticationProvider.class)
public class UserAuthenticationProvider implements AuthenticationProvider {
    @Override
    public Authenticable login(String username, String password) {
        WaterUser u = userSystemApi.findByUsername(username);
        if (u == null || password == null || password.isBlank())
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);
        if (!passwordHashService.matches(password.toCharArray(), u.getPassword(), u.getSalt()))
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);
        if (!u.isActive() || u.isDeleted())            // active/deleted gate (H30)
            throw new UnauthorizedException(WRONG_USER_OR_PWD_MESSAGE);
        // ... rehash-on-login + role loading
        return u;
    }
}
```

## UserOptions — Configuration

```java
@FrameworkComponent
public class UserOptionsImpl implements UserOptions {
    // Password policy
    boolean passwordResetEnabled();      // default: true
    int passwordMinLength();             // default: 8
    boolean requireSpecialChars();       // default: false

    // Registration
    boolean registrationEnabled();       // default: true
    boolean activationRequired();        // default: true
    String activationEmailTemplate();    // FreeMarker template name
}
```

## REST Endpoints

| Method | Path | Permission |
|---|---|---|
| `POST` | `/water/users` | Public (registration) or userManager |
| `PUT` | `/water/users` | userManager / self |
| `GET` | `/water/users/{id}` | userViewer |
| `GET` | `/water/users` | userViewer |
| `DELETE` | `/water/users/{id}` | userManager |
| `PUT` | `/water/users/activate/{code}` | Public |
| `PUT` | `/water/users/changePassword/{id}` | Self |
| `PUT` | `/water/users/resetPassword` | Public |

## Default Roles and Permissions

| Role | Allowed Actions |
|---|---|
| `userManager` | SAVE, UPDATE, FIND, FIND_ALL, REMOVE, ACTIVATE, DEACTIVATE |
| `userViewer` | FIND, FIND_ALL |
| `userEditor` | UPDATE, FIND, FIND_ALL |

## Dependencies
- `it.water.repository.jpa:JpaRepository-api` — `AbstractJpaExpandableEntity`
- `it.water.core:Core-permission` — `@AccessControl`, `CrudActions`
- `it.water.authentication:Authentication-api` — `AuthenticationProvider` interface
- `it.water.role:Role-api` — `RoleManager`, `Role`
- `it.water.rest:Rest-persistence` — `BaseEntityRestApi`
- `org.springframework.security:spring-security-crypto` — BCrypt password hashing

## Testing
- Unit tests: `WaterTestExtension` — test full CRUD + activation lifecycle + permission scenarios
- REST tests: **Karate only** (never JUnit direct calls to `UserRestController`)
- Test users: create with `userManager.addUser(...)` in `@BeforeAll`, impersonate with `TestRuntimeInitializer.getInstance().impersonate(user, runtime)`
- Always restore admin after permission tests: `TestRuntimeUtils.impersonateAdmin(componentRegistry)`

## Code Generation Rules
- User integration in other modules: inject `UserManager` (not `UserApi`) to avoid circular dependencies
- Password hashing: always via `EncryptionUtil` or BCrypt — never store plain text
- `WaterUser.roles` is `@Transient` — loaded separately by `RoleManager.getUserRoles(userId)`
- Activation email: template name configurable via `UserOptions.activationEmailTemplate()`
- `UserRestController` tested **exclusively via Karate**
