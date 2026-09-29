# Design: Identity Module — Minimal Vertical Slice

First backend feature in `118-SISA-BACK`. Proves the hexagonal module layout
end-to-end while delivering: create user, assign role, log in (access + refresh
JWT), and mandatory first-access password change. This document resolves every
open decision left by the proposal so `sdd-tasks` can proceed with zero
ambiguity.

## Resolutions at a glance (the 5 open items + key calls)

| # | Open decision | Resolution |
|---|---------------|------------|
| H2 config | mode / console / ddl-auto | `jdbc:h2:mem` + `ddl-auto=create-drop` + console enabled **dev-only** |
| mustChangePassword gate | service guard vs security filter | **Service-layer domain guard** on the `User` aggregate (caller-scoped) |
| Refresh endpoint | dedicated vs folded | **Dedicated `POST /auth/refresh`** |
| Password hashing | encoder | `BCryptPasswordEncoder` strength 12, behind a `PasswordHasher` out-port |
| Bootstrap seed | how to detect "no ADMIN" | Query `UserRole.existsByRoleType(ADMIN)`, not "users table empty" |
| Entrypoint scan | (gotcha found) | **Move `CoreApplication` to root pkg `mx.edu.utez.sisa`** |

## Technical Approach

Strict hexagonal per `00-ARQUITECTURA.md` under `mx.edu.utez.sisa.identity`:
`domain/model|port/in|port/out|service`, `application`, `infrastructure/
persistence|web|external`, plus module-local `shared` (exceptions). The global
shared kernel (`mx.edu.utez.sisa.shared`) is created for the first time with the
minimum needed: `Person` (load-only reference for this slice) and the `RoleType`
enum. One `XxxUseCase` service implements one `XxxPort` (in); JPA adapters
implement out-ports; controllers are thin; `@Transactional` lives only in the
service layer. Spring Security owns the filter chain + BCrypt; `jjwt` signs and
parses tokens. Credential validation, failed-attempt counting and auto-lock stay
in the **domain** (see Decision: Auth ownership) so the aggregate invariants are
not scattered into Spring infrastructure.

> Two distinct "shared" packages — do not confuse them:
> `mx.edu.utez.sisa.shared` = global kernel (`Person`, `RoleType`).
> `mx.edu.utez.sisa.identity.shared` = module-local exceptions/constants.

## Architecture Decisions

### Decision: Entrypoint package (blocking gotcha)

**Choice**: Move `CoreApplication` from `mx.edu.utez.sisa.core` to the root
package `mx.edu.utez.sisa`.
**Alternatives considered**: Keep it in `.core` and add
`@SpringBootApplication(scanBasePackages=...)` + `@EntityScan` +
`@EnableJpaRepositories`.
**Rationale**: `identity` and `shared` are *siblings* of `core`, not children.
Default component/entity/repository scanning starts at the entrypoint's package
and only descends. Leaving it in `.core` silently fails to register every module
bean and entity. Relocating to the root package makes all current and future
modules scannable with zero annotation clutter — the idiomatic Spring Boot
layout for a modular monolith.

### Decision: mustChangePassword gate — service-layer domain guard

**Choice**: Enforce in the domain. The `User` aggregate exposes
`assertCanOperate()` which throws `MustChangePasswordException` when
`mustChangePassword == true`. Every use case except `ChangePasswordUseCase`
loads the **caller** (userId extracted from the JWT principal by the controller,
passed as a plain command field) and calls `caller.assertCanOperate()` before
proceeding.
**Alternatives considered**: A Spring Security `OncePerRequestFilter` that blocks
all paths except `/auth/change-password` when the principal's flag is true.
**Rationale**: `01-identidad.md` frames this explicitly as a *domain invariant*
("el sistema rechaza cualquier operación excepto cambio de contraseña"), so it
belongs in the aggregate, not infrastructure. The access token carries only
`sub`+`roles` (no `mustChangePassword`), so a filter would need a per-request DB
lookup on *every* endpoint; the service guard only loads the caller on the two
mutating ADMIN endpoints, which need the caller for authorization anyway. It is
unit-testable with mocked ports and needs no security-filter wiring. Login still
issues normal tokens for a must-change user (per spec) so they can reach
`/auth/change-password`.

