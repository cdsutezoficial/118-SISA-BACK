# Tasks: Academic Program Management (academic_config)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~900-1150 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | Batch 1 (Phases 1-4: kernel/domain/ports/use-cases) -> Batch 2 (Phases 5-8: persistence/web/security/doc-sync/verify) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending (no PR chain applies — local-only repo, no remote/PR flow; use as sdd-apply session/commit boundaries) |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

Note: 118-SISA-BACK is local-only git (no push/PR) — the "PR" split maps to sdd-apply session/commit boundaries on local `main`, not an actual PR chain.

### Suggested Work Units

| Unit | Goal | Likely Batch | Notes |
|------|------|-----------|-------|
| 1 | Shared enums + domain model + ports + 5 TDD use-case impls, no persistence/web wiring yet | Batch 1 | Standalone, compiles + unit-tests green in isolation |
| 2 | Persistence, web, security wiring, doc sync, full-suite verify | Batch 2 | Depends on Batch 1 |

## Phase 1: Shared Kernel

- [x] 1.1 Create `shared/model/AcademicLevel.java` — enum TSU, CONTINUIDAD, INGENIERIA, LICENCIATURA, POSGRADO
- [x] 1.2 Create `shared/model/ProgramModality.java` — enum PRESENCIAL, MIXTA

## Phase 2: Domain Model (TDD)

- [x] 2.1 RED: `AcademicProgramTest` — activate()/deactivate() invariants
- [x] 2.2 GREEN: `ProgramStatus` enum (ACTIVE/INACTIVE), own to this aggregate
- [x] 2.3 GREEN: `AcademicProgram` entity (id, divisionId, name, offerName, code, level, modality, continuityProgramId nullable, description, status; activate()/deactivate())

## Phase 3: Ports

- [x] 3.1 `CreateAcademicProgramUseCase` + Command + `AcademicProgramResult` record
- [x] 3.2 `UpdateAcademicProgramUseCase` + Command
- [x] 3.3 `ListAcademicProgramsUseCase` + Query(status, search, divisionId, page, size) + Result
- [x] 3.4 `GetAcademicProgramUseCase`
- [x] 3.5 `ChangeAcademicProgramStatusUseCase` + Command
- [x] 3.6 `AcademicProgramRepository` out-port (save/findById/findByCode/findByOfferNameAndModality/search→page)
- [x] 3.7-3.10 Module exceptions: `AcademicProgramNotFoundException`(404), `DuplicateProgramCodeException`(409), `DuplicateOfferNameModalityException`(409), `DivisionNotFoundException`(400)

## Phase 4: Use Case Implementations (TDD)

- [x] 4.1 `CreateAcademicProgramUseCaseImpl` — valid divisionId; missing/non-existent divisionId rejected; dup code rejected; dup (offerName,modality) rejected; same offerName+diff modality succeeds; continuityProgramId null/absent accepted
- [x] 4.2 `UpdateAcademicProgramUseCaseImpl` — success; missing/non-existent divisionId rejected; code conflict vs another program rejected; (offerName,modality) conflict vs another rejected; self-update with own values succeeds; status untouched
- [x] 4.3 `GetAcademicProgramUseCaseImpl` — found / not-found
- [x] 4.4 `ListAcademicProgramsUseCaseImpl` — default pagination; filter by divisionId; filter by status
- [x] 4.5 `ChangeAcademicProgramStatusUseCaseImpl` — deactivate active; reactivate inactive

## Phase 5: Persistence

- [x] 5.1 `AcademicProgramJpaRepository` — `@Query` search (divisionId/status/search filters), findByCode, findByOfferNameAndModality
- [x] 5.2 `AcademicProgramRepositoryAdapter` — sort by `name` ASC, maps nullable `continuityProgramId` column
- [x] 5.3 `AcademicProgramRepositoryAdapterSearchIT` (`@DataJpaTest`) — dual-uniqueness DB constraints (unique `code`; composite unique `offer_name`+`modality`), divisionId filter, status filter, search filter

## Phase 6: Web Layer

- [x] 6.1 DTOs: Create/Update/ChangeStatus requests, `AcademicProgramResponse`, `AcademicProgramListItemResponse`, `AcademicProgramListResponse`
- [x] 6.2 `AcademicProgramController` — POST(201)/PUT `{id}`/GET/GET `{id}`/PATCH `{id}`/status, mapped to `/programs`
- [x] 6.3 EXTEND existing `academic_config` `GlobalExceptionHandler` — add 4 `@ExceptionHandler` methods (additive only, no new class)
- [x] 6.4 EXTEND existing `academic_config` `UseCaseConfig` — add 5 `@Bean` methods

## Phase 7: Security

- [ ] 7.1 `SecurityFilterConfig.java` — add 4 verb-split `/programs` matchers (GET/POST/PUT/PATCH), ADMIN+SERVICIOS_ESCOLARES, placed after `/divisions` matchers, before `anyRequest()`
- [ ] 7.2 `AcademicProgramControllerIT` — ADMIN full CRUD; SERVICIOS_ESCOLARES full CRUD; other role 403; unauthenticated 401

## Phase 8: Documentation & Final Verify

- [ ] 8.1 `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` — add 5 "Puertos (in)" rows for the new `AcademicProgram` use cases (no field changes needed, already documented)
- [ ] 8.2 `./mvnw clean verify` — full suite green, zero regressions (~144 existing tests + new), all 17 spec scenarios covered
