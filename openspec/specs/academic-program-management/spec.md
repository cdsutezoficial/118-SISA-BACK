# Academic Program Management Specification

## Purpose

Second aggregate in the `academic_config` bounded context (HU-PROG-010),
following `AcademicDivision` (`academic-division-management`). Provides
CRUD-style management of `AcademicProgram` catalog entries — the academic
offerings (TSU/Ingeniería/Licenciatura…) that belong to an `AcademicDivision`.
No hard delete; programs are deactivated instead. Out of scope: `AcademicPlan`,
`ProgramAdmissionConfig`, the TSU→Continuidad linking use case, and any
frontend behavior.

## Requirements

### Requirement: Create Academic Program

The system MUST allow creating an `AcademicProgram` with `divisionId`, `name`,
`offerName`, `code`, `level`, `modality`, an optional `dgpCode`, and an
optional `description`.
`divisionId` MUST be required — it MUST NOT be omitted or `null` — unlike
`AcademicDivision.directorPersonId`, which is optional. `divisionId` MUST
reference an existing `AcademicDivision`. `code` MUST be unique across all
programs. The pair `(offerName, modality)` MUST be unique across all
programs — two programs sharing both the same `offerName` and the same
`modality` are a duplicate, even if their `code`s differ. A newly created
program MUST default to `status = ACTIVE`; callers MUST NOT set `status` at
creation. `continuityProgramId` MAY be omitted or `null` at creation; this
field is schema-only in this change — no validation or linking logic is
applied to it. `dgpCode` MAY be omitted or `null`; it stores the program's
official registration code issued by the Dirección General de Profesiones
(DGP) of the SEP — not all programs have one.

#### Scenario: Successful creation with valid data
- GIVEN a `divisionId` referencing an existing `AcademicDivision`, a unique `code`, and a unique `(offerName, modality)` pair
- WHEN `CreateAcademicProgramUseCase` is invoked
- THEN an `AcademicProgram` is created with `status = ACTIVE` and the given `divisionId`

#### Scenario: continuityProgramId is accepted as null or absent
- GIVEN valid required fields and `continuityProgramId` omitted or `null`
- WHEN `CreateAcademicProgramUseCase` is invoked
- THEN the `AcademicProgram` is created successfully with `continuityProgramId = null`

#### Scenario: Rejects missing divisionId
- GIVEN a creation request with `divisionId` omitted or `null`
- WHEN `CreateAcademicProgramUseCase` is invoked
- THEN the operation is rejected and no `AcademicProgram` is created

#### Scenario: Rejects a divisionId that does not exist
- GIVEN a `divisionId` that does not match any existing `AcademicDivision`
- WHEN `CreateAcademicProgramUseCase` is invoked with that `divisionId`
- THEN the operation is rejected and no `AcademicProgram` is created

#### Scenario: Rejects duplicate code
- GIVEN an existing `AcademicProgram` with `code = "ISC-01"`
- WHEN `CreateAcademicProgramUseCase` is invoked with `code = "ISC-01"` again
- THEN the operation is rejected and no second `AcademicProgram` is created

#### Scenario: Rejects duplicate (offerName, modality) pair
- GIVEN an existing `AcademicProgram` with `offerName = "Ingeniería en Software"` and `modality = PRESENCIAL`
- WHEN `CreateAcademicProgramUseCase` is invoked again with the same `offerName` and the same `modality` (even with a different `code`)
- THEN the operation is rejected and no second `AcademicProgram` is created

#### Scenario: Same offerName with a different modality succeeds
- GIVEN an existing `AcademicProgram` with `offerName = "Ingeniería en Software"` and `modality = PRESENCIAL`
- WHEN `CreateAcademicProgramUseCase` is invoked with `offerName = "Ingeniería en Software"` and `modality = MIXTA`
- THEN a second, distinct `AcademicProgram` is created, because `(offerName, modality)` — not `offerName` alone — is the uniqueness scope

### Requirement: Update Academic Program

The system MUST allow updating `divisionId`, `name`, `offerName`, `code`,
`level`, `modality`, `dgpCode`, and `description` of an existing `AcademicProgram`.
`divisionId` MUST remain required on update and MUST reference an existing
`AcademicDivision`. The uniqueness rules for `code` and `(offerName,
modality)` apply identically on update — a program's own current values MUST
NOT count as a conflict against itself. `status` MUST NOT change through this
operation — see Activate/Deactivate.

#### Scenario: Successful update
- GIVEN an existing `AcademicProgram`
- WHEN `UpdateAcademicProgramUseCase` is invoked with a new `divisionId`, `name`, `offerName`, `code`, `level`, `modality`, and `description`
- THEN the program's fields are updated accordingly and `status` is unchanged

#### Scenario: Rejects update with a missing or non-existent divisionId
- GIVEN an existing `AcademicProgram`
- WHEN `UpdateAcademicProgramUseCase` is invoked with `divisionId` omitted, `null`, or referencing a non-existent `AcademicDivision`
- THEN the operation is rejected and the program is unchanged

#### Scenario: Rejects update to a code already used by another program
- GIVEN two existing programs, A (`code = "ISC-01"`) and B (`code = "ISC-02"`)
- WHEN `UpdateAcademicProgramUseCase` is invoked on B with `code = "ISC-01"`
- THEN the operation is rejected and B's `code` remains `"ISC-02"`

