# Topics & configuration used in usermanagement-service

This is a study map of the project. Each topic includes:

- **What** it is (in one line)
- **Where** it is used in this project
- **Why** it is used here

Learning the topics in this order makes the project easy to follow.

---

## Request flow (read this first)

```
HTTP request
  └─▶ Spring Security filter chain           (SecurityConfig)
        ├─ public URL? → allowed
        └─ Bearer JWT → verified with Keycloak JWKS → realm roles → ROLE_* (KeycloakRealmRoleConverter)
  └─▶ Controller                            (UserManagementController)
        └─ @PreAuthorize("@apiAccess.isAllowed('register-user')")   (ApiAccessChecker + app.security.api-roles)
  └─▶ Service                               (UserManagementService)
  └─▶ IdentityProvider interface            (IdentityProvider)
  └─▶ Keycloak implementation               (KeycloakIdentityProvider)
  └─▶ HTTP client to Keycloak Admin API     (KeycloakAdminClient → RestClient)
Errors anywhere → UserManagementException → GlobalExceptionHandler → JSON ErrorResponse
```

---

## 1. Java (language features)

| Topic | Where | Why |
|---|---|---|
| **Java 21** | `build.gradle` (toolchain) | Current LTS version, required by Spring Boot 4 |
| **Records** | `dto/*`, `KeycloakProperties`, `KeycloakTokenResponse`, `CachedToken` | Immutable data classes with no boilerplate |
| **Enums with fields** | `ErrorCode` (code + HTTP status), `UserStatus` | Keep a fixed set of values in one place |
| **Optional** | `UserStatus.fromValue`, `toEnabledFlag` | Represent "value may be absent" without `null` checks |
| **Streams & lambdas** | Role diff, attribute mapping, `ApiAccessChecker` | Concise filtering and mapping of lists |
| **Switch expressions** | `KeycloakIdentityProvider.toUserDto` | Map `entity_type` / `entity_id` attributes |
| **Pattern matching `instanceof`** | `KeycloakRealmRoleConverter` | Safely read the `realm_access.roles` claim |
| **Text blocks `"""`** | Tests | Readable JSON in test code |
| **Generics + `Supplier<T>`** | `KeycloakAdminClient.execute(...)` | One method wraps every Keycloak call for error handling |
| **`synchronized`** | `getAdminAccessToken()` | Thread-safe admin-token cache |
| **Interface + implementation** | `IdentityProvider` → `KeycloakIdentityProvider` | Lets you swap in another IdP later without touching the controller or service |

## 2. Build tool: Gradle

| Topic | Where | Why |
|---|---|---|
| Gradle wrapper (`gradlew.bat`) | Project root, `gradle/wrapper` | Same Gradle version for everyone; nothing to install |
| `org.springframework.boot` plugin | `build.gradle` | `bootRun` and `bootJar` (executable jar) |
| `io.spring.dependency-management` | `build.gradle` | Spring manages library versions, so dependencies need no version numbers |
| Starters | `build.gradle` | One dependency pulls in a whole feature (web, security...) |
| `compileOnly` + `annotationProcessor` | Lombok, configuration processor | Code generated at compile time |
| `bootJar { archiveFileName }`, `jar { enabled=false }` | `build.gradle` | Produces a single `usermanagement-service.jar` |
| `pluginManagement` repositories | `settings.gradle` | Plugins come only from public repositories (no internal Nexus) |

Commands:
- `.\gradlew.bat bootRun` starts the service.
- `build` compiles the code and runs the tests.
- `bootJar` builds the jar.

## 3. Spring Boot core