### Decision: Auth ownership — domain validates, Spring provides primitives

**Choice**: `AuthenticateUseCase` (domain service) orchestrates login: load user
→ reject if `LOCKED` → `passwordHasher.matches()` → on failure
`user.registerFailedLogin()` (auto-`LOCKED` at 3) → on success reset attempts,
set `lastLoginAt`, issue tokens. BCrypt matching runs via a `PasswordHasher`
out-port (adapter wraps Spring's `BCryptPasswordEncoder`).
**Alternatives considered**: Delegate credential validation to Spring's
`AuthenticationManager` + `UserDetailsService`, incrementing attempts via an
`AuthenticationFailureHandler`.
**Rationale**: The failed-attempt/auto-lock invariant is domain logic. Routing it
through `AuthenticationManager` scatters it into Spring event handlers and
couples the aggregate rule to framework callbacks. We still honor the proposal's
"Spring Security + BCrypt + filter chain" decision — BCrypt is Spring's, the
stateless filter chain is Spring's — but keep the invariant in the aggregate.
This is a deliberate refinement of the proposal's narrative, flagged here.
**PO confirmed 2026-07-05**: keep validation + auto-lock in the domain, not
`AuthenticationManager`.

### Decision: Refresh endpoint — dedicated `POST /auth/refresh`

**Choice**: Standalone endpoint exchanging a refresh token for a new access
token. Refresh tokens are opaque random strings; only their SHA-256 hash is
stored (mirrors `PasswordResetToken`), 1-day TTL, revocable, no rotation-on-use.
On refresh: SHA-256 the presented token → lookup by hash → reject if
missing/expired/revoked → **load the owning `User` and reject if
`status == LOCKED`** (closes the gap where a token issued before a LOCKED
transition would otherwise remain usable for up to its full 1-day TTL) →
issue a fresh access token. `mustChangePassword = true` does NOT block
refresh (mirrors the login exception — the user still needs to reach
`/auth/change-password`).
**Alternatives considered**: Fold refresh into the login response only; check
only the refresh token's own validity without re-checking current `User`
status (rejected — PO decision 2026-07-05: close the security gap even at
the cost of one extra repository lookup per refresh).
**Rationale**: The RESTful, standard pattern; keeps token lifetimes independent
and lets the client refresh silently. `LOCKED` must take effect immediately
for already-issued tokens, not just for future login attempts — otherwise a
locked-out user (or an attacker who stole a refresh token before the lock)
could keep operating for up to 24h via refresh alone.

**Amendment (post-PR2, 2026-07-05)**: this decision originally described the
refresh flow procedurally without assigning it a dedicated in-port, unlike
every other operation in this module (one `XxxUseCase` per `XxxPort`). Caught
during PR2 review — leaving it that way would have forced the logic into the
Phase 5 controller directly, the one place with real business rules
(the LOCKED re-check above). **Fixed**: added `RefreshAccessTokenUseCase`
(in-port) + `RefreshAccessTokenUseCaseImpl` (Phase 3) so refresh follows the
same pattern as `AuthenticateUseCase`, `CreateUserUseCase`, etc. `AuthController`
now calls this use case rather than orchestrating out-ports itself.

### Decision: H2 configuration — in-memory, create-drop, dev-only console

**Choice**: `jdbc:h2:mem:sisa;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`,
`ddl-auto=create-drop`, H2 console enabled at `/h2-console`.
**Alternatives considered**: `ddl-auto=update`; console disabled.
**Rationale**: In-memory DB is recreated every boot (matches the rollback plan —
no migration), so `create-drop` gives a clean, entity-derived schema each run;
`update` is meaningless when nothing persists across restarts. Console enabled
because the whole DB is dev-only and it accelerates debugging; it is explicitly
marked **must be removed before any real DB** and requires
`frameOptions=sameOrigin` + a permitAll on `/h2-console/**` in the dev filter
chain. `DB_CLOSE_DELAY=-1` keeps the schema alive for the JVM lifetime.

### Decision: Bootstrap ADMIN seed — check "no ADMIN role exists"

**Choice**: `AdminSeedRunner implements ApplicationRunner` in
`identity.infrastructure.bootstrap`. It checks
`userRoleRepository.existsByRoleType(ADMIN)`; if false and the password env var
is set, it persists a `Person` + `User(mustChangePassword=true, status=ACTIVE)` +
`UserRole(ADMIN, divisionId=null)` directly via repositories + `PasswordHasher`.
Env vars: `SISA_ADMIN_USERNAME` (institutional email = username),
`SISA_ADMIN_PASSWORD`, `SISA_ADMIN_CURP`. If the password var is blank it logs a
warning and skips (never seeds a blank-password admin).
**Alternatives considered**: Check whether the `User` table is empty; reuse the
`CreateUserUseCase`.
**Rationale**: The real invariant is "at least one ADMIN exists," not "zero
users." Checking table-emptiness would fail to re-seed an admin if it were
deleted while other users remained. It lives in `infrastructure` (not `domain`),
and bypasses the caller-guard deliberately because there is no authenticated
principal at boot.

### Decision: Person creation is out of scope — load-only reference

**Choice**: `CreateUserUseCase` accepts an existing `personId`; `PersonRepository`
loads it to read `institutionalEmail` and enforce one-User-per-Person. The
aggregate that owns `Person` creation (Admission/Enrollment) does not exist yet,
so runtime Persons come only from the ADMIN seed; integration tests seed Person
rows via the repository.
**Rationale**: The spec mandates `username = Person.institutionalEmail` and
forbids the system generating the email, i.e. Person pre-exists. Keeping Person
load-only avoids scope creep while staying faithful to the shared-kernel design.

## Data Flow

```
POST /auth/login ─→ AuthController ─→ AuthenticateUseCase ─→ UserRepository (load)
                                              │
                        PasswordHasher.matches │ (fail → registerFailedLogin → LOCKED@3)
                                              ▼
                    AccessTokenIssuer(jjwt) + RefreshTokenGenerator ─→ RefreshTokenRepository (hash)
                                              ▼
                        { accessToken, refreshToken, mustChangePassword }

GET/POST protected ─→ JwtAuthenticationFilter (Bearer → validate → SecurityContext[ROLE_*])
                                              ▼
      Controller extracts caller userId ─→ UseCase ─→ caller.assertCanOperate() ─→ domain op

POST /auth/refresh ─→ AuthController ─→ RefreshAccessTokenUseCase ─→ RefreshTokenRepository (hash lookup)
                                              │ (missing/expired/revoked → InvalidRefreshTokenException)
                                              ▼
                        UserRepository (load owner) ─→ reject if LOCKED (AccountLockedException)
                                              ▼
                              AccessTokenIssuer(jjwt) ─→ { accessToken }
```

## File Changes

| File (under `src/main/java/mx/edu/utez/sisa/`) | Action | Description |
|---|---|---|
| `../CoreApplication.java` → `mx/edu/utez/sisa/CoreApplication.java` | Move | Root-package entrypoint so all modules are scanned |
| `shared/model/Person.java` | Create | Minimal `@Entity`: `id, curp, firstName, lastName1, lastName2?, institutionalEmail` |
| `shared/model/RoleType.java` | Create | Global enum, 11 values verbatim from kernel |
| `identity/domain/model/User.java` | Create | Aggregate root + invariants (`assertCanOperate`, `registerFailedLogin`, `changePassword`) |
| `identity/domain/model/UserRole.java` | Create | `id, userId, roleType, divisionId?` + division-required rule |
| `identity/domain/model/RefreshToken.java` | Create | `id, userId, tokenHash, expiresAt, revokedAt?` |
| `identity/domain/model/UserStatus.java` | Create | Enum `ACTIVE, INACTIVE, LOCKED` |
| `identity/domain/port/in/CreateUserUseCase.java` | Create | in-port |
| `identity/domain/port/in/AssignRoleUseCase.java` | Create | in-port |
| `identity/domain/port/in/AuthenticateUseCase.java` | Create | in-port |
| `identity/domain/port/in/ChangePasswordUseCase.java` | Create | in-port |
| `identity/domain/port/in/RefreshAccessTokenUseCase.java` | Create | in-port — added post-PR2 to close a hexagonal-consistency gap (see Decision: Refresh endpoint, updated) |
| `identity/domain/port/out/UserRepository.java` | Create | out-port |
| `identity/domain/port/out/UserRoleRepository.java` | Create | out-port (`existsByRoleType`, `findByUserId`) |
| `identity/domain/port/out/RefreshTokenRepository.java` | Create | out-port |
| `identity/domain/port/out/PersonRepository.java` | Create | out-port (load-only for use cases; save for seed) |
| `identity/domain/port/out/PasswordHasher.java` | Create | out-port (BCrypt) |
| `identity/domain/port/out/AccessTokenIssuer.java` | Create | out-port (jjwt) |
| `identity/domain/port/out/RefreshTokenGenerator.java` | Create | out-port (opaque random) |
| `identity/domain/port/out/LlaveMxOAuthPort.java` | Create | out-port (stub only) |
| `identity/domain/service/{CreateUser,AssignRole,Authenticate,ChangePassword,RefreshAccessToken}UseCaseImpl.java` | Create | 5 interactors, `@Transactional` |
| `identity/domain/service/TokenHashing.java` | Create | Pure SHA-256 util (or in `identity.shared`) |
| `identity/application/**` | Create | Command/result DTOs + mappers |
| `identity/infrastructure/persistence/*JpaRepository.java` + adapters | Create | Spring Data + out-port adapters |
| `identity/infrastructure/web/{Auth,User}Controller.java` | Create | Thin controllers |
| `identity/infrastructure/web/dto/*.java` | Create | Request/response DTOs (below) |
| `identity/infrastructure/web/GlobalExceptionHandler.java` | Create | `@RestControllerAdvice` |
| `identity/infrastructure/security/SecurityFilterConfig.java` | Create | `SecurityFilterChain` bean |
| `identity/infrastructure/security/JwtAuthenticationFilter.java` | Create | `OncePerRequestFilter` |
| `identity/infrastructure/security/JwtService.java` | Create | jjwt sign/parse |
| `identity/infrastructure/security/BcryptPasswordHasher.java` | Create | `PasswordHasher` adapter |
| `identity/infrastructure/external/LlaveMxOAuthStubAdapter.java` | Create | Stub adapter |
| `identity/infrastructure/bootstrap/AdminSeedRunner.java` | Create | `ApplicationRunner` seed |
| `identity/shared/exception/*.java` | Create | Domain exceptions (below) |
| `pom.xml` | Modify | Add `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `com.h2database:h2`, `io.jsonwebtoken:jjwt-{api,impl,jackson}` |
| `src/main/resources/application.properties` | Modify | H2 + JWT + seed config |

## Interfaces / Contracts

### REST endpoints

| Method + path | Auth | Request body | Success | Errors |
|---|---|---|---|---|
| `POST /auth/login` | public | `{username, password}` | 200 `{accessToken, refreshToken, tokenType:"Bearer", expiresIn:1800, mustChangePassword}` | 401 invalid; 423 locked |
| `POST /auth/refresh` | public | `{refreshToken}` | 200 `{accessToken, tokenType:"Bearer", expiresIn:1800}` | 401 invalid/expired/revoked; 423 owning user is LOCKED |
| `POST /auth/change-password` | Bearer | `{currentPassword, newPassword}` | 204 | 400 validation; 401 wrong current |
| `POST /users` | Bearer, ROLE_ADMIN | `{personId, temporaryPassword}` | 201 `{userId, username, mustChangePassword:true}` | 400; 403 must-change caller; 409 person already has user / no email |
| `POST /users/{userId}/roles` | Bearer, ROLE_ADMIN | `{roleId, divisionId?}` | 201 `{userRoleId, roleType, divisionId}` | 400 division rule; 403; 404 user |

`ErrorResponse` body: `{timestamp, status, error, message, path}`.

### Exception → HTTP mapping (`GlobalExceptionHandler`)

| Exception (`identity.shared.exception`) | Status |
|---|---|
| `InvalidCredentialsException` | 401 |
| `AccountLockedException` | 423 |
| `MustChangePasswordException` | 403 |
| `InvalidRefreshTokenException` | 401 |
| `DivisionRuleViolationException` | 400 |
| `PersonAlreadyHasUserException` / `MissingInstitutionalEmailException` | 409 |
| `UserNotFoundException` | 404 |
| `MethodArgumentNotValidException` (bean validation) | 400 |

### Security filter chain

Stateless (`SessionCreationPolicy.STATELESS`), CSRF disabled. `permitAll`:
`/auth/login`, `/auth/refresh`, `/h2-console/**` (dev). `/users/**` →
`hasRole("ADMIN")`; everything else authenticated. `JwtAuthenticationFilter`
runs before `UsernamePasswordAuthenticationFilter`: reads
`Authorization: Bearer <t>`, validates via `JwtService`, maps each `roles` claim
to `new SimpleGrantedAuthority("ROLE_" + roleName)`, sets `SecurityContext`.
BCrypt strength 12.

### application.properties (key entries)

```properties
spring.datasource.url=jdbc:h2:mem:sisa;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.username=sa
spring.jpa.hibernate.ddl-auto=create-drop
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
sisa.security.jwt.secret=${JWT_SECRET:dev-only-insecure-256bit-secret-change-me!!}
sisa.security.jwt.access-token-ttl=PT30M
sisa.security.jwt.refresh-token-ttl=P1D
sisa.security.bootstrap.admin.username=${SISA_ADMIN_USERNAME:}
sisa.security.bootstrap.admin.password=${SISA_ADMIN_PASSWORD:}
sisa.security.bootstrap.admin.curp=${SISA_ADMIN_CURP:}
```

## Testing Strategy (Strict TDD active — `./mvnw test` under JDK 21)

| Layer | What to test | Approach |
|---|---|---|
| Unit | `User` invariants: `registerFailedLogin` locks at 3, `assertCanOperate` throws when must-change, `changePassword` clears flag | JUnit 5 + AssertJ, no mocks |
| Unit | 4 use cases with mocked out-ports: auth success/wrong-password/lock/locked-rejects/first-access; create-user duplicate + missing-email + caller-guard; assign-role division rules; change-password wrong-current | Mockito |
| Unit | `JwtService` sign→parse round-trip; `TokenHashing` determinism; refresh generator | JUnit 5 |
| Integration | login → protected `POST /users` (ADMIN) → `POST /auth/refresh` → reuse new token; must-change blocks then `/auth/change-password` unblocks; 3 fails → 423; seed creates ADMIN once; refresh token issued pre-LOCKED is rejected once the user becomes LOCKED; refresh still succeeds for a mustChangePassword user | `@SpringBootTest` + real H2 + full Spring Security filter chain, `MockMvc` |
| E2E | — | Not available in this repo |

Write the failing test first for each use case and controller flow (Red → Green
→ Refactor). Every Maven invocation must set `JAVA_HOME` to
`C:/Users/JoseNarvaez/.jdks/corretto-21.0.10`.

## Migration / Rollout

No data migration — H2 in-memory is recreated on every boot (`create-drop`).
Additive new module; rollback = revert the change commit(s). No other module
depends on Identity yet.

## Open Questions

- [ ] Production DB (PostgreSQL vs MySQL) — deferred by proposal; must be decided
  before go-live. Persistence stays behind out-ports for an easy swap.
- [ ] Runtime Person provisioning for non-admin users arrives with
  Admission/Enrollment; this slice only seeds the ADMIN's Person + uses test
  fixtures.
