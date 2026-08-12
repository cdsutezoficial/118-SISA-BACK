# User Account Management Specification

## Purpose

Create staff `User` accounts, assign `RoleType` roles (with division scoping where
required), and bootstrap the first `ADMIN` account at application startup. Scope
limited to this slice: no student/`Enrollment` integration, no deactivate/unlock,
no password-reset flow (all deferred per proposal).

## Requirements

### Requirement: Create User Account

The system MUST allow creating exactly one `User` for a `Person` that does not
already have one (one-`Person`-one-`User` invariant). For this slice, the
account is for institutional staff: `username` MUST be the `Person`'s
pre-existing `institutionalEmail` — the system MUST NOT generate or assign an
institutional email itself. The created `User` MUST have `mustChangePassword =
true` and `status = ACTIVE`.

#### Scenario: Successful staff account creation
- GIVEN a `Person` with `institutionalEmail` set and no existing `User`
- WHEN `CreateUserUseCase` is invoked for that `Person`
- THEN a `User` is created with `username = institutionalEmail`, `mustChangePassword = true`, `status = ACTIVE`

#### Scenario: Rejects duplicate account for the same Person
- GIVEN a `Person` that already has a `User`
- WHEN `CreateUserUseCase` is invoked again for that same `Person`
- THEN the operation is rejected and no second `User` is created

#### Scenario: Rejects creation without institutional email
- GIVEN a `Person` with `institutionalEmail = null`
- WHEN `CreateUserUseCase` is invoked for that `Person`
- THEN the operation is rejected — IT must provision the institutional email first

### Requirement: Assign Role to User

The system MUST allow assigning a `RoleType` to an existing `User` via a
`UserRole`. Roles `GESTOR_ACADEMICO`, `COORDINACION_ESTADIAS_DIVISION`, and
`DIRECTOR_DIVISION` MUST require a `divisionId`. All other `RoleType` values
MUST NOT have a `divisionId`. A `User` MAY hold multiple `UserRole` entries
with different scopes.

#### Scenario: Division-scoped role requires divisionId
- GIVEN a `User` with no roles yet
- WHEN `AssignRoleUseCase` assigns `DIRECTOR_DIVISION` with a valid `divisionId`
- THEN the `UserRole` is created with that `divisionId`

#### Scenario: Division-scoped role rejected without divisionId
- GIVEN a `User` with no roles yet
- WHEN `AssignRoleUseCase` assigns `GESTOR_ACADEMICO` without a `divisionId`
- THEN the operation is rejected

#### Scenario: Non-division role rejected with a divisionId
- GIVEN a `User` with no roles yet
- WHEN `AssignRoleUseCase` assigns `ADMIN` with a `divisionId` provided
- THEN the operation is rejected

#### Scenario: User accumulates multiple scoped roles
- GIVEN a `User` already holds `COORDINACION_ESTADIAS_DIVISION` for division A
- WHEN `AssignRoleUseCase` assigns `GESTOR_ACADEMICO` for division B
- THEN the `User` ends up with both `UserRole` entries, one per division

### Requirement: Bootstrap ADMIN Seed

At application startup, the system MUST create exactly one `ADMIN` `User` if
no `User` with role `ADMIN` exists yet, using credentials from environment
variables. The seeded account MUST have `mustChangePassword = true`. If an
`ADMIN` already exists, the seed MUST be skipped without creating a duplicate.

#### Scenario: Empty database seeds the first ADMIN
- GIVEN no `User` with role `ADMIN` exists
- WHEN the application starts
- THEN one `ADMIN` `User` is created from env-var credentials with `mustChangePassword = true`

#### Scenario: Existing ADMIN skips reseeding
- GIVEN an `ADMIN` `User` already exists
- WHEN the application restarts
- THEN no additional `ADMIN` `User` is created
