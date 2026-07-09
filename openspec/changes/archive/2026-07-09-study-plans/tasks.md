# Tasks: Study Plan Management (AcademicPlan + PlanLevel + Subject)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1900-2400 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | Batch 1 (Phases 1-3: domain/ports/root use-cases) -> Batch 2 (Phase 4: child use-cases) -> Batch 3 (Phases 5-8: persistence/web/security/docs/verify) |
| Delivery strategy | auto-chain (established precedent: same sequential-commit pattern used for academic-config-divisions and academic-config-programs) |
| Chain strategy | pending (no PR chain applies — local-only repo, no remote/PR flow; maps to sdd-apply batch/commit boundaries on local `main`) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

Note: 118-SISA-BACK is local-only git (no push/PR). "Chained PRs" here means sequential batch commits directly to local `main`, one per batch, identical to how `academic-config-programs` (8 phases, 2 batches) and `academic-config-divisions` were delivered. No confirmation needed before apply — this is the established repo convention.

### Suggested Work Units

| Unit | Goal | Batch | Notes |
|------|------|-------|-------|
| 1 | `AcademicPlan`/`PlanLevel`/`Subject` domain model (TDD) + 11 ports + 9 exceptions + 5 root CRUD use-case impls | Batch 1 | Standalone, compiles + unit tests green in isolation, no persistence/web wiring |
| 2 | 6 child-entity use-case impls (levels + subjects), exercised through `AcademicPlanRepository` mock | Batch 2 | Depends on Batch 1 ports/domain |
| 3 | Persistence, web (11 endpoints), security matchers, doc sync, full-suite verify | Batch 3 | Depends on Batch 1 + Batch 2 |

## Phase 1: Domain Model (TDD) — COMPLETE (Batch 1)

- [x] 1.1 RED: `AcademicPlanTest` — dup `levelNumber`/`code`, remove-level blocked (has subjects / == `socialServiceMinLevelId`), `addSubject` rejects a `levelId` from another plan
- [x] 1.2 GREEN: `PlanStatus`, `PlanLevelType` (REGULAR/INTERNSHIP), `SubjectType` (CORE/ELECTIVE/INTERNSHIP) enums
- [x] 1.3 GREEN: `PlanLevel` entity (`levelNumber`, `type`, `description`, `@ManyToOne` back-ref to plan; `subjects` `@OrderBy("displayOrder ASC")`)
- [x] 1.4 GREEN: `Subject` entity (`code`, `name`, `credits`, `weeklyHours`, `evaluationUnits`, `displayOrder`, `type`, `isRetakeable` default `true`, `classificationId`, denormalized `planId`; `@ManyToOne` back-ref to level)
- [x] 1.5 GREEN: `AcademicPlan` aggregate — fields per spec (`programId`, `version`, `validityPeriod`, `titulationKey`, `effectiveFrom`, `totalLevels`, `minPassingGrade` [0,10], `maxExtraordinaryExamsPerPeriod` >=0, `requiresSocialService`, `socialServiceMinLevelId`, `status`) + `activate()`/`deactivate()` + `addLevel`/`updateLevel`/`removeLevel`/`addSubject`/`updateSubject`/`removeSubject` mutators; `levels` `@OrderBy("levelNumber ASC")`

## Phase 2: Ports & Exceptions — COMPLETE (Batch 1)

- [x] 2.1 Root CRUD in-ports (5): `CreateAcademicPlanUseCase`+Command+`AcademicPlanResult`, `UpdateAcademicPlanUseCase`+Command, `GetAcademicPlanUseCase`, `ListAcademicPlansUseCase`+Query(programId,status,search,page,size)+Result, `ChangeAcademicPlanStatusUseCase`+Command
- [x] 2.2 Child in-ports (6): `AddPlanLevelUseCase`+Command, `UpdatePlanLevelUseCase`+Command, `RemovePlanLevelUseCase`, `AddSubjectToPlanUseCase`+Command, `UpdateSubjectUseCase`+Command, `RemoveSubjectUseCase`
- [x] 2.3 `AcademicPlanRepository` out-port (save/findById/findByProgramIdAndVersion/search→page)
- [x] 2.4 Exceptions (9 planned + 1 added): `AcademicPlanNotFoundException`(404), `ProgramNotFoundException`(400, FK-check variant), `PlanLevelNotFoundException`(404), `SubjectNotFoundException`(404), `PlanLevelHasSubjectsException`(409), `PlanLevelInUseException`(409), `DuplicateLevelNumberException`(409), `DuplicateSubjectCodeException`(409), `InvalidSocialServiceLevelException`(400) — **plus `DuplicatePlanVersionException`(409), added during apply**: the original 9-exception list omitted an exception for the spec's "version MUST be unique within the same programId" requirement (multiple explicit reject scenarios). See deviation note in apply-progress.

