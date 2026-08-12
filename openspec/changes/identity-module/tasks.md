# Tasks: Identity Module — Minimal Vertical Slice

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~2500-3050 total (module + tests) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | 6 PRs, one per architectural layer |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Layer | PR | Est. lines | Risk | Notes |
|---|---|---|---|---|---|
| 1 | Prerequisite + Shared Kernel | PR 1 | ~150-200 | Low | Base for all others; entrypoint move is blocking |
| 2 | Domain Model + Ports | PR 2 (sub-commit per group) | ~650-750 | High | Depends on PR1; request size:exception if diff >400 despite sub-commits |
| 3 | Use Case Implementations | PR 3 (sub-commit per use case) | ~600-700 | High | Depends on PR2; request size:exception if diff >400 |
| 4 | Infrastructure Adapters | PR 4 (sub-commit per adapter group) | ~450-550 | Medium-High | Depends on PR2/PR3 ports |
| 5 | Web Layer | PR 5 | ~350-450 | Medium | Depends on PR3/PR4 |
| 6 | Integration Tests | PR 6 | ~300-400 | Medium | Depends on PR1-PR5 merged |

Order: PR1 → PR2 → PR3 → PR4 → PR5 → PR6, all base `main` (stacked-to-main).
Strictly sequential — hexagonal layers within one bounded context, no independent parallel units.

## Phase 1: Prerequisite + Shared Kernel (PR 1)