| Topic | Where | Why |
|---|---|---|
| `@SpringBootApplication` | `UserManagementApplication` | Turns on auto-configuration and component scanning |
| **Dependency injection** (constructor) | Every class, via Lombok `@RequiredArgsConstructor` | Spring creates objects and wires them together |
| Stereotypes `@RestController`, `@Service`, `@Component`, `@Configuration`, `@Bean` | Controller, service, IdP, config | Tell Spring what each class is |
| **Named bean** `@Component("apiAccess")` | `ApiAccessChecker` | Lets `@PreAuthorize` refer to it as `@apiAccess` |
| **application.yaml** | `src/main/resources` | All settings live in one file |
| **Property placeholders `${ENV:default}`** | `application.yaml` | Override any value with an environment variable (local vs Docker) |
| **Type-safe config `@ConfigurationProperties`** (records) | `KeycloakProperties` (`keycloak.*`), `SecurityProperties` (`app.security.*`) | Typed objects instead of many `@Value` fields |
| `@ConfigurationPropertiesScan` | `UserManagementApplication` | Registers those records automatically |
| Relaxed binding (kebab-case ↔ camelCase) | `server-url` → `serverUrl` | YAML style maps to Java style |
| `Duration` binding (`5s`, `15m`) | timeouts, `link-lifespan` | Human-readable time values |
| Compact record constructor defaults | `KeycloakProperties` | Use defaults when a property is missing |
| spring-boot-configuration-processor | `build.gradle` | IDE autocomplete for your own properties |
| DevTools | `build.gradle` | Auto-restart while developing |

## 4. Spring Web MVC (REST API)

| Topic | Where | Why |
|---|---|---|
| `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@DeleteMapping` | `UserManagementController` | Map URLs to methods |
| `@RequestBody`, `@RequestParam` | Controller | Read a JSON body or query parameters |
| `ResponseEntity` + `HttpStatus` | Controller | Control the status code (201, 204, 404) |
| JSON with **Jackson 3** | DTOs | Automatic object ↔ JSON conversion |
| Jackson annotations `@JsonInclude(NON_NULL)`, `@JsonProperty`, `@JsonIgnore`, `@JsonIgnoreProperties`, `access = WRITE_ONLY` | `UserDto`, Keycloak models, requests | Control JSON shape. For example, the password is accepted in requests but never returned |
| DTO pattern (request / response / domain) | `dto/` | API objects are kept separate from Keycloak objects |
| Manual request validation | Controller `requireUser`, `requireUserId` | Return the same error codes as the original (UM107, UM110...) |

## 5. Error handling and i18n

| Topic | Where | Why |
|---|---|---|
| Custom runtime exception | `UserManagementException(ErrorCode, entity)` | One exception type for every business error |
| `@RestControllerAdvice` + `@ExceptionHandler` | `GlobalExceptionHandler` | Convert exceptions into JSON `ErrorResponse` in one place |
| Error code → HTTP status | `ErrorCode` enum | Example: UM005 → 409 Conflict |
| **MessageSource** (i18n) | `messages/messages.properties`, `spring.messages.basename` | Readable messages per code; add `messages_fr.properties` for another language |
| `LocaleContextHolder` | `GlobalExceptionHandler` | Pick the language from the `Accept-Language` header |
| Translating Keycloak errors | `KeycloakErrorTranslator` | Keycloak's 404 / 409 / 401 become meaningful UMxxx codes |

## 6. Spring Security (replaces bm-authorization)

| Topic | Where | Why |
|---|---|---|
| `SecurityFilterChain` bean | `SecurityConfig` | Defines which URLs are public and which need a token |
| **OAuth2 Resource Server (JWT)** | `spring-boot-starter-oauth2-resource-server`, `jwk-set-uri` | Validate Bearer tokens issued by Keycloak |
| **JWKS** (public keys) | `.../protocol/openid-connect/certs` | Verify the token signature without sharing any secret |
| Stateless sessions | `SessionCreationPolicy.STATELESS` | REST API: every request carries its own token |
| CSRF disabled | `SecurityConfig` | Not needed for token-based APIs with no cookies |
| `permitAll` for public URLs | `app.security.public-urls` | `/anonymous`, Swagger and health need no login |
| `JwtAuthenticationConverter` + custom `Converter<Jwt, Collection<GrantedAuthority>>` | `KeycloakRealmRoleConverter` | Keycloak puts roles in `realm_access.roles`; Spring needs `ROLE_*` authorities |
| `GrantedAuthority` / `ROLE_` prefix | Converter, `ApiAccessChecker` | Spring's representation of roles |
| **Method security** `@EnableMethodSecurity` + `@PreAuthorize` | Controller | Per-endpoint permission check |
| **SpEL calling a bean** `@apiAccess.isAllowed('key')` | Controller → `ApiAccessChecker` | Roles per API come from YAML instead of code |
| `SecurityContextHolder` | `ApiAccessChecker` | Read the current caller's authorities |
| 401 vs 403 | Security + `AuthorizationDeniedException` handler | 401 means no or invalid token; 403 (UM112) means the token is missing the required role |