## Phase 3: Root Use Case Implementations (TDD) — COMPLETE (Batch 1)

- [x] 3.1 `CreateAcademicPlanUseCaseImpl` — valid `programId`; missing/non-existent `programId` rejected; dup `version` within `programId` rejected; same `version` diff program succeeds; `requiresSocialService=true` + non-null `socialServiceMinLevelId` at creation rejected; `requiresSocialService=false` + non-null `socialServiceMinLevelId` rejected; out-of-range `minPassingGrade` rejected
- [x] 3.2 `UpdateAcademicPlanUseCaseImpl` — success; dup `version` vs another plan same program rejected; `socialServiceMinLevelId` referencing own plan's level accepted; referencing another plan's level rejected; `programId` change rejected (command has no `programId` field — see deviation note in apply-progress)
- [x] 3.3 `GetAcademicPlanUseCaseImpl` — found with levels+subjects / not-found
- [x] 3.4 `ListAcademicPlansUseCaseImpl` — default returns all statuses; filter by `programId`; filter by `status`
- [x] 3.5 `ChangeAcademicPlanStatusUseCaseImpl` — deactivate active; reactivate inactive (independent of other plans under same `programId`)

## Phase 4: Child Use Case Implementations (TDD) — COMPLETE (Batch 2)

- [x] 4.1 `AddPlanLevelUseCaseImpl` — success; `levelNumber` outside `[1,totalLevels]` rejected; duplicate `levelNumber` rejected
- [x] 4.2 `UpdatePlanLevelUseCaseImpl` — success; level not found rejected
- [x] 4.3 `RemovePlanLevelUseCaseImpl` — success; rejected when == `socialServiceMinLevelId`; rejected when level still has subjects
- [x] 4.4 `AddSubjectToPlanUseCaseImpl` — success; `planLevelId` from a different plan rejected; duplicate `code` within plan rejected; `classificationId` accepted without existence validation
- [x] 4.5 `UpdateSubjectUseCaseImpl` — success; subject not found rejected
- [x] 4.6 `RemoveSubjectUseCaseImpl` — removes subject from its level

## Phase 5: Persistence — COMPLETE (Batch 3)

- [x] 5.1 `AcademicPlanJpaRepository` — `@Query` search (programId/status/search filters), `findByProgramIdAndVersion`
- [x] 5.2 `AcademicPlanRepositoryAdapter` — sort by `version` ASC, maps full plan graph (levels+subjects) via cascade
- [x] 5.3 `AcademicPlanRepositoryAdapterSearchIT` (`@DataJpaTest`) — unique `(program_id, version)` DB constraint, `programId` filter, `status` filter, `search` filter, cascade persists levels/subjects on save

## Phase 6: Web Layer — COMPLETE (Batch 3)

- [x] 6.1 DTOs: Create/Update/ChangeStatus plan requests, Add/Update level requests, Add/Update subject requests, `AcademicPlanResponse` (nested levels/subjects), `AcademicPlanListItemResponse`, `AcademicPlanListResponse`, `PlanLevelResponse`, `SubjectResponse`
- [x] 6.2 `AcademicPlanController` — 11 endpoints: POST/PUT `{id}`/GET/GET `{id}`/PATCH `{id}`/status on `/plans`; POST/PUT `{levelId}`/DELETE `{levelId}` on `/plans/{id}/levels`; POST/PUT `{subjectId}`/DELETE `{subjectId}` on `/plans/{id}/levels/{levelId}/subjects`
- [x] 6.3 EXTEND existing `academic_config` `GlobalExceptionHandler` — add 10 `@ExceptionHandler` methods grouped into 3 handler methods (additive only; 10 not 9 — includes `DuplicatePlanVersionException` per the Batch 1 deviation)
- [x] 6.4 EXTEND existing `academic_config` `UseCaseConfig` — add 11 `@Bean` methods

## Phase 7: Security — COMPLETE (Batch 3)

- [x] 7.1 `SecurityFilterConfig.java` — add `/plans/**` matchers (GET/POST/PUT/PATCH/DELETE, incl. nested `/levels` and `/subjects` paths), ADMIN+SERVICIOS_ESCOLARES, placed after `/programs` matchers, before `anyRequest()`
- [x] 7.2 `AcademicPlanControllerIT` — ADMIN full CRUD incl. nested level/subject flows; SERVICIOS_ESCOLARES full write access; other role 403; unauthenticated 401

## Phase 8: Documentation & Final Verify — COMPLETE (Batch 3)

- [x] 8.1 `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` — replaced the 3 placeholder "Puertos (in)" rows with the complete 11 `AcademicPlan`/`PlanLevel`/`Subject` use-case rows
- [x] 8.2 `./mvnw clean verify` — full suite green, zero regressions: 228 unit tests (surefire) + 57 integration tests (failsafe) = 285 total, all 33 spec scenarios covered
