# Academic Division Management Specification

## Purpose

First capability of the new `academic_config` bounded context. Provides
CRUD-style management of `AcademicDivision` catalog entries — the academic
divisions each `AcademicProgram` will later belong to (HU-PROG-010, out of
scope here). No hard delete; divisions are deactivated instead. `programCount`
is a stub (`0`) until `AcademicProgram` exists.

## Requirements

### Requirement: Create Academic Division

The system MUST allow creating an `AcademicDivision` with `name`, `code`,
`description`, and an optional `directorPersonId`. `name` MUST be unique
across all divisions. `code` MUST be unique across all divisions. A newly
created division MUST default to `status = ACTIVE`; callers MUST NOT set
`status` at creation. When `directorPersonId` is provided, it MUST reference
an existing `Person`; when omitted or `null`, creation MUST still succeed.

#### Scenario: Successful creation with a director
- GIVEN a unique `name` and `code`, and a `directorPersonId` referencing an existing `Person`
- WHEN `CreateAcademicDivisionUseCase` is invoked
- THEN an `AcademicDivision` is created with `status = ACTIVE` and that `directorPersonId`

#### Scenario: Successful creation without a director
- GIVEN a unique `name` and `code`, and `directorPersonId = null`
- WHEN `CreateAcademicDivisionUseCase` is invoked
- THEN an `AcademicDivision` is created with `status = ACTIVE` and `directorPersonId = null`

#### Scenario: Rejects duplicate code
- GIVEN an existing `AcademicDivision` with `code = "DSC"`
- WHEN `CreateAcademicDivisionUseCase` is invoked with `code = "DSC"` again
- THEN the operation is rejected and no second `AcademicDivision` is created

#### Scenario: Rejects duplicate name
- GIVEN an existing `AcademicDivision` with a given `name`
- WHEN `CreateAcademicDivisionUseCase` is invoked with that same `name`
- THEN the operation is rejected and no second `AcademicDivision` is created

#### Scenario: Rejects non-existent director
- GIVEN a `directorPersonId` that does not match any `Person`
- WHEN `CreateAcademicDivisionUseCase` is invoked with that `directorPersonId`
- THEN the operation is rejected and no `AcademicDivision` is created

### Requirement: Update Academic Division

The system MUST allow updating `name`, `code`, `description`, and
`directorPersonId` of an existing `AcademicDivision`. The uniqueness rules for
`name` and `code`, and the existing-`Person` validation for
`directorPersonId`, apply identically on update (a division's own current
values do not count as a conflict against itself). `status` MUST NOT change
through this operation — see Activate/Deactivate.

#### Scenario: Successful update
- GIVEN an existing `AcademicDivision`
- WHEN `UpdateAcademicDivisionUseCase` is invoked with a new `name`, `code`, `description`, and `directorPersonId`
- THEN the division's fields are updated accordingly and `status` is unchanged

#### Scenario: Rejects update to a code already used by another division
- GIVEN two existing divisions, A (`code = "DSC"`) and B (`code = "DIN"`)
- WHEN `UpdateAcademicDivisionUseCase` is invoked on B with `code = "DSC"`
- THEN the operation is rejected and B's `code` remains `"DIN"`

#### Scenario: Rejects update with a non-existent director
- GIVEN an existing `AcademicDivision`
- WHEN `UpdateAcademicDivisionUseCase` is invoked with a `directorPersonId` that does not match any `Person`
- THEN the operation is rejected and the division is unchanged

### Requirement: List Academic Divisions (Paginated)

The system MUST return a paginated, filterable list of `AcademicDivision`
entries shaped `{items[], totalElements, totalPages, page, size}`, mirroring
the `GET /users` convention. Each item MUST include a `programCount` field
hardcoded to `0` — an explicit stub pending real `AcademicProgram` data
(HU-PROG-010), not a real aggregation query.

#### Scenario: Default pagination
- GIVEN more `AcademicDivision` records than the default page size
- WHEN `ListAcademicDivisionsUseCase` is invoked without `page`/`size`
- THEN the first page is returned using the default size, with correct `totalElements` and `totalPages`

#### Scenario: Every item reports a stub programCount
- GIVEN any set of `AcademicDivision` records
- WHEN `ListAcademicDivisionsUseCase` is invoked
- THEN every returned item has `programCount = 0`, regardless of any `AcademicProgram` data

### Requirement: Activate and Deactivate Academic Division

The system MUST allow toggling an `AcademicDivision`'s `status` between
`ACTIVE` and `INACTIVE`. There MUST be no operation that hard-deletes an
`AcademicDivision`.

#### Scenario: Deactivate an active division
- GIVEN an `AcademicDivision` with `status = ACTIVE`
- WHEN `DeactivateAcademicDivisionUseCase` is invoked
- THEN `status` becomes `INACTIVE` and the record still exists

#### Scenario: Reactivate an inactive division
- GIVEN an `AcademicDivision` with `status = INACTIVE`
- WHEN `ActivateAcademicDivisionUseCase` is invoked
- THEN `status` becomes `ACTIVE`

### Requirement: Role-Based Access for Division Management

The system MUST grant both `ADMIN` and `SERVICIOS_ESCOLARES` full access —
create, update, list, activate, and deactivate — to `AcademicDivision`
management. This is a deliberate divergence from the narrower
`GET /users`-only-read pattern used in `identity`; it MUST NOT be narrowed to
match that precedent. Requests from unauthenticated callers or any other role
MUST be rejected.

#### Scenario: SERVICIOS_ESCOLARES can write, not just read
- GIVEN an authenticated caller with role `SERVICIOS_ESCOLARES`
- WHEN that caller invokes `CreateAcademicDivisionUseCase` (or Update/Activate/Deactivate)
- THEN the operation succeeds

#### Scenario: ADMIN retains full access
- GIVEN an authenticated caller with role `ADMIN`
- WHEN that caller invokes any division management operation
- THEN the operation succeeds

#### Scenario: Other roles are rejected
- GIVEN an authenticated caller whose only role is neither `ADMIN` nor `SERVICIOS_ESCOLARES`
- WHEN that caller invokes any division management operation
- THEN the request is rejected

#### Scenario: Unauthenticated requests are rejected
- GIVEN a request with no valid authentication
- WHEN it targets any division management endpoint
- THEN the request is rejected
