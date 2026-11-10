# usermanagement-service

A standalone local replica of `bm-usermanagement-v4`. It exposes the same `/usermanagement/*` REST API and manages users and realm roles in **your own local Keycloak** (Docker).

It uses no internal libraries: there is no bm-authorization, bm-logging-utils, bm-mcp, internal BOMs or Nexus.

| Original (internal) | Replaced with |
|---|---|
| bm-authorization (`@Authorize`, permission JSON) | Spring Security OAuth2 resource server + `@PreAuthorize("@apiAccess.isAllowed('<api>')")`, with roles configured in `app.security.api-roles` |
| bm-logging-utils | Plain SLF4J / Logback (Spring Boot default) |
| keycloak-admin-client library | `KeycloakAdminClient`, a small wrapper around the Keycloak Admin REST API using Spring `RestClient` |
| dev/test environment URLs | `localhost` only |

## Project structure

```
src/main/java/com/usermanagement
├── UserManagementApplication.java      entry point
├── config/                             KeycloakProperties, SecurityProperties, SecurityConfig, KeycloakClientConfig, OpenApiConfig
├── controller/UserManagementController REST endpoints (/usermanagement/*)
├── identityprovider/
│   ├── IdentityProvider                interface (swap Keycloak for another IdP here)
│   └── keycloak/
│       ├── KeycloakIdentityProvider    user/role logic (register, update, role diff...)
│       ├── KeycloakAdminClient         HTTP calls to the Keycloak Admin API + cached admin token
│       ├── KeycloakErrorTranslator     Keycloak HTTP errors -> ErrorCode
├── security/                           KeycloakRealmRoleConverter (realm roles -> ROLE_*), ApiAccessChecker ("apiAccess" bean)
├── dto/                                request payloads (UserRequest, UserDto, ...)
├── entity/                             response objects (AnonymousUserResponse, ErrorResponse) + Keycloak representations
├── service/ + service/impl/            business interface + implementation
├── constants/UserStatus                ENABLE / DISABLE
└── exception/                          ErrorCode (UMxxx), UserManagementException, GlobalExceptionHandler
src/main/resources
├── application.yaml                    all settings (env-overridable)
└── messages/messages.properties        error messages per code
docker/keycloak/realm-import/           realm imported automatically on Keycloak start
docker-compose.yml, Dockerfile
```

## Run locally

Prerequisites: Java 21 and a Keycloak on http://localhost:8080 (local install or Docker; Docker is optional - the Dockerfile/docker-compose.yml are only a convenience, there is no Jenkins/Nexus publishing).

For manual Keycloak setup, which values to copy into `application.yaml`, and why, see **[KEYCLOAK_SETUP.md](KEYCLOAK_SETUP.md)**.

```powershell
# 1. Start Keycloak (+ Postgres). The realm "usermanagement-local" is imported automatically.
docker compose up -d
# Keycloak admin console: http://localhost:8080  (admin / admin)

# 2. Start the service on http://localhost:9012
.\gradlew.bat bootRun

# Swagger UI: http://localhost:9012/swagger-ui/index.html
```

To run the service in Docker as well:

```powershell
.\gradlew.bat bootJar
docker compose --profile app up -d --build
```

### What the realm import creates

| Item | Purpose |
|---|---|
| Realm roles `USER_MANAGEMENT_ADMIN`, `ANONYMOUS_USER`, `CUSTOMER` | API access role, guest role, sample role |
| Client `um-admin` (secret `um-admin-local-secret`) | The service's own client for calling the Admin API. Its service account has the realm-management roles `manage-users`, `view-users`, `query-users`, `view-realm` and `manage-realm` |
| Client `um-anonymous` (secret `um-anonymous-local-secret`) | Issues guest tokens for `/anonymous` |
| Client `um-api-caller` (secret `um-api-caller-local-secret`) | Use it from curl/Postman; its service account has `USER_MANAGEMENT_ADMIN` |
| User `local.admin` / `admin123` | Test user with `USER_MANAGEMENT_ADMIN` (password grant through `um-api-caller`) |
| User profile: unmanaged attributes **Enabled** | Required (Keycloak 24+) to store `entity_type`, `entity_id` and custom attributes |