## 7. Keycloak / OAuth2 / OpenID Connect concepts

| Topic | Where | Why |
|---|---|---|
| Realm | `keycloak.realm` | An isolated space for users, roles and clients |
| Realm roles vs client roles | `USER_MANAGEMENT_ADMIN`; `realm-management` roles | Realm roles are for your app; `realm-management` roles grant Admin API rights |
| Composite roles, `default-roles-<realm>` | `getRoleWithComposites`, role diff | Roles that contain other roles. The default role is kept on update |
| Confidential client + client secret | `um-admin`, `um-anonymous`, `um-api-caller` | The application authenticates as itself |
| **Client credentials grant** | `KeycloakAdminClient.requestClientCredentialsToken` | Service-to-service token without a user |
| Password grant (direct access) | `um-api-caller` + `local.admin` (testing) | Get a user token from curl or Postman |
| Service accounts | Clients with **Service accounts roles** | A client's own "user" that roles can be assigned to |
| Access token (JWT) structure, `expires_in` | `KeycloakTokenResponse` | Token caching and refreshing before expiry |
| **Keycloak Admin REST API** | `/admin/realms/{realm}/users`, `/roles`, `/role-mappings/realm`, `/credentials`, `/execute-actions-email` | Manage users and roles over HTTP (no admin-client library) |
| `Location` header on create (201) | `createUser` | Keycloak returns the new user id in this header |
| User attributes and the **unmanaged attribute policy** | `entity_type`, `entity_id`, realm import | Store custom data on users (Keycloak 24+) |
| Required actions email | `VERIFY_EMAIL`, `UPDATE_PASSWORD` | Users verify their email and set their own password |
| Realm import (JSON) | `docker/keycloak/realm-import/` | Keycloak is configured automatically on start |

## 8. HTTP client to Keycloak

| Topic | Where | Why |
|---|---|---|
| **`RestClient`** (Spring 6+ fluent client) | `KeycloakClientConfig`, `KeycloakAdminClient` | Modern synchronous HTTP client that replaces `RestTemplate` |
| `baseUrl`, URI templates `{realm}`, `UriBuilder` + query parameters | `KeycloakAdminClient` | Safe URL building with automatic encoding |
| `JdkClientHttpRequestFactory` (Java `HttpClient`), timeouts, HTTP/1.1 | `KeycloakClientConfig` | Connect and read timeouts. A request that hangs doesn't block the service forever |
| Form-encoded body (`MultiValueMap`) | Token request | The token endpoint requires `application/x-www-form-urlencoded` |
| `ParameterizedTypeReference<List<T>>` | `USER_LIST`, `ROLE_LIST` | Deserialize generic JSON lists |
| Bearer header via `Consumer<HttpHeaders>` | `adminAuthorization()` | Add the admin token to every Admin API call |
| `RestClientResponseException` / `ResourceAccessException` | `execute()` | Handle HTTP errors and "Keycloak down" (UM001) separately |
| Token caching (cleared on 401) | `CachedToken`, `forgetAdminToken` | Avoids a new token on every call; after a 401 the next call fetches a fresh token |

## 9. API documentation and monitoring

| Topic | Where | Why |
|---|---|---|
| **springdoc-openapi** (Swagger UI) | `build.gradle`, `OpenApiConfig` | Interactive API documentation at `/swagger-ui/index.html` |
| `@Tag`, `@Operation`, `@SecurityRequirement`, `@Schema` | Controller, DTOs | Describe the endpoints. Adds an "Authorize" (Bearer) button to Swagger |
| **Actuator** | `management.endpoints.web.exposure.include: health,info` | `/actuator/health` for liveness and Docker checks |
| Logging with SLF4J (Lombok `@Slf4j`) | Services, handlers | Plain Spring Boot logging (replaces bm-logging-utils) |

