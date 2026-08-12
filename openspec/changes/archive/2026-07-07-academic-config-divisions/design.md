# Design: AcademicDivision CRUD (academic_config — first slice)

## Technical Approach

Net-new bounded context `mx.edu.utez.sisa.academic_config`, mirroring `identity`'s hexagonal layout exactly: framework-agnostic domain (JPA annotations on the aggregate, no Lombok, no Spring stereotypes), ports in/out, plain use-case interactors wired by a per-module composition root, thin JPA adapters and controllers. As the FIRST context after `identity`, this design fixes the second-module conventions the proposal deferred: per-module `UseCaseConfig`, per-module `@RestControllerAdvice`, own cross-context query port for director validation, and a centralized (single) security filter chain that each module extends by adding matchers.

## Architecture Decisions

### Decision: Per-module composition root (own `UseCaseConfig`)
**Choice**: `academic_config/infrastructure/config/UseCaseConfig.java` — a sibling `@Configuration` that `@Bean`-wires each `*UseCaseImpl` with its out-ports.
**Alternatives**: extend/append to `identity`'s `UseCaseConfig`; annotate impls with `@Service`.
**Rationale**: `identity`'s impls are deliberately annotation-free and wired in one place. A sibling config keeps modules independently removable (rollback = delete the package) and mirrors the established pattern. Cross-module bean sharing is avoided.

### Decision: Director validation via own out-port, not `identity.PersonRepository`
**Choice**: `academic_config` defines `PersonLookupPort` (`boolean existsById(UUID)`) in its own `domain/port/out`, adapted by a `PersonLookupAdapter` over its own `PersonLookupJpaRepository extends JpaRepository<Person, UUID>` on the shared `person` table.
**Alternatives**: inject `identity`'s `PersonRepository` out-port; JOIN across modules.
**Rationale**: `config.yaml` design rule forbids cross-module repository access — communicate via query ports only. `Person` is shared kernel (`shared.model.Person`) so referencing the entity + mapping a read-only repository over the shared table is legal; importing `identity`'s port is not. Minimal `existsById` contract keeps the coupling to "validate presence" only.

