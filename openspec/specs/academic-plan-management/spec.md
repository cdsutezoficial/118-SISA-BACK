# Academic Plan Management Specification

## Purpose

Third aggregate in the `academic_config` bounded context, following
`AcademicDivision` and `AcademicProgram`. Provides CRUD-style management of
`AcademicPlan` (Plan de Estudios) — the curriculum version that drives
Inscripciones, Calificaciones, and Egreso/Titulación downstream — plus
aggregate-scoped management of its owned children `PlanLevel` and `Subject`.
`AcademicPlan` is the first aggregate in this module with a child-entity
graph: children have no independent CRUD ports and are mutated only through
plan-scoped use cases. `GradeScale`/`GradeScaleEntry` and `SubjectClassification`
existence validation are out of scope (deferred follow-up).

## Requirements

### Requirement: Create Academic Plan

The system MUST allow creating an `AcademicPlan` with `programId`, `version`,
`validityPeriod`, `titulationKey`, `effectiveFrom`, `totalLevels`,
`minPassingGrade` (0–10 scale, per RF-PROG-001c — NOT 0–100),
`maxExtraordinaryExamsPerPeriod`, `requiresSocialService`, and
`socialServiceMinLevelId`. `programId` MUST be required and MUST reference an
existing `AcademicProgram` (validated by direct `AcademicProgramRepository`
injection, mirroring `divisionId` validation in
`CreateAcademicProgramUseCaseImpl`). A plan MUST reference exactly one
`AcademicProgram` — TSU and its Continuidad are separate `AcademicProgram`
records and therefore separate `AcademicPlan` records; there is no
multi-program plan. `version` MUST be unique within the same `programId` (not
globally) — two different programs may reuse the same version label. There is
no separate `code`/`clave` field; `version` is the plan's sole identifying
label. `titulationKey` MUST be a non-blank `String` with no catalog/format
validation in this slice (the external Titulación platform integration is
deferred to a future cycle; this field is stored opaquely). `minPassingGrade`
MUST be within `[0, 10]`. `maxExtraordinaryExamsPerPeriod` MUST be an integer
`>= 0` (a configured per-period limit, not a lifetime counter; the counting/
reset logic itself belongs to Control Escolar and is out of scope here).
`requiresSocialService = false` MUST require `socialServiceMinLevelId = null`.
`requiresSocialService = true` MAY be created with `socialServiceMinLevelId =
null` (no `PlanLevel` can exist yet at creation time — levels are added
afterward via `AddPlanLevelUseCase`); it MUST be rejected if
`socialServiceMinLevelId` is non-null at creation, since no `PlanLevel` exists
to validate against yet. A newly created plan MUST default to
`status = ACTIVE`; callers MUST NOT set `status` at creation. Multiple
`ACTIVE` plans MAY coexist for the same `programId` (different generations) —
there is no uniqueness constraint on `status = ACTIVE` per `programId`.

#### Scenario: Successful creation with valid data
- GIVEN a `programId` referencing an existing `AcademicProgram` and a `version` unique within that `programId`
- WHEN `CreateAcademicPlanUseCase` is invoked
- THEN an `AcademicPlan` is created with `status = ACTIVE`

#### Scenario: Rejects a programId that does not exist
- GIVEN a `programId` that does not match any existing `AcademicProgram`
- WHEN `CreateAcademicPlanUseCase` is invoked with that `programId`
- THEN the operation is rejected and no `AcademicPlan` is created

#### Scenario: Rejects duplicate version within the same program
- GIVEN an existing `AcademicPlan` with `programId = P1` and `version = "2022-A"`
- WHEN `CreateAcademicPlanUseCase` is invoked again with `programId = P1` and `version = "2022-A"`
- THEN the operation is rejected and no second plan is created

#### Scenario: Same version reused across different programs succeeds
- GIVEN an existing `AcademicPlan` with `programId = P1` and `version = "2022-A"`
- WHEN `CreateAcademicPlanUseCase` is invoked with `programId = P2` and `version = "2022-A"`
- THEN a distinct `AcademicPlan` is created, because uniqueness is scoped to `programId`