## 10. Lombok

| Annotation | Where | Effect |
|---|---|---|
| `@RequiredArgsConstructor` | Beans | Constructor for `final` fields, used for dependency injection |
| `@Getter` / `@Setter` | `UserDto`, Keycloak models, exception | No hand-written getters or setters |
| `@Slf4j` | Many classes | Creates a `log` field |

## 11. Testing

| Topic | Where | Why |
|---|---|---|
| JUnit 5 (`@Test`, `@BeforeEach`) | `src/test` | Test framework |
| AssertJ (`assertThat`, `assertThatThrownBy`) | All tests | Readable assertions |
| `@SpringBootTest` + **MockMvc** (`@AutoConfigureMockMvc`) | `UserManagementControllerTest` | Call endpoints through the real security chain without starting a server |
| `@MockitoBean` | Controller test | Replace the service and `JwtDecoder` with mocks |
| spring-security-test `jwt().authorities(...)` | Controller test | Fake a logged-in caller with given roles |
| `jsonPath(...)` | Tests | Assert on JSON fields |
| **MockRestServiceServer** | `KeycloakIdentityProviderTest` | Fake Keycloak HTTP responses, so no Docker is needed |
| Plain unit test | `KeycloakRealmRoleConverterTest` | Test the converter without Spring |

## 12. Docker

| Topic | Where | Why |
|---|---|---|
| Dockerfile (`eclipse-temurin:21-jre`, non-root user) | `Dockerfile` | Run the jar in a container |
| `.dockerignore` | Project root | Keep the build context small (only the jar) |
| **Docker Compose** services, volumes, healthcheck, `depends_on` | `docker-compose.yml` | Starts Postgres, Keycloak and the service together |
| Compose **profiles** (`--profile app`) | `usermanagement-service` service | Start only Keycloak by default and run the app from your IDE |
| Keycloak `start-dev --import-realm` | `docker-compose.yml` | Dev mode that loads the realm JSON |
| Container networking (`http://keycloak:8080`) | `KEYCLOAK_URL` env var | Inside Docker, "localhost" means the container itself |

---

## All configuration at a glance

| File | What is configured |
|---|---|
| `build.gradle` | Spring Boot 4.1.1, Java 21, starters, Lombok, springdoc, test libraries, jar name |
| `settings.gradle` | Project name, plugin repositories |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle version (9.4.1, already cached locally) |
| `application.yaml` → `server.port` | 9012 |
| `application.yaml` → `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | Keycloak public keys used to verify incoming tokens |
| `application.yaml` → `spring.messages.basename` | Location of error messages |
| `application.yaml` → `keycloak.*` | URL, realm, timeouts, admin/anonymous clients, temporary password, email verification |
| `application.yaml` → `app.security.public-urls` | URLs that need no token |
| `application.yaml` → `app.security.api-roles` | Which roles may call each API |
| `application.yaml` → `management.*`, `logging.*` | Actuator endpoints, log level |
| `messages/messages.properties` | Text for each error code UMxxx |
| `docker-compose.yml`, `Dockerfile`, `.dockerignore` | Local container setup |
| `docker/keycloak/realm-import/*.json` | Realm, roles, clients, service-account roles, test user, user profile |

## Suggested learning order

1. Java records, enums, streams
2. Spring Boot basics: dependency injection, beans, `application.yaml`, `@ConfigurationProperties`
3. REST controllers, DTOs, JSON
4. Exception handling with `@RestControllerAdvice`
5. OAuth2 / OIDC and Keycloak concepts (realm, client, roles, tokens)
6. Spring Security resource server, JWT, `@PreAuthorize`
7. `RestClient` and the Keycloak Admin REST API
8. Testing with MockMvc and MockRestServiceServer
9. Docker and Docker Compose

See also: `README.md` (how to run) and `KEYCLOAK_SETUP.md` (Keycloak configuration and why).
