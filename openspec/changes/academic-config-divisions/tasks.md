# Tasks: Academic Division Management (academic_config)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~950-1150 (net-new bounded context: 4 use cases, 4 exceptions, entity, 2 repos/adapters, controller, 6 DTOs, config, security, docs; plus a small refactor touching identity) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (Phase 0 refactor) -> PR 2 (Phases 1-3 domain+use cases) -> PR 3 (Phases 4-7 infra/web/security/docs) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending — no PR chain applies (local-only repo, no remote/PR flow as of 2026-07-06); use as sdd-apply session/commit boundaries instead |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

Note: `118-SISA-BACK` is local-only git (no push/PR) — the "PR" split above maps to **sdd-apply session/commit boundaries** on local `main`, not an actual PR chain. Use it to decide how many `sdd-apply` batches to run, not to open GitHub PRs.

### Suggested Work Units

| Unit | Goal | Likely Batch | Notes |
|------|------|-----------|-------|
| 1 | Phase 0: promote `ErrorResponse` to `shared`, full `identity` regression green | Batch 1 | Must land and verify green before any `academic_config` code exists |
| 2 | Phases 1-3: domain model + ports + use-case impls (TDD) | Batch 2 | Depends on Batch 1 (imports shared `ErrorResponse` transitively via later phases only, but keep sequence clean) |
| 3 | Phases 4-7: persistence, web, security, docs, final verify | Batch 3 | Depends on Batch 2 |

## Phase 0: Refactor — Promote `ErrorResponse` to `shared`

- [x] 0.1 Create `src/main/java/mx/edu/utez/sisa/shared/web/dto/ErrorResponse.java` with identical fields/shape as `identity/infrastructure/web/dto/ErrorResponse.java`
- [x] 0.2 Update `identity/infrastructure/web/GlobalExceptionHandler.java` import to `mx.edu.utez.sisa.shared.web.dto.ErrorResponse`
- [x] 0.3 Update `identity/infrastructure/security/SecurityFilterConfig.java` (`AuthenticationEntryPoint`) import to `mx.edu.utez.sisa.shared.web.dto.ErrorResponse`
- [x] 0.4 Delete `identity/infrastructure/web/dto/ErrorResponse.java`
- [x] 0.5 Run full existing `identity` suite (`./mvnw clean verify`) — confirm `GlobalExceptionHandlerTest`, `AuthenticationEntryPointTest`, `AuthFlowIT`, `UserControllerTest`, `AuthControllerTest`, and all others are green with zero regressions before proceeding to Phase 1

## Phase 1: Domain Model (TDD)

- [x] 1.1 RED: `academic_config/domain/model/AcademicDivisionTest.java` — invariants for `activate()`/`deactivate()` (idempotency, status transitions)
- [x] 1.2 GREEN: `academic_config/domain/model/DivisionStatus.java` — enum `ACTIVE`/`INACTIVE`
- [x] 1.3 GREEN: `academic_config/domain/model/AcademicDivision.java` — `@Entity @Table(name="academic_division")`: `id, name, code, description, directorPersonId, status`; `activate()/deactivate()`

## Phase 2: Ports

- [x] 2.1 Create `academic_config/domain/port/in/CreateAcademicDivisionUseCase.java` + `CreateAcademicDivisionCommand`, `AcademicDivisionResult`
- [x] 2.2 Create `academic_config/domain/port/in/UpdateAcademicDivisionUseCase.java` + `UpdateAcademicDivisionCommand`
- [x] 2.3 Create `academic_config/domain/port/in/ListAcademicDivisionsUseCase.java` + `ListAcademicDivisionsQuery`, `ListAcademicDivisionsResult`, `DivisionSummary`
- [x] 2.4 Create `academic_config/domain/port/in/ChangeAcademicDivisionStatusUseCase.java` + `ChangeStatusCommand(callerId, divisionId, DivisionStatus target)`
- [x] 2.5 Create `academic_config/domain/port/out/AcademicDivisionRepository.java` — `save`, `findById`, `findByCode`, `findByName`, `search(criteria)` + `DivisionSearchCriteria`/`DivisionSearchPage` records
- [x] 2.6 Create `academic_config/domain/port/out/PersonLookupPort.java` — `boolean existsById(UUID)`
- [x] 2.7 Create `academic_config/shared/exception/AcademicDivisionNotFoundException.java` (404)
- [x] 2.8 Create `academic_config/shared/exception/DuplicateDivisionCodeException.java` (409)
- [x] 2.9 Create `academic_config/shared/exception/DuplicateDivisionNameException.java` (409)
- [x] 2.10 Create `academic_config/shared/exception/DirectorNotFoundException.java` (400)

