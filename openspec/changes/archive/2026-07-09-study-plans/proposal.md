# Proposal: Study Plan Management (AcademicPlan CRUD)

## Intent

`academic_config` has `AcademicDivision` and `AcademicProgram` implemented but no way to define the curriculum (Plan de Estudios) that actually drives Inscripciones, Calificaciones, and Egreso/Titulación downstream. Without `AcademicPlan`, a program exists as a catalog entry with no levels/subjects to enroll students into or grade. This change adds the third `academic_config` aggregate, the first with an owned child-entity graph (`PlanLevel` → `Subject`).

## Decisions (binding, resolved by PO — do not re-litigate)

| # | Decision | Deviates from |
|---|----------|---------------|
| 1 | `AcademicPlan.programId` → exactly ONE `AcademicProgram`. TSU and its Continuidad are two separate plans, linked only via existing `AcademicProgram.continuityProgramId` | Frontend mock (`PlanForm.tsx`) merges TSU+Continuidad niveles in one plan |
| 2 | Multiple `ACTIVE` plans may coexist per `programId` (different generations) — no uniqueness constraint | `02-config-academica.md` invariant "solo un ACTIVE por programa" |
| 3 | `PlanStatus` = symmetric `ACTIVE`/`INACTIVE` toggle (idempotent `activate()`/`deactivate()`, same as Division/Program) | Domain doc's one-way `ACTIVE`/`DEPRECATED` |
| 4 | `maxExtraordinaryExamsPerPeriod` (int, resets each school period) included in `AcademicPlan` now | Domain doc's lifetime `maxExtraordinaryAttempts` (`02-config-academica.md`); name/semantics follow `06-control-escolar.md` instead |

## Scope

### In Scope
- `AcademicPlan` aggregate root: `programId`, `version`, `validityPeriod`, `titulationKey`, `effectiveFrom`, `totalLevels`, `minPassingGrade` (0–10 scale, per `HU-PROG-017`/RF-PROG-001c), `maxExtraordinaryExamsPerPeriod`, `requiresSocialService`, `socialServiceMinLevelId` (nullable, required if `requiresSocialService=true`), `status`.
- Full CRUD ports: Create/Update/Get/List/ChangeStatus — mirrors Division/Program port set exactly.
- Child-entity use cases scoped to the aggregate: `AddPlanLevelUseCase`, `AddSubjectToPlanUseCase` (+ Remove/Update variants), no independent `PlanLevel`/`Subject` CRUD ports.
- `programId` FK validated via direct `AcademicProgramRepository` injection (same pattern as `CreateAcademicProgramUseCaseImpl` → `AcademicDivisionRepository`), no new cross-aggregate port.
- REST: `POST/PUT/GET /plans`, `GET /plans?programId=&status=&search=&page=&size=` (flat, query-param filter — matches confirmed `/programs?divisionId=` pattern, NOT nested `/programs/{id}/plans`), `PATCH /plans/{id}/status`.

### Out of Scope
- `GradeScale`/`GradeScaleEntry` (HU-PROG-017) — separate follow-up slice, frontend already treats it as a distinct route/tab.
- Correcting `PlanForm.tsx`/`PlanDetalle.tsx` (118-SISA-FRONT) to stop mixing TSU+Continuidad — frontend debt created by Decision 1, tracked separately, does not block this backend change.
- Plan-to-plan continuity linking (`HU-PROG-007`'s "vincular TSU con su Continuidad") — already modeled at `AcademicProgram.continuityProgramId`, no new field needed here.

## Approach

Third aggregate in existing `academic_config` module, same hexagonal layering as `AcademicProgram` (`domain/model`, `domain/port/in`, `domain/port/out`, `domain/service`, `infrastructure/web`, `infrastructure/web/dto`, `infrastructure/persistence`). `PlanLevel`/`Subject` are `@Entity` children persisted through `AcademicPlan`'s aggregate boundary — mutated only via plan-scoped use cases, never their own controllers/repositories.

## Capabilities

### New Capabilities
- `academic-plan-management`: CRUD + status toggle for `AcademicPlan`, plus child-entity management (`PlanLevel`, `Subject`) scoped to the aggregate.

### Modified Capabilities
None.

## Affected Areas

| Area | Impact | Description |
|------|--------|--------------|
| `academic_config/domain/model` | New | `AcademicPlan`, `PlanStatus`, `PlanLevel`, `Subject`, `PlanLevelType`, `SubjectType` |
| `academic_config/domain/port/in,out` | New | Create/Update/Get/List/ChangeStatus + AddPlanLevel/AddSubjectToPlan ports |
| `academic_config/domain/service` | New | `*UseCaseImpl` classes |
| `academic_config/infrastructure/web` | New | `AcademicPlanController` + DTOs |
| `academic_config/infrastructure/persistence` | New | JPA repository + adapter |
| `identity.SecurityFilterConfig` | Modified | Add `/plans` matchers (ADMIN/SERVICIOS_ESCOLARES), mirrors `/programs` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Frontend contract drift (mock expects merged TSU+Continuidad, titulationKey/social-service fields absent) | High | Documented as explicit non-goal; sdd-spec defines the real contract, frontend correction is separate future work |
| Child-entity aggregate boundary leaking (independent Subject edits bypassing plan invariants) | Medium | Enforce all mutations through plan-scoped use cases only, covered in design.md |
| Scale mismatch for `minPassingGrade` (mock UI hints 0–100) | Low | Backend uses 0–10 per RF-PROG-001c; spec states this explicitly |

## Rollback Plan

New module code only, no existing table/data touched. Revert by dropping the `AcademicPlan`/`PlanLevel`/`Subject` migration and removing the added `academic_config` classes; `AcademicDivision`/`AcademicProgram` are unaffected.

## Dependencies

- `AcademicProgram` (existing, for `programId` FK validation).

## Success Criteria

- [ ] Full CRUD + status toggle for `AcademicPlan` implemented and tested (`./mvnw test` green).
- [ ] `PlanLevel`/`Subject` addable/removable only through aggregate-scoped use cases.
- [ ] All 4 PO decisions reflected verbatim in spec.md acceptance criteria.
