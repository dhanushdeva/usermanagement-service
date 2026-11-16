# usermanagement-service (local replica of bm-usermanagement-v4)

Same 15 endpoints, request/response bodies, validations, error codes and Keycloak flow as `bm-usermanagement-v4`,
but running fully on **localhost** against a **local Keycloak**, with no internal libraries
(bm-authorization, bm-logging-utils, bm-mcp, Kafka, Nexus).

## How it maps to the original

| bm-usermanagement-v4 | This project |
|---|---|
| `UserManagementController` | `controller/UserManagementController` (same code, same `@Hidden` endpoints) |
| `UserManagementService` / `UserManagementServiceImpl` | `service/UserManagementService`, `service/impl/UserManagementServiceImpl` |
| `datasource/IdentityProvider` | `datasource/IdentityProvider` |
| `KeycloakServiceProvider` | `datasource/provider/keycloak/KeycloakServiceProvider` (same methods, same flow) |
| `HttpHandler` | `util/HttpHandler` |
| `MessageUtil`, `MessageSourceConfiguration` | `util/MessageUtil`, `config/MessageSourceConfiguration` |
| `SecurityConfiguration` (`apipermission.api.*`) | `config/SecurityConfiguration` (same keys) |
| bm-authorization library (token check, public URLs, roles) | `config/WebSecurityConfiguration` + `security/KeycloakRealmRoleConverter` |
| `UMExceptionHandler`, all exception classes, `ErrorCodeConstants`, `UMErrorCodeConstants` | `exception/*` (same classes, same HTTP status per error) |
| request DTOs + `User`, `Roles`, `Attribute` | `dto/*` |
| responses (`AnonymousUser`, `RoleDetails`, ...) + `KeycloakDTO*` | `entity/*` |
| `bm.idp.keycloak.*` pointing to int-fall25 | `bm.idp.keycloak.*` pointing to `http://localhost:8080`, realm `master` |

## Endpoints (all under `/usermanagement`)

| # | Method & path | Swagger | Permission key (`apipermission.api.*`) | Success |
|---|---|---|---|---|
| 1 | POST `/anonymous` | visible | public | 200 guest token |
| 2 | POST `/register` | visible | register | 201 user + `userId` |
| 3 | POST `/updateUser` | visible | updateUser | 200 user |
| 4 | DELETE `/deleteUsers` | visible | deleteUsers | 204 |
| 5 | POST `/addRole` | visible | addRole | 201 role |
| 6 | DELETE `/deleteRole` | visible | deleteRole | 204 |
| 7 | POST `/changeUserStatus` | visible | changeUserStatus | 204 |
| 8 | GET `/retrieveAndDeleteAnonymousUser` | hidden | retrieveAndDeleteAnonymousUser | 204 (does nothing, as in original) |
| 9 | POST `/validateIdpRole` | hidden | validateIdpRole | 200 Role Found / 404 Role Not Found |
| 10 | GET `/getRoleAndComposite?roleName=` | hidden | roleAndComposite | 200 |
| 11 | GET `/getUserInformation?userId=` | hidden | userInformation | 200 |
| 12 | POST `/resendVerificationMail?userId=` | hidden | resendVerificationMail | 204 (404 UM112 when email verification is off) |
| 13 | GET `/getUsers?search=` | hidden | users | 200 list |
| 14 | GET `/getUserById?userId=` | hidden | users | 200 |
| 15 | GET `/createRoleIfNotExist?roleName=` | hidden | none (any valid token, as in original) | 200 |

"Hidden" = not shown in Swagger UI (`@Hidden`, exactly like the original), but callable (Postman).

Error body (same as original):
`{"timestamp","httpStatus","errorCode","message","details":{"errorCode","message","entity"}}`

## Secrets (never in git)

The Keycloak client secrets are not in `application.yaml`. Copy `secrets.properties.example` to
`secrets.properties` (project root, ignored by git) and fill in `UM_ADMIN_CLIENT_SECRET` and
`UM_ANONYMOUS_CLIENT_SECRET` from Keycloak (Clients > client > Credentials). Environment variables with the same
names work too and take priority. Without them the service refuses to start.

## Run locally

1. Start Keycloak on `http://localhost:8080` and create the roles, clients and test user described in KEYCLOAK_SETUP.md (realm `master`)
2. Start the service: `gradlew bootRun`, or run `UserManagementApplication` from the IDE. Port **9012**.
3. Swagger: **http**://localhost:9012/swagger-ui/index.html. Postman: import the two files in `postman/`.
4. Health: http://localhost:9012/actuator/health

The service speaks plain **http**. If the browser shows `ERR_SSL_PROTOCOL_ERROR`, it switched to https:
type the full `http://` URL; if Chrome keeps switching, open `chrome://net-internals/#hsts`, enter `localhost`
under **Delete domain security policies** and click **Delete**. `http://localhost:9012/` itself has no page;
use the Swagger URL.

Create these once in the `master` realm (steps in KEYCLOAK_SETUP.md):

| Item | Purpose |
|---|---|
| Realm role `PM_CREATE` | The permission role of every protected API (same as `apipermission.api.*` in the original) |
| Client `um-admin` | Used by the service to call the Keycloak Admin API (service account has realm-management roles and the `kc-admin-api` scope); secret in `secrets.properties` |
| Client `um-anonymous` | Used by `/anonymous`; secret in `secrets.properties` |
| Client `um-api-caller` | Use it in Postman to get a token; its secret goes into the Postman environment only |
| User `local.admin` / `admin123` | Test caller with `PM_CREATE` |

These are local development secrets; never reuse them anywhere else.

## Behaviour kept exactly as in the original (be aware when testing)

- `register` needs a `roles` array (can be empty `[]`); without it the original fails with HTTP 500.
- `register` echoes the request user, including `password`, plus the new `userId`.
- `updateUser` does not read the user first: missing `status` re-enables the user; attributes, when given,
  replace all existing attributes; `roles`, when given, removes **all** current realm roles
  (including `default-roles-master`) and assigns the new ones.
- `updateUser` with `entityType` writes it under the `entity_id` attribute (bug in the original, kept on purpose).
- `changeUserStatus` accepts only `ENABLE` / `DISABLE` in upper case.
- A new admin token is requested from Keycloak for every operation.
- Keycloak not reachable returns 500 `UM999` (only an SSL handshake failure gives `UM001`).

## Not replicated (internal-only)

Kafka, MCP, request/response logging + masking (bm-logging-utils), MDC headers `X-Transaction-Id`,
`X-Correlation-Id`, `X-Source-Channel`, `X-Tenant-Id` (you can still send them; they are ignored), Russian messages.

## Build & test

```
gradlew build     # compiles and runs the unit tests (Keycloak is mocked)
```