If you create the realm by hand instead, set **Realm settings → General → Unmanaged attributes → Enabled**.

These secrets are local development placeholders. Never reuse them in a real environment.

## Try it (PowerShell)

```powershell
# Token for calling the API
$token = (Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/realms/usermanagement-local/protocol/openid-connect/token `
  -Body @{ grant_type='client_credentials'; client_id='um-api-caller'; client_secret='um-api-caller-local-secret' }).access_token
$h = @{ Authorization = "Bearer $token" }

# Register a user
$body = '{"user":{"userName":"john","password":"Pass@123","email":"john@example.com","firstName":"John","lastName":"Doe","entityType":"CUSTOMER","entityId":"C-1","roles":["CUSTOMER"]}}'
$user = Invoke-RestMethod -Method Post -Uri http://localhost:9012/usermanagement/register -Headers $h -ContentType 'application/json' -Body $body


# Public guest token (no auth)
Invoke-RestMethod -Method Post http://localhost:9012/usermanagement/anonymous
```

## Endpoints

All paths are under `/usermanagement`. Every endpoint except `/anonymous` needs a Bearer token whose realm roles match `app.security.api-roles.<key>`.

| Method & path | api-roles key | Success |
|---|---|---|
| POST `/anonymous` | public | 200 guest token |
| POST `/register` `{"user":{...}}` | register-user | 201 user (with `userId`, without password) |
| POST `/updateUser` `{"user":{"userId":..}}` | update-user | 200 updated user |
| DELETE `/deleteUsers` `{"userIds":[..]}` | delete-users | 204 |
| POST `/changeUserStatus` `{"userId","status":"ENABLE\|DISABLE"}` | change-user-status | 204 |
| POST `/addRole` `{"roles":{"name","description"}}` | create-role | 201 |
| DELETE `/deleteRole` `{"roleName":".."}` | delete-role | 204 |

Errors are returned as `{timestamp,status,errorCode,message,details}`. The codes are defined in `ErrorCode` and the messages in `messages.properties`.

## Configuration (`application.yaml`)

| Property | Env variable | Default |
|---|---|---|
| `server.port` | `SERVER_PORT` | 9012 |
| `keycloak.server-url` | `KEYCLOAK_URL` | http://localhost:8080 |
| `keycloak.realm` | `KEYCLOAK_REALM` | usermanagement-local |
| `keycloak.admin-client.*` | `KEYCLOAK_ADMIN_CLIENT_ID/SECRET` | um-admin |
| `keycloak.anonymous-client.*` | `KEYCLOAK_ANONYMOUS_CLIENT_ID/SECRET` | um-anonymous |
| `keycloak.temporary-password` | | false |
| `keycloak.email-verification.enabled` | | false (true needs SMTP configured in the realm) |
| `app.security.public-urls` | | anonymous, swagger, health |
| `app.security.api-roles.<key>` | | `[USER_MANAGEMENT_ADMIN]` |

## Differences from the original (intentional)

- **More accurate HTTP statuses:** "already exists" returns 409, "Keycloak unreachable" returns 503, and "forbidden" returns 403 (UM112).
- **Separate SMTP error code:** "email verification disabled" now uses its own code, UM113. In the original it collided with UM112.
- **Password never echoed:** the password is never returned in a response.
- **Cached admin token:** the admin token is reused until it is close to expiry.
- **`updateUser` fixes:**
  - It merges attributes instead of overwriting them, which fixes the `entity_type` bug.
  - It no longer re-enables disabled users.
  - When it replaces roles, it keeps `default-roles-*`.
- **Unimplemented endpoints:** `/refresh`, `/serviceAccountToken`, `/offline` and the mock IdP are documented in the original README but were never implemented there, so they are not implemented here either.

## Build & test

```powershell
.\gradlew.bat build      # compiles + runs unit tests (Keycloak is mocked; no Docker needed)
```
