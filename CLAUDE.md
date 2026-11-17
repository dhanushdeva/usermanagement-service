# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A local replica of the company service `bm-usermanagement-v4` (real source, if available, at `C:\team8\bm-usermanagement-v4`).
Same 15 REST endpoints, validations and error codes, but it runs on localhost against a **local Keycloak** and uses **no
internal libraries** (no bm-authorization, bm-logging-utils, bm-mcp, Kafka, Nexus, Docker, Jenkins). Spring Boot 4.1.1,
Java 21, Gradle wrapper, Lombok, springdoc-openapi 3, Jackson 3 (`tools.jackson`). `README.md` has the endpoint table and
the list of original quirks that are kept on purpose; `KEYCLOAK_SETUP.md` has the one-time Keycloak setup.

## Commands (Windows, run from the project root)

```
gradlew.bat build                 # compile + all unit tests (Keycloak is mocked, no server needed)
gradlew.bat test                  # tests only
gradlew.bat test --tests "com.usermanagement.controller.UserManagementControllerTest.registerWithoutEmailReturnsUM113"
gradlew.bat bootRun               # runs on port 9012
gradlew.bat bootRun --args=--server.port=9013   # second instance, e.g. while the IDE already holds 9012
```

- Swagger: `http://localhost:9012/swagger-ui/index.html` (plain **http**); health: `/actuator/health`.
- If the Gradle wrapper must download behind the corporate proxy, add `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT`.
- `Get-NetTCPConnection` is unreliable in this environment; use `netstat -ano | Select-String ":9012"` to see who holds a port.

## Secrets (never commit)

`application.yaml` holds only placeholders. Real client secrets come from `secrets.properties` (project root, git-ignored,
imported via `spring.config.import`) or the env vars `UM_ADMIN_CLIENT_SECRET` / `UM_ANONYMOUS_CLIENT_SECRET`. Copy
`secrets.properties.example`. The app intentionally refuses to start when they are missing. The `um-api-caller` secret is
used only in Postman and never by the service.

## Architecture

Request path: `UserManagementController` (all 15 endpoints under `/usermanagement`) -> `UserManagementService(Impl)`
(thin delegate) -> `datasource/IdentityProvider` -> `datasource/provider/keycloak/KeycloakServiceProvider`
(`@ConditionalOnProperty bm.idp.provider=keycloak`). The provider is where nearly all logic lives; it talks to Keycloak's
Admin REST API through the static `util/HttpHandler` RestTemplate and **requests a fresh admin token (client credentials
of `um-admin`) for every operation**.

Two separate trust boundaries, easy to confuse:
1. **Caller -> this service:** `config/WebSecurityConfiguration` is a stateless OAuth2 resource server. It verifies the
   caller's Keycloak JWT with the realm's JWKS (`jwk-set-uri` is built from `bm.idp.keycloak.url` + admin realm).
   `security/KeycloakRealmRoleConverter` turns `realm_access.roles` into `ROLE_*`; controllers use
   `@PreAuthorize("hasAnyRole(@securityConfiguration.getXxx())")` and `config/SecurityConfiguration` reads the allowed roles
   from `apipermission.api.*` (all `PM_CREATE`). URLs in `app.security.public-urls` are public **and ignore any
   Authorization header** (custom `BearerTokenResolver`; otherwise an expired token gives 401 even on `/anonymous`).
   `createRoleIfNotExist` deliberately has no `@PreAuthorize` (matches the original).
2. **This service -> Keycloak:** the `um-admin` service account. A wrong secret / missing roles / missing scope must
   surface as its own error (see below), never as a generic 401.

Keycloak realm is `master` (`bm.idp.keycloak.admin.realm`); users, roles and clients all live there. The unmanaged
user attributes setting must be Enabled (stores `entity_type`, `entity_id`, additional attributes).

### Keycloak 26.8.2+ gotcha
The admin API rejects tokens without the `kc-admin-api` client scope (log: "Expected audience not available in the
token"). `um-admin` needs that scope as **Default**, plus the `realm-management` roles (shown as `master-realm` in the
master realm). Symptom without it: 500 `UM118`.

### Errors
`exception/UMExceptionHandler` maps each exception class to an HTTP status and builds
`{timestamp, httpStatus, errorCode, message, details{errorCode,message,entity}}`; the message always comes from
`messages/messages.properties` by error code. Original codes UM001-UM112/UM999 are kept; added here: UM113 email missing,
UM114 admin-client login failed, UM115 admin-client forbidden, UM116 userName missing, UM117 roles missing,
UM118 admin token rejected (401 from Keycloak admin API), UM119 anonymous-client login failed.
`KeycloakAdminClientException` (500, `entity` = client id) is for service-side Keycloak configuration problems.
Adding a code means: constant in `UMErrorCodeConstants`/`ErrorCodeConstants`, message in `messages.properties`, handler
mapping if it is a new exception type.

### Package conventions (user's choice, keep it)
`dto` = request payloads (plus the shared `User`, `Roles`, `Attribute`); `entity` = responses and `KeycloakDTO*` classes.
`register` and `updateUser` have separate request types (`RegisterUserRequest`/`RegisterUser`,
`UpdateUserRequest`/`UpdateUser`, both extending `User`) only so Swagger shows different required fields via
`@Schema(requiredProperties=...)`. Endpoint validations are enforced in the controller (e.g. register: userName, email,
roles, status must be exactly `ENABLE`/`DISABLE`; updateUser: userId only), not by Bean Validation.

### Original quirks kept on purpose
See README ("Behaviour kept exactly as in the original"): register echoes the password; updateUser re-enables a user when
`status` is omitted and replaces attributes/roles when given; changeUserStatus accepts only upper-case ENABLE/DISABLE;
Keycloak unreachable gives 500 `UM999`. Do not "fix" these without being asked.

## Tests

JUnit 5 with MockMvc (`UserManagementControllerTest`) and `MockRestServiceServer` bound to the static
`HttpHandler.restTemplate()` (`KeycloakServiceProviderTest`). Nothing talks to a real Keycloak, so a Keycloak upgrade can
break the service without failing tests; verify end to end against the local Keycloak (register -> update -> status ->
delete, then clean up test users and roles).

## Not replicated (internal-only in the original)

Kafka, MCP, request/response logging and masking, the `X-Transaction-Id` / `X-Correlation-Id` / `X-Source-Channel` /
`X-Tenant-Id` headers (accepted, ignored), Russian messages.
