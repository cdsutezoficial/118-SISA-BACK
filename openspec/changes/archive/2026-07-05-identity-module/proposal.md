# Proposal: Identity Module — Minimal Vertical Slice

## Intent

First backend feature in `118-SISA-BACK`. SISAv2 needs a working authentication
foundation before any other module: every workflow (admission, enrollment,
grades…) requires authenticated, role-scoped users. Today the repo is a bare
Spring skeleton — no user, no login, no roles. This slice proves the hexagonal
module layout end-to-end while delivering the minimum needed to create a user,
give them a role, log in, and change the mandatory first-access password.

## Scope

### In Scope
- 4 use cases (ports-in + service + REST adapter): `CreateUserUseCase`,
  `AssignRoleUseCase`, `AuthenticateUseCase` (login → access + refresh JWT), `ChangePasswordUseCase`.
- `User` aggregate + `UserRole` with domain invariants (mustChangePassword blocks
  all ops but password change; failedLoginAttempts>=3 auto-LOCKED; one User per Person).
- `RefreshToken` entity: hash-stored (never the plain token), 1-day TTL, revocable —
  mirrors the existing `PasswordResetToken` pattern for consistency. A refresh
  endpoint exchanges a valid refresh token for a new access token.
- `LlaveMxOAuthPort` out-port interface + **stub/mock** adapter (no real integration).
- JPA persistence adapters on **H2 in-memory** (dev-only).
- `CommandLineRunner` bootstrap seed: one ADMIN if none exists, credentials from env vars.
- Bcrypt password hashing; JWT issuance on login via Spring Security (`AuthenticationManager`,
  `UserDetailsService`, `PasswordEncoder`) + `jjwt` for token signing/parsing.

### Out of Scope (deferred to future changes)
- `RequestPasswordResetUseCase` / `ResetPasswordUseCase` + `NotificationPort` (email reset flow).
- `LinkLlaveMxUseCase` and real LlaveMX OAuth integration (no Gobierno de Morelos credentials yet).
- `DeactivateUserUseCase`, `UnlockUserUseCase`.
- Production database choice (PostgreSQL vs MySQL) — **undecided, flagged gap**.
- Cross-module `CreateUserUseCase` invocation from Enrollment/Faculty.

## Capabilities

### New Capabilities
- `user-account-management`: create user, assign role, bootstrap ADMIN seed.
- `authentication`: local credential login returning JWT, first-access password change.

### Modified Capabilities
- None.

## Approach

Hexagonal per `00-ARQUITECTURA.md`: `domain/model|port/in|port/out|service`,
`application`, `infrastructure/persistence|web|external`, `shared` under
`mx.edu.utez.sisa.identity`. One `XxxUseCase` implements one `XxxPort`; thin
controllers; transactions only in service layer. LlaveMX port exists but adapter
is a stub. H2 in-memory for now.

**Auth mechanism (decided)**: Spring Security handles credential validation
(`AuthenticationManager`/`UserDetailsService`/`BCryptPasswordEncoder`) and the
security filter chain; `jjwt` handles JWT signing/parsing (Spring Security's
OAuth2 Resource Server module is for *validating* tokens from an external
Authorization Server, not for self-issuing them, so it doesn't fit here).
- **Access token TTL**: 30 minutes. **Claims**: `sub` (userId) + `roles` (RoleType
  names only — no `divisionId`; division-scoped authorization is resolved by
  querying `UserRole` directly when a division-scoped module needs it, avoiding
  stale-claim risk).
- **Refresh token TTL**: 1 day, hash-stored, revocable (see `RefreshToken` in Scope).
  No rotation-on-use in this slice (deferred — single refresh token valid until
  TTL or explicit revocation).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/mx/edu/utez/sisa/identity/**` | New | Full module (domain→infra) |
| `src/main/java/mx/edu/utez/sisa/shared/**` | New | `Person`, `RoleType` (as needed by slice) |
| `pom.xml` | Modified | Add JPA, H2, Security/JWT, validation deps |
| `application.properties` | Modified | H2 + JWT + seed env config |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| H2 hides prod-DB constraints | Med | Flag as known gap; keep persistence behind ports for easy swap |
| Refresh token theft (1-day window, no rotation) | Low | Hash-stored + revocable; rotation-on-use deferred, revisit if abuse observed |
| Bootstrap ADMIN with weak/default creds | Med | Env-var creds; mustChangePassword=true; document rotation |
| Stub LlaveMX diverges from real API | Low | Isolate behind `LlaveMxOAuthPort` |

## Rollback Plan

New module, additive only. Revert the change commit(s); no schema migration
(H2 in-memory recreated on each boot). No other module depends on it yet.

## Dependencies

- Shared kernel `Person` + `RoleType` (defined in domain docs; created as needed).
- JDK 21 for `./mvnw test`/`package` (JAVA_HOME gotcha — see project.md).

## Success Criteria

- [ ] ADMIN seed created at startup when DB empty (env-var creds).
- [ ] ADMIN can create a user (mustChangePassword=true) and assign a role (division rule enforced).
- [ ] User logs in → receives access token (30 min) + refresh token (1 day); 3 failed attempts → LOCKED.
- [ ] Refresh token exchanges for a new access token without re-entering credentials, until it expires or is revoked.
- [ ] mustChangePassword=true blocks all ops except ChangePassword; after change, login works normally.
- [ ] `./mvnw clean package` + `./mvnw test` green under JDK 21.

## Open Decisions for Design Phase

- Exact H2 config (mode, console, schema init) and how `Person`/FK is represented in-slice.
- Where the mustChangePassword gate is enforced (service guard vs security filter)
  and how a LOCKED/mustChangePassword user's refresh-token attempts are handled.
- Refresh token endpoint shape (dedicated `/auth/refresh` vs folded into login response).
- Real production DB choice (deferred — must be decided before go-live).

## Resolved Decisions (settled by PO, 2026-07-05)

- JWT library: `jjwt`. Auth framework: Spring Security (not OAuth2 Resource Server,
  not a custom-only filter).
- Access token TTL: 30 min. Claims: `sub` + `roles` (no `divisionId`).
- Refresh token TTL: 1 day. Storage: hashed, revocable, mirrors `PasswordResetToken`.