## Phase 3: Use Case Implementations (TDD)

- [x] 3.1 RED/GREEN: `CreateAcademicDivisionUseCaseImplTest` + `CreateAcademicDivisionUseCaseImpl` — scenarios: creation with director, without director, rejects duplicate code, rejects duplicate name, rejects non-existent director
- [x] 3.2 RED/GREEN: `UpdateAcademicDivisionUseCaseImplTest` + `UpdateAcademicDivisionUseCaseImpl` — scenarios: successful update, rejects code conflict with another division, rejects non-existent director, `status` untouched
- [x] 3.3 RED/GREEN: `ListAcademicDivisionsUseCaseImplTest` + `ListAcademicDivisionsUseCaseImpl` — scenarios: default pagination, every item `programCount = 0`
- [x] 3.4 RED/GREEN: `ChangeAcademicDivisionStatusUseCaseImplTest` + `ChangeAcademicDivisionStatusUseCaseImpl` — scenarios: deactivate active division, reactivate inactive division

## Phase 4: Infrastructure / Persistence

- [x] 4.1 Create `academic_config/infrastructure/persistence/AcademicDivisionJpaRepository.java` — `@Query search` (status + free-text name/code), `findByCode`/`findByName`
- [x] 4.2 Create `academic_config/infrastructure/persistence/AcademicDivisionRepositoryAdapter.java` — implements `AcademicDivisionRepository`, sort by `name` ASC
- [x] 4.3 Create `academic_config/infrastructure/persistence/PersonLookupJpaRepository.java` — `JpaRepository<Person, UUID>` over shared `person` table
- [x] 4.4 Create `academic_config/infrastructure/persistence/PersonLookupAdapter.java` — implements `PersonLookupPort.existsById`
- [x] 4.5 RED/GREEN: `AcademicDivisionRepositoryAdapterSearchIT` (`@DataJpaTest`, mirroring `identity/infrastructure/persistence/UserRepositoryAdapterSearchIT`) — covers search filter, pagination, sort, case-insensitive uniqueness on `name`/`code`

## Phase 5: Web Layer

- [x] 5.1 Create DTOs: `CreateAcademicDivisionRequest`, `UpdateAcademicDivisionRequest`, `ChangeDivisionStatusRequest`, `AcademicDivisionResponse`, `AcademicDivisionListItemResponse` (incl. `programCount`), `AcademicDivisionListResponse` under `academic_config/infrastructure/web/dto/`
- [x] 5.2 Create `academic_config/infrastructure/web/AcademicDivisionController.java` — `POST /divisions` (201), `PUT /divisions/{id}`, `GET /divisions`, `PATCH /divisions/{id}/status`
- [x] 5.3 Create `academic_config/infrastructure/web/GlobalExceptionHandler.java` — maps only this module's 4 exceptions to HTTP status; imports shared `mx.edu.utez.sisa.shared.web.dto.ErrorResponse`; no generic handler re-declaration
- [x] 5.4 Create `academic_config/infrastructure/config/UseCaseConfig.java` — `@Configuration` wiring the 4 `*UseCaseImpl` beans with their out-ports

## Phase 6: Security

- [x] 6.1 Modify `identity/infrastructure/security/SecurityFilterConfig.java` — add 4 split `/divisions` matchers (GET, POST, PUT, PATCH), all `hasAnyRole("ADMIN", "SERVICIOS_ESCOLARES")`, placed before `anyRequest().authenticated()`
- [x] 6.2 RED/GREEN: `AcademicDivisionControllerIT` (`@SpringBootTest` + `MockMvc`, mirroring `UserControllerTest`/`AuthenticationEntryPointTest`) — ADMIN full CRUD, SERVICIOS_ESCOLARES full CRUD (create/update/list/activate/deactivate), other-role 403, unauthenticated 401

## Phase 7: Domain-Doc Sync

- [x] 7.1 Update `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` — add `description` field to `AcademicDivision`
- [x] 7.2 Update same doc — add explicit `code` uniqueness constraint (doc currently documents only `name` uniqueness)
- [x] 7.3 Update same doc — add 4 "Puertos (in)" rows: `CreateAcademicDivisionUseCase`, `UpdateAcademicDivisionUseCase`, `ListAcademicDivisionsUseCase`, `ChangeAcademicDivisionStatusUseCase`

## Final Verify

- [x] 8.1 Run `./mvnw clean verify` — full suite green, zero regressions in `identity`, all `academic_config` tests (18 spec scenarios) passing