#### Scenario: A second ACTIVE plan for the same program is allowed
- GIVEN an existing `AcademicPlan` with `programId = P1` and `status = ACTIVE`
- WHEN `CreateAcademicPlanUseCase` is invoked again with `programId = P1` and a different `version`
- THEN the operation succeeds and both plans coexist with `status = ACTIVE`

#### Scenario: Rejects requiresSocialService=true with a non-null socialServiceMinLevelId at creation
- GIVEN a creation request with `requiresSocialService = true` and a non-null `socialServiceMinLevelId`
- WHEN `CreateAcademicPlanUseCase` is invoked
- THEN the operation is rejected, because no `PlanLevel` can exist for a plan that does not exist yet

#### Scenario: Rejects requiresSocialService=false with a non-null socialServiceMinLevelId
- GIVEN a creation request with `requiresSocialService = false` and a non-null `socialServiceMinLevelId`
- WHEN `CreateAcademicPlanUseCase` is invoked
- THEN the operation is rejected

#### Scenario: Rejects out-of-range minPassingGrade
- GIVEN a creation request with `minPassingGrade = 70` (0–100 scale value)
- WHEN `CreateAcademicPlanUseCase` is invoked
- THEN the operation is rejected, because the valid scale is `[0, 10]`

### Requirement: Update Academic Plan

