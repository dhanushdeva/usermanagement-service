# Keycloak setup (realm `master`) for usermanagement-service

The service is configured for the **master** realm of your local Keycloak (`src/main/resources/application.yaml`,
`bm.idp.keycloak.admin.realm: master`). Create the items below once in the admin console
(http://localhost:8080 > log in with your Keycloak admin user, realm dropdown must show **master**).

## 1. Allow custom user attributes
**Realm settings > General > Unmanaged attributes > Enabled > Save**
(needed to store `entity_type`, `entity_id` and `additionalAttributes`).

## 2. Realm role
**Realm roles > Create role** > name `PM_CREATE` > Save.
Every protected API needs a token that carries this role (`apipermission.api.*` in the yaml).

## 3. Client `um-admin` (the service logs in with it to call Keycloak's Admin API)
1. **Clients > Create client** > Client ID `um-admin` > Next.
2. **Client authentication** ON, tick only **Service accounts roles** > Next > Save.
3. **Credentials** tab > copy the generated **Client secret** into `secrets.properties` as `UM_ADMIN_CLIENT_SECRET`
   (see "Secrets" in README.md; the secret never goes into `application.yaml`).
4. **Service accounts roles** tab > **Assign role** > **Filter by clients** > search `realm-management` (shown as
   `master-realm` in the master realm) and assign: `manage-users`, `view-users`, `query-users`, `view-realm`, `manage-realm`.
5. **Client scopes** tab > **Setup** > **Add client scope** > `kc-admin-api` > **Default**.
   **Required on Keycloak 26.8.2 and later:** without it every admin API call returns 401 and the service answers
   `UM118`.

## 4. Client `um-anonymous` (guest tokens for `/anonymous`)
Same as step 3 with Client ID `um-anonymous`; copy its secret into `secrets.properties` as `UM_ANONYMOUS_CLIENT_SECRET`.
Client authentication On, Service accounts roles ticked. No realm-management roles and no `kc-admin-api` scope needed.

## 5. Client `um-api-caller` (for Postman)
1. **Clients > Create client** > Client ID `um-api-caller` > Next.
2. **Client authentication** ON; tick **Standard flow off**, **Direct access grants ON** (password login) and
   **Service accounts roles** > Save.
3. **Credentials** tab: copy the generated secret into the Postman environment (`clientSecret`). It is used only in
   Postman, never by the service.
4. **Service accounts roles** tab > **Assign role** > realm role `PM_CREATE`.

## 6. Test user for the demo
**Users > Add user** > username `local.admin`, Email verified ON > Create.
**Credentials > Set password** `admin123`, Temporary OFF. **Role mapping > Assign role** `PM_CREATE`.

## Values used by the service

| Value | yaml property | Value |
|---|---|---|
| Keycloak URL | `bm.idp.keycloak.url` | `http://localhost:8080` |
| Realm | `bm.idp.keycloak.admin.realm` | `master` |
| Admin client | `bm.idp.keycloak.admin.client_id` / secret in `secrets.properties` | `um-admin` / `UM_ADMIN_CLIENT_SECRET` (copy from Clients > um-admin > Credentials) |
| Anonymous client | `bm.idp.keycloak.anonymous.client_id` / secret in `secrets.properties` | `um-anonymous` / `UM_ANONYMOUS_CLIENT_SECRET` |
| Permission role | `apipermission.api.*` | `PM_CREATE` |

The token-verification URL (`.../realms/master/protocol/openid-connect/certs`) is built from the URL and realm.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| 404 `UM008` Realm not found | Realm name in the yaml doesn't match Keycloak |
| 500 `UM114` / `UM119` | Wrong `um-admin` / `um-anonymous` secret in `secrets.properties` |
| 500 `UM118` | Keycloak 26.8.2+: `kc-admin-api` scope missing on `um-admin` (step 3.5) |
| 500 `UM115` | `um-admin` service account lacks the `realm-management` roles |
| 400 `UM111` / `UM113` / `UM116` / `UM117` | Payload problem: status, email, userName or roles missing/invalid |
| 400 `UM002` | Wrong `um-anonymous` secret, or the client has Service accounts roles switched off |
| 401 with empty body | No / expired Bearer token (5 minutes), or token from another realm |
| 403 `UM112` | The token has no `PM_CREATE` realm role |
| Token request returns `invalid_client` | Wrong client secret in Postman |
| `entity_type` / `entity_id` not saved | Step 1 not done |