#### Scenario: Rejects update to an (offerName, modality) pair already used by another program
- GIVEN two existing programs, A (`offerName = "Ingeniería en Software"`, `modality = PRESENCIAL`) and B (`offerName = "Ingeniería Industrial"`, `modality = PRESENCIAL`)
- WHEN `UpdateAcademicProgramUseCase` is invoked on B with `offerName = "Ingeniería en Software"` and `modality = PRESENCIAL`
- THEN the operation is rejected and B's fields remain unchanged

#### Scenario: Updating a program keeping its own offerName/modality/code succeeds
- GIVEN an existing `AcademicProgram` with `code = "ISC-01"`, `offerName = "Ingeniería en Software"`, `modality = PRESENCIAL`
- WHEN `UpdateAcademicProgramUseCase` is invoked on that same program with those same `code`, `offerName`, and `modality` values, plus a changed `description`
- THEN the operation succeeds, because the program's own current values do not count as a conflict against itself

### Requirement: Get Academic Program by Id

The system MUST allow retrieving the full detail of a single `AcademicProgram`
by its id.

#### Scenario: Existing program is found
- GIVEN an existing `AcademicProgram`
- WHEN `GetAcademicProgramUseCase` is invoked with that program's id
- THEN the full detail of that `AcademicProgram` is returned

#### Scenario: Non-existent program is not found
- GIVEN no `AcademicProgram` exists with a given id
- WHEN `GetAcademicProgramUseCase` is invoked with that id
- THEN the operation is rejected as not found

### Requirement: List Academic Programs (Paginated)

The system MUST return a paginated, filterable list of `AcademicProgram`
entries shaped `{items[], totalElements, totalPages, page, size}`, mirroring
the `GET /users` and `GET /divisions` convention. The list MUST be filterable
by `status`, by free-text `search`, and by `divisionId` — the latter being new
relative to `academic-division-management`, since programs belong to
divisions and staff need to see "programs in division X". Each returned item
MUST include the fields needed for a list view: at minimum `id`, `divisionId`,
`name`, `offerName`, `code`, `level`, `modality`, `dgpCode`, and `status`.

#### Scenario: Default pagination
- GIVEN more `AcademicProgram` records than the default page size
- WHEN `ListAcademicProgramsUseCase` is invoked without `page`/`size`
- THEN the first page is returned using the default size, with correct `totalElements` and `totalPages`

#### Scenario: Filter by divisionId
- GIVEN `AcademicProgram` records belonging to multiple divisions
- WHEN `ListAcademicProgramsUseCase` is invoked with a specific `divisionId`
- THEN only programs belonging to that `divisionId` are returned

#### Scenario: Filter by status
- GIVEN `AcademicProgram` records with a mix of `ACTIVE` and `INACTIVE` status
- WHEN `ListAcademicProgramsUseCase` is invoked with `status = ACTIVE`
- THEN only `ACTIVE` programs are returned

### Requirement: Activate and Deactivate Academic Program

The system MUST allow toggling an `AcademicProgram`'s `status` between
`ACTIVE` and `INACTIVE`. There MUST be no operation that hard-deletes an
`AcademicProgram`. Deactivating an `AcademicDivision` that still has `ACTIVE`
`AcademicProgram` records MUST NOT be blocked or validated by this
capability — this is an intentional, accepted gap for this change (not a bug
to be silently "fixed" later); a future change may add that cascade
validation.

#### Scenario: Deactivate an active program
- GIVEN an `AcademicProgram` with `status = ACTIVE`
- WHEN `DeactivateAcademicProgramUseCase` is invoked
- THEN `status` becomes `INACTIVE` and the record still exists

#### Scenario: Reactivate an inactive program
- GIVEN an `AcademicProgram` with `status = INACTIVE`
- WHEN `ActivateAcademicProgramUseCase` is invoked
- THEN `status` becomes `ACTIVE`

#### Scenario: Deactivating a division with active programs is not blocked
- GIVEN an `AcademicDivision` with one or more `AcademicProgram` records where `status = ACTIVE`
- WHEN that `AcademicDivision` is deactivated via `academic-division-management`
- THEN the deactivation succeeds and no validation against its `AcademicProgram` records is performed, by design

### Requirement: Role-Based Access for Program Management

The system MUST grant both `ADMIN` and `SERVICIOS_ESCOLARES` full access —
create, update, list, get, activate, and deactivate — to `AcademicProgram`
management, mirroring `academic-division-management`. Requests from
unauthenticated callers or any other role MUST be rejected.

#### Scenario: SERVICIOS_ESCOLARES can write, not just read
- GIVEN an authenticated caller with role `SERVICIOS_ESCOLARES`
- WHEN that caller invokes `CreateAcademicProgramUseCase` (or Update/Activate/Deactivate)
- THEN the operation succeeds

#### Scenario: ADMIN retains full access
- GIVEN an authenticated caller with role `ADMIN`
- WHEN that caller invokes any program management operation
- THEN the operation succeeds

#### Scenario: Other roles are rejected
- GIVEN an authenticated caller whose only role is neither `ADMIN` nor `SERVICIOS_ESCOLARES`
- WHEN that caller invokes any program management operation
- THEN the request is rejected

#### Scenario: Unauthenticated requests are rejected
- GIVEN a request with no valid authentication
- WHEN it targets any program management endpoint
- THEN the request is rejected
