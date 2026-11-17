# Open code-review findings (from /code-review)

1. UserManagementController.java:274 — createRoleIfNotExist has no @PreAuthorize; /usermanagement/anonymous is public and issues valid tokens, so anyone can obtain a token and create realm roles.
2. HttpHandler.java:62 — userSearch builds the URI with URI.create(url + "?search=" + search) without encoding; spaces or special characters cause a generic 500, and `&` injects extra Keycloak query parameters.
3. KeycloakServiceProvider.java:878 — getTokenBody concatenates client_id/client_secret into the form body without URL-encoding; secrets containing `&`, `+`, `%`, `=` end up as a misleading UM114/UM119.
4. HttpHandler.java:20 — static RestTemplate has no connect/read timeout; a stalled Keycloak blocks request threads indefinitely.
5. ErrorCodeConstants.java:18 — MISSING_SMTP_CONFIG is "UM112", the same code as ACCESS_FORBIDDEN, so the caller sees a misleading permission message (404).
6. UserManagementController.java:162 — validateIdpRole dereferences request.getConfigurations() without a null check (NPE gives a generic 500 instead of UM105).
7. KeycloakServiceProvider.java:243 — updateUser/changeUserStatus PUT a fresh KeycloakDTOUser without reading the current user; omitted fields go out as null (can blank email/names under Keycloak 26 user profile). updateName is a no-op.
8. KeycloakServiceProvider.java:407 — registerUser is not atomic: the user is created before roles are assigned, with no rollback; a retry returns 409 UM005.
9. KeycloakServiceProvider.java:262 — deleteUsers stops at the first failure and drops the offending id (entity null); a null id produces DELETE /users/null.
10. UserManagementController.java:181 — addRole/deleteRole/userId/roleName accept blank values and are concatenated into Keycloak URL paths without encoding.
11. SecurityConfiguration.java:19 — allowed roles are split on ',' without trimming, so "PM_CREATE, PM_ADMIN" never matches the second role (repeated 12 times).