### Decision: Split GET/write security matchers for `/divisions` (revised per PO feedback)
**Choice**: separate matchers per verb instead of one blanket `/divisions/**`:
```java
.requestMatchers(HttpMethod.GET, "/divisions", "/divisions/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
.requestMatchers(HttpMethod.POST, "/divisions").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
.requestMatchers(HttpMethod.PUT, "/divisions/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
.requestMatchers(HttpMethod.PATCH, "/divisions/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
```
Both role sets are identical TODAY — the point is structural, not functional yet.
**Alternatives**: one blanket `/divisions/**` matcher for all methods (original design draft).
**Rationale**: PO flagged that read-only roles are coming later (catalog-consuming roles that shouldn't get write access). Both matchers grant the same two roles TODAY, but splitting them now means a future read-only role is a one-line addition to the GET matcher — the write matcher and everything else stays untouched. Same structural shape as the `/users` GET-vs-blanket split, just with a different role set on each side.

### Decision: Single status-change use case + idempotent `PATCH`
**Choice**: one `ChangeAcademicDivisionStatusUseCase` (command carries target `DivisionStatus`), exposed as `PATCH /divisions/{id}/status` with body `{ "status": "ACTIVE" | "INACTIVE" }`. Fulfills both the proposal's "Activate" and "Deactivate" capabilities.
**Alternatives**: two use cases + two `POST /divisions/{id}/activate|deactivate` endpoints.
**Rationale**: activate/deactivate is one state transition parameterized by target — one interactor removes duplicated logic; one idempotent endpoint maps cleanly onto the frontend `Switch` toggle the proposal reuses.

### Decision: Own `@RestControllerAdvice`, additive only
**Choice**: `academic_config/infrastructure/web/GlobalExceptionHandler` maps ONLY this module's exceptions. Generic handlers (`MethodArgumentNotValid`→400, type-mismatch→400, catch-all→500) already exist app-globally in `identity`'s advice and are NOT re-declared (duplicate `@ExceptionHandler` for one type is ambiguous).
**Rationale**: keeps envelope consistent without redeclaring shared behavior.

### Decision: Promote `ErrorResponse` to `shared` now, not duplicate-then-promote-later (revised per PO feedback)
**Choice**: move `ErrorResponse` from `identity.infrastructure.web.dto.ErrorResponse` to `mx.edu.utez.sisa.shared.web.dto.ErrorResponse`. `identity`'s `GlobalExceptionHandler`/`SecurityFilterConfig` (the `AuthenticationEntryPoint`) update their import; `academic_config`'s `GlobalExceptionHandler` imports the same shared type — no local copy.
**Alternatives**: keep `ErrorResponse` local to `identity`, duplicate an identical record into `academic_config` now, promote to `shared` "once a 3rd context appears" (original design draft).
**Rationale**: `ErrorResponse` is a pure data record — `{timestamp, status, error, message, path}`, zero business logic, zero behavior. This is NOT the same situation as `PersonRepository` (a real port with business meaning that `config.yaml`'s "no cross-module repository access" rule exists to protect) — it's a cross-cutting response envelope, same category as `Person` already sitting in `shared.model`. There will be 9 bounded contexts total (`config.yaml`'s context line); duplicating a shared shape into every one of them and promoting "later" guarantees N-1 avoidable duplications and a harder mechanical cleanup down the line. Moving it now costs one file move + two import updates in already-shipped, already-tested `identity` code — cheap today, only gets more expensive the more modules exist. `academic_config` never creates its own copy.
**Impact on already-shipped `identity` code**: this touches working, tested, archived-module code — treat as a small refactor within this change, run the full `identity` suite after moving (no behavior change expected, purely a package move) before touching `academic_config`.

### Decision: `DivisionStatus` is its own enum
`ACTIVE`/`INACTIVE` only. NOT shared with `UserStatus` (which adds `LOCKED`) — different aggregate lifecycles; sharing would couple unrelated domains. Stated explicitly rather than silently duplicated.

## Data Flow

    POST/PUT/PATCH/GET /divisions ──▶ AcademicDivisionController (thin)
         │ Command/Query records
         ▼
    *UseCaseImpl (domain/service) ──▶ AcademicDivisionRepository (out-port)
         │                          └▶ PersonLookupPort (validate directorPersonId)
         ▼                               │
    AcademicDivisionResponse ◀───── Adapters ──▶ H2 (academic_division / person)

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `academic_config/domain/model/AcademicDivision.java` | Create | `@Entity @Table(name="academic_division")` aggregate: `id, name, code, description, directorPersonId, status`; `activate()/deactivate()` behavior |
| `academic_config/domain/model/DivisionStatus.java` | Create | Enum ACTIVE/INACTIVE |
| `academic_config/domain/port/in/CreateAcademicDivisionUseCase.java` | Create | + `CreateAcademicDivisionCommand`, `AcademicDivisionResult` records |
| `academic_config/domain/port/in/UpdateAcademicDivisionUseCase.java` | Create | + `UpdateAcademicDivisionCommand` |
| `academic_config/domain/port/in/ListAcademicDivisionsUseCase.java` | Create | + `ListAcademicDivisionsQuery`, `ListAcademicDivisionsResult`, `DivisionSummary` (incl. `programCount`) |
| `academic_config/domain/port/in/ChangeAcademicDivisionStatusUseCase.java` | Create | + `ChangeStatusCommand(callerId, divisionId, DivisionStatus target)` |
| `academic_config/domain/port/out/AcademicDivisionRepository.java` | Create | `save`, `findById`, `findByCode`, `findByName`, `search(criteria)`→page |
| `academic_config/domain/port/out/PersonLookupPort.java` | Create | `boolean existsById(UUID)` |
| `academic_config/domain/service/*UseCaseImpl.java` (4) | Create | Plain interactors; uniqueness + director-existence checks |
| `academic_config/shared/exception/*` | Create | `AcademicDivisionNotFoundException` (404), `DuplicateDivisionCodeException`/`DuplicateDivisionNameException` (409), `DirectorNotFoundException` (400) |
| `academic_config/infrastructure/persistence/AcademicDivisionJpaRepository.java` | Create | `@Query search` (status + free-text name/code), `findByCode/findByName` |
| `academic_config/infrastructure/persistence/AcademicDivisionRepositoryAdapter.java` | Create | Adapter, sort by `name` ASC |
| `academic_config/infrastructure/persistence/PersonLookupJpaRepository.java` + `PersonLookupAdapter.java` | Create | Read-only over shared `person` table |
| `academic_config/infrastructure/web/AcademicDivisionController.java` | Create | `@RequestMapping("/divisions")`: POST(201)/PUT/{id}/GET/PATCH/{id}/status |
| `academic_config/infrastructure/web/dto/*` | Create | `CreateAcademicDivisionRequest`, `UpdateAcademicDivisionRequest`, `ChangeDivisionStatusRequest`, `AcademicDivisionResponse`, `AcademicDivisionListItemResponse` (`programCount`), `AcademicDivisionListResponse` — NOT `ErrorResponse`, now shared (see below) |
| `academic_config/infrastructure/web/GlobalExceptionHandler.java` | Create | Module exceptions → HTTP; additive only; imports shared `ErrorResponse` |
| `academic_config/infrastructure/config/UseCaseConfig.java` | Create | Composition root (4 beans) |
| `shared/web/dto/ErrorResponse.java` | Move | From `identity.infrastructure.web.dto.ErrorResponse` — no field/shape change, package move only |
| `identity/infrastructure/web/GlobalExceptionHandler.java` | Modify | Update `ErrorResponse` import to `shared.web.dto` |
| `identity/infrastructure/web/dto/ErrorResponse.java` | Delete | Superseded by the shared copy |
| `identity/infrastructure/security/SecurityFilterConfig.java` | Modify | Update `AuthenticationEntryPoint`'s `ErrorResponse` import; add split GET/write `/divisions` matchers (see below) |
| `118-SISA-CLAUDE/.../02-config-academica.md` | Modify | Add `description` row + `code` uniqueness note (per spec phase finding) + 4 "Puertos (in)" rows |

## Interfaces / Contracts

```java
// out-port — no child collection in this slice, so NO EXISTS-subquery needed
// (contrast UserRepository.search); a plain filtered page suffices.
record DivisionSearchCriteria(DivisionStatus status, String search, int page, int size) {}
record DivisionSearchPage(List<AcademicDivision> content, long totalElements, int totalPages) {}

// programCount is a hardcoded 0 stub (no AcademicProgram yet) — HU-PROG-010.
record DivisionSummary(UUID id, String name, String code, String description,
                       UUID directorPersonId, DivisionStatus status, int programCount) {}
```

### Security matcher (order matters)
```java
.requestMatchers("/auth/login", "/auth/refresh", "/h2-console/**").permitAll()
.requestMatchers(HttpMethod.GET, "/users").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
.requestMatchers("/users/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.GET, "/divisions", "/divisions/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
.requestMatchers(HttpMethod.POST, "/divisions").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
.requestMatchers(HttpMethod.PUT, "/divisions/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
.requestMatchers(HttpMethod.PATCH, "/divisions/**").hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")
.anyRequest().authenticated()
```
Single app-wide `SecurityFilterChain` stays in `identity`. `/divisions` gets FOUR separate matchers (GET, POST, PUT, PATCH) instead of one blanket rule — both roles get identical access on every verb TODAY (still a deliberate divergence from `/users`' narrower read/write role split), but splitting by verb now means a future read-only-only role is a one-line addition to just the GET matcher. All four MUST precede `anyRequest()`.

## Testing Strategy

| Layer | What | Approach |
|-------|------|----------|
| Unit | 4 use-case impls (mocked ports): create uniqueness/director-validation, update, list `programCount=0`, status change | JUnit5 + Mockito + AssertJ under `academic_config/domain/service/` |
| Unit | `AcademicDivisionTest` — activate/deactivate, invariants | `academic_config/domain/model/` |
| Integration | `AcademicDivisionRepositoryAdapterIT` — search filter/pagination/sort, case-insensitive uniqueness | `@DataJpaTest` mirroring `UserRepositoryAdapterSearchIT` |
| Integration | `AcademicDivisionControllerIT` — endpoints, validation 400s, mapping; ADMIN & SERVICIOS_ESCOLARES full CRUD, other role 403, unauthenticated 401 | `@SpringBootTest` + `MockMvc` mirroring `UserControllerTest`/`AuthenticationEntryPointTest` |

Strict TDD: each impl/adapter/controller task pairs with a preceding failing test.

## Migration / Rollout

No migration. H2 `create-drop` auto-creates `academic_division`; no Flyway. Additive — rollback = delete package + revert the one security matcher and the doc edit.

## Open Questions

- [ ] `directorPersonId` stays null until a Person-provisioning API exists (known fast-follow, per proposal).

## Revisions

- **2026-07-07 (PO review)**: two changes made after José reviewed the initial design — (1) `/divisions` security matchers split by HTTP verb instead of one blanket rule, anticipating future read-only roles; (2) `ErrorResponse` promoted to `shared.web.dto` instead of duplicated per module, since it's a pure data shape (not a business port) and 9 bounded contexts are coming per `config.yaml` — duplicate-then-promote-later would have compounded across all of them.
