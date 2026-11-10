# Keycloak setup for usermanagement-service

This guide covers:

1. Which URLs the service uses.
2. What to create in Keycloak.
3. Which values to copy from Keycloak into `src/main/resources/application.yaml`.
4. Why each setting is needed (section 6).

There are two ways to set up Keycloak:

| Option | When to use | Effort |
|---|---|---|
| **A. Automatic** (Docker Compose + realm import) | You run Keycloak with this project's `docker-compose.yml` | 1 command; no copy/paste |
| **B. Manual** (Keycloak admin console) | You already run your own Keycloak (Docker or zip) | About 15 minutes of clicking, then copy the secrets into the yaml |

---

## 1. URLs

| What | URL |
|---|---|
| Keycloak admin console | http://localhost:8080 (login `admin` / `admin`) |
| Realm used by the service | `usermanagement-local` |
| Token endpoint (to get a token) | http://localhost:8080/realms/usermanagement-local/protocol/openid-connect/token |
| Public keys (JWKS), used by the service to verify tokens | http://localhost:8080/realms/usermanagement-local/protocol/openid-connect/certs |
| Realm discovery (handy for checking) | http://localhost:8080/realms/usermanagement-local/.well-known/openid-configuration |
| Admin REST API (called by the service) | http://localhost:8080/admin/realms/usermanagement-local/... |
| This service | http://localhost:9012 |
| Swagger UI | http://localhost:9012/swagger-ui/index.html |
| Health check | http://localhost:9012/actuator/health |

You do **not** need to type the JWKS or Admin API URLs anywhere. The service builds them from `keycloak.server-url` and `keycloak.realm`.

---

## 2. Option A: automatic setup (recommended)

```powershell
cd C:\Team_5\usermanagement-service\usermanagement-service
docker compose up -d                      # Keycloak + Postgres; imports the realm
# wait ~30-60 s, then check:
Invoke-RestMethod http://localhost:8080/realms/usermanagement-local/.well-known/openid-configuration
.\gradlew.bat bootRun
```

The file `docker/keycloak/realm-import/usermanagement-local-realm.json` already contains everything from section 3, using the same secrets as the defaults in `application.yaml`. **You don't need to change anything in the yaml.**

> If another Keycloak is already running on port 8080, stop it first. Otherwise use Option B with that Keycloak.

---

## 3. Option B: manual setup in the Keycloak admin console

These steps were written for Keycloak 24–26. Open http://localhost:8080 and log in as `admin`.

### Step 1: Create the realm
1. Click the realm dropdown at the top left, then **Create realm**.
2. Set **Realm name** to `usermanagement-local`, then click **Create**.

### Step 2: Allow custom user attributes
1. Go to **Realm settings → General → Unmanaged attributes** and choose **Enabled**.
2. Click **Save**.

### Step 3: Create realm roles
Go to **Realm roles → Create role** and create these three roles:

| Role name | Purpose |
|---|---|
| `USER_MANAGEMENT_ADMIN` | Allows a caller to use the protected APIs |
| `ANONYMOUS_USER` | Given to guest tokens |
| `CUSTOMER` | Sample role to assign to users |

### Step 4: Create client `um-admin` (used by the service itself)
1. Go to **Clients → Create client**.
   - Client type: `OpenID Connect`
   - Client ID: `um-admin`
   - Click **Next**.
2. On the capability config page:
   - Turn **Client authentication** ON.
   - Tick **Service accounts roles**.
   - Untick **Standard flow** and **Direct access grants**.
   - Click **Next**, then **Save**.
3. Open the **Service accounts roles** tab and click **Assign role**.
4. Change the filter to **Filter by clients**, search for `realm-management`, and tick these roles:
   - `manage-users`
   - `view-users`
   - `query-users`
   - `view-realm`
   - `manage-realm`
5. Click **Assign**.
6. Open the **Credentials** tab and copy the **Client Secret**. You'll paste it as `keycloak.admin-client.client-secret` in section 4.

### Step 5: Create client `um-anonymous` (guest tokens)
1. Create the client the same way as in Step 4:
   - Client ID: `um-anonymous`
   - **Client authentication** ON
   - Only **Service accounts roles** ticked
