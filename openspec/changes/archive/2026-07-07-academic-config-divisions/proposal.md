# Proposal: AcademicDivision CRUD (academic_config — first slice)

## Intent

Backend has no `academic_config` bounded context yet (only `identity` + `shared`). Division-scoped roles in `identity` (`GESTOR_ACADEMICO`, `DIRECTOR_DIVISION`, `COORDINACION_ESTADIAS_DIVISION`) cannot be meaningfully exercised because no real `AcademicDivision` rows exist — `AssignRoleUseCase` is untestable for these. This slice creates the context and delivers `AcademicDivision` CRUD (RF-PROG-004 / HU-PROG-009), unblocking that work and giving Servicios Escolares a real catalog to manage.

## Scope

### In Scope
- NEW bounded context `academic_config` (package `mx.edu.utez.sisa.academic_config`), mirroring `identity`'s hexagonal conventions exactly.
- `AcademicDivision` use cases: Create, Update, List, Activate, Deactivate.
- Fields: `id`, `name`, `code`, `description` (NEW), `directorPersonId` (nullable), `status` (ACTIVE/INACTIVE).
- `programCount` in List response, hardcoded `0` (no real `AcademicProgram` yet), with a code comment marking it a stub for HU-PROG-010.
- Domain-doc update: add `description` + the missing "Puertos (in)" rows for `AcademicDivision` to `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md`.

### Out of Scope
- `AcademicProgram` (HU-PROG-010, separate later change) and every other Config Académica catalog (Periods, Plans, Subjects, Payment Concepts, Groups).
- All frontend work — wiring `DivisionesList.tsx`/`DivisionesForm.tsx` and fixing the delete→deactivate UI is a separate follow-up (same pattern as identity backend → `real-login-integration`).
- Person-provisioning — no API creates a `Person` anywhere yet; this blocks real director assignment and is a not-yet-scoped gap.

## Key Decisions (resolved by PO)

- **`directorPersonId`**: nullable at creation; when provided, validated via read-only `PersonRepository.findById`. No Person creation here.
- **Delete**: NO hard delete. `status` + activate/deactivate. Frontend's "Eliminar" is a prototype bug; the paired FE change reuses the existing `Switch` pattern (`DocumentosInstitucionales.tsx`).
- **Authorization (deliberate divergence)**: `ADMIN` AND `SERVICIOS_ESCOLARES` both get FULL CRUD (read + create/update/activate/deactivate). This intentionally diverges from the narrower `GET /users`-only-read precedent — Servicios Escolares owns catalog/config management at real UTEZ. Do NOT copy the `/users` split here.

## Capabilities

### New Capabilities
- `academic-division-management`: create, update, list (with stub program count), activate, and deactivate academic divisions, with role-based access for ADMIN and SERVICIOS_ESCOLARES.

### Modified Capabilities
- None.

## Approach

Net-new package mirroring `identity`: `domain.{model,port.in,port.out,service}` + `infrastructure.{persistence,web,config}`. Plain domain classes/records, no Lombok, ports in/out, JPA adapters, thin controllers, Strict TDD Mode. Add a specific matcher for `/divisions` in `SecurityFilterConfig` (specific-before-blanket), granting both roles read + write. Confirm during design whether `academic_config` gets its own composition-root/security wiring or extends shared config — no second-module precedent exists yet.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `academic_config/**` | New | Full bounded context + AcademicDivision CRUD |
| `identity/infrastructure/security/SecurityFilterConfig.java` | Modified | New `/divisions` matcher, ADMIN + SERVICIOS_ESCOLARES read+write |
| `118-SISA-CLAUDE/.../02-config-academica.md` | Modified | Add `description` + missing AcademicDivision "Puertos (in)" rows |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `directorPersonId` stays null indefinitely (no Person API) | High | Flag as fast-follow dependency; UI handles absent director |
| First-context conventions drift from `identity` | Med | Mirror `identity` structure exactly; confirm wiring in design |
| `programCount=0` mistaken for real data | Low | Explicit stub comment; becomes a real query in HU-PROG-010 |

## Rollback Plan

New context is additive: delete the `academic_config` package and revert the `SecurityFilterConfig` matcher + the domain-doc edit. No schema migrations (H2 `create-drop`, no Flyway). No data loss risk.

## Dependencies

- Person-provisioning capability (not scoped) for real director assignment.
- `ServiciosEscolaresSeedRunner` dev-seed user (already added) to develop/test under a realistic role.
- Local-only git workflow (no push/PR as of 2026-07-06): `sdd-apply` commits directly to local `main`.

## Success Criteria

- [ ] `academic_config` context exists mirroring `identity` conventions.
- [ ] Create/Update/List/Activate/Deactivate work; List returns `programCount: 0`.
- [ ] `directorPersonId` nullable, validated against `Person` when set.
- [ ] Both ADMIN and SERVICIOS_ESCOLARES have full CRUD; verified in security tests.
- [ ] Domain doc updated with `description` + AcademicDivision ports.
- [ ] Strict TDD followed; tests mirror `identity` structure.