The system MUST allow updating `version`, `validityPeriod`, `titulationKey`,
`effectiveFrom`, `totalLevels`, `minPassingGrade`,
`maxExtraordinaryExamsPerPeriod`, `requiresSocialService`, and
`socialServiceMinLevelId` of an existing `AcademicPlan`. `programId` MUST NOT
be changeable via update — moving a plan to a different program is not
supported. The `version` uniqueness-within-`programId` rule applies
identically on update (a plan's own current value does not conflict with
itself). When `requiresSocialService = true`, `socialServiceMinLevelId` MUST
reference an existing `PlanLevel` that belongs to this same `AcademicPlan` —
cross-aggregate leakage (referencing another plan's level) MUST be rejected.
`status` MUST NOT change through this operation — see Activate/Deactivate.

#### Scenario: Successful update
- GIVEN an existing `AcademicPlan`
- WHEN `UpdateAcademicPlanUseCase` is invoked with new `version`, `validityPeriod`, `titulationKey`, `totalLevels`, and `minPassingGrade`
- THEN the plan's fields are updated accordingly and `status` is unchanged

#### Scenario: Rejects update to a version already used within the same program
- GIVEN two existing plans under `programId = P1`: A (`version = "2022-A"`) and B (`version = "2023-A"`)
- WHEN `UpdateAcademicPlanUseCase` is invoked on B with `version = "2022-A"`
- THEN the operation is rejected and B's `version` remains `"2023-A"`

#### Scenario: Accepts socialServiceMinLevelId referencing a PlanLevel of this same plan
- GIVEN an existing `AcademicPlan` with an existing `PlanLevel` belonging to it
- WHEN `UpdateAcademicPlanUseCase` is invoked with `requiresSocialService = true` and `socialServiceMinLevelId` set to that `PlanLevel`'s id
- THEN the operation succeeds

#### Scenario: Rejects socialServiceMinLevelId referencing a PlanLevel from a different plan
- GIVEN an existing `AcademicPlan` A and a `PlanLevel` belonging to a different plan B
- WHEN `UpdateAcademicPlanUseCase` is invoked on A with `requiresSocialService = true` and `socialServiceMinLevelId` set to B's `PlanLevel` id
- THEN the operation is rejected

#### Scenario: Rejects attempts to change programId
- GIVEN an existing `AcademicPlan`
- WHEN `UpdateAcademicPlanUseCase` is invoked with a different `programId`
- THEN the operation is rejected and the plan's `programId` is unchanged

### Requirement: Get Academic Plan by Id

The system MUST allow retrieving the full detail of a single `AcademicPlan`
by its id, including its `PlanLevel` and `Subject` children.

#### Scenario: Existing plan is found with its children
- GIVEN an existing `AcademicPlan` with two `PlanLevel` children, one containing a `Subject`
- WHEN `GetAcademicPlanUseCase` is invoked with that plan's id
- THEN the full detail is returned, including both levels and the subject

#### Scenario: Non-existent plan is not found
- GIVEN no `AcademicPlan` exists with a given id
- WHEN `GetAcademicPlanUseCase` is invoked with that id
- THEN the operation is rejected as not found

### Requirement: List Academic Plans (Paginated)

The system MUST return a paginated, filterable list of `AcademicPlan` entries
shaped `{items[], totalElements, totalPages, page, size}`, mirroring
`GET /programs`. The list MUST be filterable by `programId`, `status`, and
free-text `search`. When `status` is omitted, the list MUST return plans of
ALL statuses (`ACTIVE` and `INACTIVE`) — there is no default active-only
filter, consistent with the `/programs` convention. Each returned item MUST
include at minimum `id`, `programId`, `version`, `validityPeriod`,
`effectiveFrom`, `totalLevels`, and `status`.

#### Scenario: Default pagination and status
- GIVEN `AcademicPlan` records with a mix of `ACTIVE` and `INACTIVE` status
- WHEN `ListAcademicPlansUseCase` is invoked without `status`
- THEN both `ACTIVE` and `INACTIVE` plans are returned in the first page

#### Scenario: Filter by programId
- GIVEN `AcademicPlan` records belonging to multiple programs
- WHEN `ListAcademicPlansUseCase` is invoked with a specific `programId`
- THEN only plans belonging to that `programId` are returned

#### Scenario: Filter by status
- GIVEN `AcademicPlan` records with a mix of `ACTIVE` and `INACTIVE` status
- WHEN `ListAcademicPlansUseCase` is invoked with `status = ACTIVE`
- THEN only `ACTIVE` plans are returned

### Requirement: Activate and Deactivate Academic Plan

The system MUST allow toggling an `AcademicPlan`'s `status` between `ACTIVE`
and `INACTIVE` via idempotent `activate()`/`deactivate()`, matching
`AcademicDivision`/`AcademicProgram` — NOT the domain doc's one-way
`ACTIVE`/`DEPRECATED` model. There MUST be no operation that hard-deletes an
`AcademicPlan`. Deactivating one plan MUST NOT affect any other plan under
the same `programId` — since multiple `ACTIVE` plans may coexist, toggling
one is independent of the others.

#### Scenario: Deactivate an active plan
- GIVEN an `AcademicPlan` with `status = ACTIVE`
- WHEN `DeactivateAcademicPlanUseCase` is invoked
- THEN `status` becomes `INACTIVE` and the record still exists

#### Scenario: Reactivate an inactive plan
- GIVEN an `AcademicPlan` with `status = INACTIVE`
- WHEN `ActivateAcademicPlanUseCase` is invoked
- THEN `status` becomes `ACTIVE`, even if another plan under the same `programId` is already `ACTIVE`

### Requirement: Add and Update Plan Level

The system MUST allow adding a `PlanLevel` (`levelNumber`, `type` —
`REGULAR`/`INTERNSHIP`, optional `description`) to an existing `AcademicPlan`
via `AddPlanLevelUseCase`, and editing/removing an existing one via
`UpdatePlanLevelUseCase`/`RemovePlanLevelUseCase`. There MUST be no
independent `PlanLevel` controller or repository exposed outside these
plan-scoped use cases. `levelNumber` MUST be unique within the plan and MUST
be within `[1, totalLevels]` — `AddPlanLevelUseCase` MUST reject a
`levelNumber` outside that range or already used by another level of the same
plan. `RemovePlanLevelUseCase` MUST reject removal of a `PlanLevel` that is
referenced by `socialServiceMinLevelId` on its own plan or by any `Subject`.

#### Scenario: Successful level addition
- GIVEN an `AcademicPlan` with `totalLevels = 6` and no existing level numbered 3
- WHEN `AddPlanLevelUseCase` is invoked with `levelNumber = 3`, `type = REGULAR`
- THEN the level is added to the plan

#### Scenario: Rejects levelNumber outside totalLevels range
- GIVEN an `AcademicPlan` with `totalLevels = 6`
- WHEN `AddPlanLevelUseCase` is invoked with `levelNumber = 7`
- THEN the operation is rejected

#### Scenario: Rejects duplicate levelNumber within the same plan
- GIVEN an `AcademicPlan` with an existing level `levelNumber = 3`
- WHEN `AddPlanLevelUseCase` is invoked again with `levelNumber = 3` for that same plan
- THEN the operation is rejected

#### Scenario: Rejects removing a level used as socialServiceMinLevelId
- GIVEN an `AcademicPlan` with `socialServiceMinLevelId` pointing to `PlanLevel` L
- WHEN `RemovePlanLevelUseCase` is invoked on L
- THEN the operation is rejected

### Requirement: Add and Update Subject

The system MUST allow adding a `Subject` (`planLevelId`, `classificationId`,
`code`, `name`, `credits`, `weeklyHours`, `evaluationUnits`, `displayOrder`,
`type` — `CORE`/`ELECTIVE`/`INTERNSHIP`, `isRetakeable` default `true`) to a
`PlanLevel` of an existing `AcademicPlan` via `AddSubjectToPlanUseCase`, and
editing/removing one via `UpdateSubjectUseCase`/`RemoveSubjectUseCase`. There
MUST be no independent `Subject` controller or repository. `planLevelId`
MUST reference a `PlanLevel` that belongs to this same `AcademicPlan` —
referencing a level from a different plan MUST be rejected. `code` MUST be
unique within the plan (across all its levels). `classificationId` MUST be
provided as a non-null UUID but is NOT validated against an existing
`SubjectClassification` record in this slice — that aggregate is not yet
implemented in `academic_config` and existence validation is deferred to the
`GradeScale` follow-up slice; this is a known, accepted gap, not a bug.

#### Scenario: Successful subject addition
- GIVEN an `AcademicPlan` with a `PlanLevel` and a unique `code` within the plan
- WHEN `AddSubjectToPlanUseCase` is invoked with that `planLevelId` and `code`
- THEN the subject is added under that level

#### Scenario: Rejects planLevelId from a different plan
- GIVEN plan A and a `PlanLevel` belonging to plan B
- WHEN `AddSubjectToPlanUseCase` is invoked on A with B's `PlanLevel` id
- THEN the operation is rejected

#### Scenario: Rejects duplicate code within the same plan
- GIVEN an `AcademicPlan` with an existing `Subject` `code = "MAT101"` under one level
- WHEN `AddSubjectToPlanUseCase` is invoked with `code = "MAT101"` under a different level of the same plan
- THEN the operation is rejected

#### Scenario: classificationId is accepted without existence validation
- GIVEN a `classificationId` that does not match any real `SubjectClassification` record (none exist yet)
- WHEN `AddSubjectToPlanUseCase` is invoked with that `classificationId`
- THEN the subject is added successfully, because classification existence is out of scope for this slice

### Requirement: Role-Based Access for Plan Management

The system MUST grant both `ADMIN` and `SERVICIOS_ESCOLARES` full access —
create, update, list, get, activate, deactivate, and all `PlanLevel`/`Subject`
child operations — mirroring `academic-program-management`. Requests from
unauthenticated callers or any other role MUST be rejected.

#### Scenario: SERVICIOS_ESCOLARES can write, not just read
- GIVEN an authenticated caller with role `SERVICIOS_ESCOLARES`
- WHEN that caller invokes any plan or child-entity write operation
- THEN the operation succeeds

#### Scenario: Other roles are rejected
- GIVEN an authenticated caller whose only role is neither `ADMIN` nor `SERVICIOS_ESCOLARES`
- WHEN that caller invokes any plan management endpoint
- THEN the request is rejected

#### Scenario: Unauthenticated requests are rejected
- GIVEN a request with no valid authentication
- WHEN it targets any plan management endpoint
- THEN the request is rejected