2. Open **Service accounts roles → Assign role**, choose **Filter by realm roles**, and assign `ANONYMOUS_USER`.
3. Open **Credentials** and copy the **Client Secret**. You'll paste it as `keycloak.anonymous-client.client-secret` in section 4.

### Step 6: Create client `um-api-caller` (for you: Postman / curl / a frontend)
1. Create the client:
   - Client ID: `um-api-caller`
   - **Client authentication** ON
   - Tick both **Service accounts roles** and **Direct access grants**
2. Open **Service accounts roles → Assign role** and assign the realm role `USER_MANAGEMENT_ADMIN`.
3. Open **Credentials** and copy the secret. You use this secret only when requesting a token in step 8. It does **not** go into `application.yaml`.

### Step 7 (optional): Create a test user
1. Go to **Users → Add user**.
   - Username: `local.admin`
   - Email verified: ON
   - Click **Create**.
2. Open **Credentials → Set password**, enter `admin123`, and turn **Temporary** OFF.
3. Open **Role mapping → Assign role** and assign `USER_MANAGEMENT_ADMIN`.

### Step 8: Check that you can get a token
```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/realms/usermanagement-local/protocol/openid-connect/token `
  -Body @{ grant_type='client_credentials'; client_id='um-admin'; client_secret='<um-admin secret>' }
```
The response should contain an `access_token`. If you get `401 invalid_client`, the secret is wrong.

### Step 9 (optional): Email verification
This is needed only if you set `keycloak.email-verification.enabled: true`.

1. Go to **Realm settings → Email** and fill in the SMTP host, port and from address.
   - For local testing you can run MailHog or Mailpit on `localhost:1025`.
2. Make sure each user has an email address.

---

## 4. What to copy into `application.yaml`

File: `src/main/resources/application.yaml`, under the `keycloak:` section.

| Value in Keycloak | Where in Keycloak | yaml property | Default (Option A) |
|---|---|---|---|
| Keycloak base URL | The browser URL, without a path | `keycloak.server-url` | `http://localhost:8080` |
| Realm name | Realm dropdown | `keycloak.realm` | `usermanagement-local` |
| `um-admin` client ID | Clients | `keycloak.admin-client.client-id` | `um-admin` |
| `um-admin` secret | Clients → um-admin → Credentials | `keycloak.admin-client.client-secret` | `um-admin-local-secret` |
| `um-anonymous` client ID | Clients | `keycloak.anonymous-client.client-id` | `um-anonymous` |
| `um-anonymous` secret | Clients → um-anonymous → Credentials | `keycloak.anonymous-client.client-secret` | `um-anonymous-local-secret` |
| Role that may call the APIs | Realm roles | `app.security.api-roles.*` | `USER_MANAGEMENT_ADMIN` |

Paste your values here:

```yaml
keycloak:
  server-url: ${KEYCLOAK_URL:http://localhost:8080}          # your Keycloak URL
  realm: ${KEYCLOAK_REALM:usermanagement-local}              # your realm name
  admin-client:
    client-id: ${KEYCLOAK_ADMIN_CLIENT_ID:um-admin}
    client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET:<paste um-admin secret>}
  anonymous-client:
    client-id: ${KEYCLOAK_ANONYMOUS_CLIENT_ID:um-anonymous}
    client-secret: ${KEYCLOAK_ANONYMOUS_CLIENT_SECRET:<paste um-anonymous secret>}
```

**Better: don't edit the file.** Set environment variables in the same PowerShell window before running:

```powershell
$env:KEYCLOAK_ADMIN_CLIENT_SECRET = '<um-admin secret>'
$env:KEYCLOAK_ANONYMOUS_CLIENT_SECRET = '<um-anonymous secret>'
.\gradlew.bat bootRun
```

You don't need to change these:
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, because it is built automatically from `server-url` and `realm`.
- `app.security.public-urls`.
- `server.port`.

If you used a role name other than `USER_MANAGEMENT_ADMIN`, replace it in every line under `app.security.api-roles`.

---

## 5. Test end to end