- [x] 1.1 Move `CoreApplication.java` from package `mx.edu.utez.sisa.core` to root package `mx.edu.utez.sisa` (blocking gotcha — fixes component/entity scanning)
- [x] 1.2 Add to `pom.xml`: `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `com.h2database:h2`, `io.jsonwebtoken:jjwt-{api,impl,jackson}`
- [x] 1.3 Configure `application.properties`: H2 (`jdbc:h2:mem:sisa;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`, `ddl-auto=create-drop`, console at `/h2-console`), JWT (`sisa.security.jwt.secret/access-token-ttl/refresh-token-ttl`), seed env vars (`sisa.security.bootstrap.admin.*`)
- [x] 1.4 Create `shared/model/RoleType.java` (11-value enum per kernel)
- [x] 1.5 Create `shared/model/Person.java` minimal `@Entity` (`id, curp, firstName, lastName1, lastName2?, institutionalEmail`)
- [x] 1.6 Verify: `JAVA_HOME=C:/Users/JoseNarvaez/.jdks/corretto-21.0.10 ./mvnw clean compile` succeeds

## Phase 2: Domain Model + Ports (PR 2)

- [x] 2.1 [RED] `UserTest`: `registerFailedLogin()` increments attempts, transitions `LOCKED` at 3rd failure (spec: Third consecutive failure locks the account)
- [x] 2.2 [GREEN] Create `UserStatus.java` (`ACTIVE, INACTIVE, LOCKED`) + `User.java` aggregate implementing `registerFailedLogin()`
- [x] 2.3 [RED] extend `UserTest`: `assertCanOperate()` throws `MustChangePasswordException` when pending (spec: Blocked operation while change is pending)
- [x] 2.4 [GREEN] implement `assertCanOperate()`
- [x] 2.5 [RED] extend `UserTest`: `changePassword()` clears `mustChangePassword` (spec: Successful change lifts the block)
- [x] 2.6 [GREEN] implement `changePassword()`
- [x] 2.7 Create `UserRole.java` (`id, userId, roleType, divisionId?`) — division validation lives in `AssignRoleUseCaseImpl` (Phase 3)
- [x] 2.8 Create `RefreshToken.java` (`id, userId, tokenHash, expiresAt, revokedAt?`)
- [x] 2.9 Create 4 in-ports: `CreateUserUseCase`, `AssignRoleUseCase`, `AuthenticateUseCase`, `ChangePasswordUseCase`
- [x] 2.9b Create `RefreshAccessTokenUseCase` in-port — added post-PR2 to close a hexagonal-consistency gap (refresh had no dedicated port; see design.md amendment)
- [x] 2.10 Create out-ports: `UserRepository`, `UserRoleRepository` (incl. `existsByRoleType`, `findByUserId`), `RefreshTokenRepository`, `PersonRepository`, `PasswordHasher`, `AccessTokenIssuer`, `RefreshTokenGenerator`, `LlaveMxOAuthPort`
- [x] 2.11 Create `identity/shared/exception/*.java`: `InvalidCredentialsException`, `AccountLockedException`, `MustChangePasswordException`, `InvalidRefreshTokenException`, `DivisionRuleViolationException`, `PersonAlreadyHasUserException`, `MissingInstitutionalEmailException`, `UserNotFoundException`
- [x] 2.12 Verify: `./mvnw test -Dtest=UserTest` green; `./mvnw compile` clean

## Phase 3: Use Case Implementations (PR 3)

- [x] 3.1 [RED] `AuthenticateUseCaseImplTest`: valid credentials issue both tokens, mocked ports (spec: Valid credentials issue both tokens)
- [x] 3.2 [GREEN] Implement `AuthenticateUseCaseImpl`: load user, reject `LOCKED`, `passwordHasher.matches`, issue tokens on success
- [x] 3.3 [RED] extend test: wrong password / 3rd-fail locks / locked rejects / first-access still issues tokens (spec: 4 scenarios)
- [x] 3.4 [GREEN] complete failure branch (`registerFailedLogin`, reset attempts on success, `lastLoginAt`) — added `User.recordSuccessfulLogin()` (new domain method, own RED/GREEN cycle) since none of the PR2 aggregate methods reset attempts/stamp lastLoginAt
- [x] 3.5 [RED] `ChangePasswordUseCaseImplTest`: wrong current password rejected; success clears flag
- [x] 3.6 [GREEN] Implement `ChangePasswordUseCaseImpl`
- [x] 3.7 [RED] `CreateUserUseCaseImplTest`: success / duplicate Person rejected / missing `institutionalEmail` rejected / must-change caller blocked
- [x] 3.8 [GREEN] Implement `CreateUserUseCaseImpl` (caller `assertCanOperate()`, one-User-per-Person, `username = institutionalEmail`)
- [x] 3.9 [RED] `AssignRoleUseCaseImplTest`: division-scoped role requires `divisionId` / non-division role rejects `divisionId` / multi-role accumulation (spec: 4 scenarios)
- [x] 3.10 [GREEN] Implement `AssignRoleUseCaseImpl`
- [x] 3.11 Create `TokenHashing.java` (pure SHA-256 util) + `TokenHashingTest` (determinism) — pulled forward before 3.1/3.2 since `AuthenticateUseCaseImpl` needs it to hash the refresh token it issues
- [x] 3.12a [RED] `RefreshAccessTokenUseCaseImplTest`: valid non-expired/non-revoked token issues a new access token (spec: Valid refresh token yields a new access token)
- [x] 3.12b [RED] extend test: expired / revoked / unknown token rejected; owning-user-`LOCKED` token rejected even if unexpired; `mustChangePassword` user's token still succeeds (spec: 4 scenarios)
- [x] 3.12c [GREEN] Implement `RefreshAccessTokenUseCaseImpl`: hash lookup → validity checks → load owner → reject if `LOCKED` → issue new access token via `AccessTokenIssuer`
- [x] 3.12 Verify: `./mvnw test -Dtest=*UseCaseImplTest,TokenHashingTest` green (24/24), full suite also green (31/31)

## Phase 4: Infrastructure Adapters (PR 4)

- [x] 4.1 Create Spring Data interfaces: `UserJpaRepository`, `UserRoleJpaRepository`, `RefreshTokenJpaRepository`, `PersonJpaRepository`
- [x] 4.2 Create out-port adapters: `UserRepositoryAdapter`, `UserRoleRepositoryAdapter`, `RefreshTokenRepositoryAdapter`, `PersonRepositoryAdapter`
- [x] 4.3 [RED] `BcryptPasswordHasherTest`: hash+matches round-trip
- [x] 4.4 [GREEN] Implement `BcryptPasswordHasher` (`BCryptPasswordEncoder` strength 12)
- [x] 4.5 [RED] `JwtServiceTest`: sign→parse round-trip preserves `sub`+`roles` claims
- [x] 4.6 [GREEN] Implement `JwtService` (jjwt) + `AccessTokenIssuer` adapter
- [x] 4.7 Create `RefreshTokenGenerator` adapter (opaque random string)
- [x] 4.8 Create `LlaveMxOAuthStubAdapter` (stub only)
- [x] 4.9 [RED] `AdminSeedRunnerTest`: seeds ADMIN when none exists / skips when one exists (spec: 2 scenarios)
- [x] 4.10 [GREEN] Implement `AdminSeedRunner` (`ApplicationRunner`, `existsByRoleType(ADMIN)` check, env-var creds, blank-password skip+warn)
- [x] 4.11 Verify: `./mvnw test -Dtest=BcryptPasswordHasherTest,JwtServiceTest,AdminSeedRunnerTest` green

## Phase 5: Web Layer (PR 5)

- [x] 5.1 Create DTOs: `LoginRequest/Response`, `RefreshRequest/Response`, `ChangePasswordRequest`, `CreateUserRequest/Response`, `AssignRoleRequest/Response`, `ErrorResponse`
- [x] 5.2 Create `GlobalExceptionHandler.java` (`@RestControllerAdvice`) mapping 8 exceptions to HTTP status per design table
- [x] 5.3 Create `JwtAuthenticationFilter.java` (`OncePerRequestFilter`: Bearer → validate → map `roles` claim → `SecurityContext`)
- [x] 5.4 Create `SecurityFilterConfig.java` (`SecurityFilterChain`: stateless, CSRF off, `permitAll` on `/auth/login`, `/auth/refresh`, `/h2-console/**`; `/users/**` → `hasRole("ADMIN")`; filter ordering before `UsernamePasswordAuthenticationFilter`)
- [x] 5.5 Create `AuthController.java`: `POST /auth/login`, `/auth/refresh`, `/auth/change-password` — `/auth/refresh` delegates to `RefreshAccessTokenUseCase` (Phase 3), thin controller like the other two endpoints, no inline token/lock logic
- [x] 5.6 Create `UserController.java`: `POST /users`, `POST /users/{userId}/roles`
- [x] 5.7 Verify: `./mvnw clean package` succeeds; manual curl smoke: seeded ADMIN login returns `{accessToken, refreshToken, mustChangePassword:true}`

## Phase 6: Integration Tests (PR 6)

- [x] 6.1 [RED→GREEN] `AuthFlowIT` (`@SpringBootTest` + `MockMvc`): login → protected `POST /users` (ADMIN) → `POST /auth/refresh` → reuse new access token
- [x] 6.2 [RED→GREEN] extend `AuthFlowIT`: must-change blocks non-change ops, then `/auth/change-password` unblocks
- [x] 6.3 [RED→GREEN] extend `AuthFlowIT`: 3 failed logins → 423 `LOCKED`
- [x] 6.4 [RED→GREEN] extend `AuthFlowIT`: refresh token issued pre-`LOCKED` is rejected once user becomes `LOCKED` (spec: since-LOCKED scenario)
- [x] 6.5 [RED→GREEN] extend `AuthFlowIT`: refresh still succeeds for a `mustChangePassword` user (spec: mustChangePassword refresh scenario)
- [x] 6.6 [RED→GREEN] `AdminSeedIT`: fresh app context creates exactly one ADMIN, restart skips reseed
- [x] 6.7 Verify: full suite `JAVA_HOME=C:/Users/JoseNarvaez/.jdks/corretto-21.0.10 ./mvnw test` green end-to-end