```powershell
# 1. Token for you as the API caller
$token = (Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/realms/usermanagement-local/protocol/openid-connect/token `
  -Body @{ grant_type='client_credentials'; client_id='um-api-caller'; client_secret='<um-api-caller secret>' }).access_token
$h = @{ Authorization = "Bearer $token" }

# 2. Call the service
$body = '{"user":{"userName":"john","password":"Pass@123","email":"john@example.com","entityType":"CUSTOMER","entityId":"C-1","roles":["CUSTOMER"]}}'
Invoke-RestMethod -Method Post http://localhost:9012/usermanagement/register -Headers $h -ContentType 'application/json' -Body $body
```

You can also check in Keycloak: **Users → john** should show the `entity_type` / `entity_id` attributes and the `CUSTOMER` role.

### Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `UM001` / 503 | Keycloak is not running, or `server-url` is wrong |
| `UM009` / 401 when calling the service | Wrong `um-admin` / `um-anonymous` secret in the yaml |
| `UM003` | `um-admin` service account is missing `realm-management` roles (Step 4) |
| `UM008` | Realm name in the yaml doesn't match Keycloak |
| 401 from the service | No Bearer token, an expired token, or a token from a different realm or Keycloak |
| 403 `UM112` | The token has no `USER_MANAGEMENT_ADMIN` realm role (Step 6) |
| Attributes `entity_type` / `entity_id` are not saved | Unmanaged attributes are not enabled (Step 2) |
| `UM113` on resend mail | `email-verification.enabled` is false |

---

## 6. Why each piece is needed

### The two token flows

The service deals with tokens in two directions:

```
 You / frontend ──(token from um-api-caller)──▶ usermanagement-service ──(token from um-admin)──▶ Keycloak Admin API
                                                     │
                                                     └─ checks the incoming token with Keycloak's public keys (JWKS)
```

### Why each piece of configuration is needed

- **Realm `usermanagement-local`:** keeps your users, roles and clients separate from Keycloak's `master` realm. The service only works inside the realm named in `keycloak.realm`.
- **`server-url` + `realm`:** the service builds every Keycloak URL from these two values:
  - the token URL,
  - the Admin API URL,
  - the JWKS URL.

  This is why they are the only URLs you configure.
- **`um-admin` client with service-account roles:**
  - To create, update or delete users and roles, the service calls the Keycloak **Admin REST API**.
  - It logs in as itself using the *client credentials* grant (client ID + secret, no user).
  - The `realm-management` roles give that login permission:
    - `manage-users` to create, update and delete users,
    - `view-users` / `query-users` to search and read users,
    - `view-realm` / `manage-realm` to read and create realm roles.
  - Without them, Keycloak returns 403, which the service reports as `UM003`.
- **Client secret in the yaml:** it is the "password" of the `um-admin` client. It is copied into the yaml (or an environment variable) because the service must present it to get its admin token. Keep it in environment variables for anything beyond local work.
- **`um-anonymous` client:** `/usermanagement/anonymous` gives guests a real Keycloak token without creating a user. It uses a separate client so guest tokens carry only `ANONYMOUS_USER` and can never use admin rights.
- **`um-api-caller` client and the `USER_MANAGEMENT_ADMIN` role:** this replaces the internal bm-authorization library.
  1. The service is an OAuth2 *resource server*: every protected call must carry a Bearer token from your realm.
  2. The service verifies the token's signature with the realm's public keys (JWKS). No secret is needed for this.
  3. `app.security.api-roles` then decides which realm roles may call which API.
  
  `um-api-caller` is simply a convenient client for getting such a token. Its secret stays with the caller, not in the service.
- **Unmanaged attributes:** since Keycloak 24, a "user profile" rejects or drops attributes it doesn't know about. The service stores `entity_type`, `entity_id` and your `additionalAttributes` as user attributes, so these must be allowed.
- **Environment-variable overrides (`${KEYCLOAK_...:default}`):**
  - The same jar runs locally, in Docker (`KEYCLOAK_URL=http://keycloak:8080`) or anywhere else without code changes.
  - Secrets don't need to be committed.
- **SMTP (optional):** with email verification on, the service doesn't set a password. Instead Keycloak emails the user a "verify email + set password" link, and Keycloak can only send that email if SMTP is configured.
