# Proposal: AcademicProgram CRUD (HU-PROG-010)

## Intent

Second aggregate inside the ALREADY-EXISTING `academic_config` bounded context —
`AcademicDivision` shipped and archived (`academic-config-divisions`). This is NOT
a new module: it extends the same module, reusing its composition root, module-local
`GlobalExceptionHandler`/`UseCaseConfig`, and verb-split security matchers. SISAv2's
academic catalog needs programs (TSU/Ingeniería/Licenciatura…) before any downstream
catalog (Planes, Admisión config) can reference them. Today only Divisiones exists.

## Scope

### In Scope
- `AcademicProgram` aggregate + own `ProgramStatus` enum (ACTIVE/INACTIVE), mirroring `DivisionStatus`.
- **6 use cases** (ports-in + service + REST adapter): Create, Update, List, **Get-by-id**, Activate, Deactivate. Include the by-id getter from the start (Divisiones needed it as a fast-follow — don't repeat that gap).
- **New shared-kernel enums** in `mx.edu.utez.sisa.shared.model`: `AcademicLevel` (TSU, CONTINUIDAD, INGENIERIA, LICENCIATURA, POSGRADO) and `ProgramModality` (PRESENCIAL, MIXTA). First shared-kernel enums beyond `RoleType`; other contexts (Admisión) will reuse them.
- `divisionId` **REQUIRED (NOT NULL)** at creation, validated via existing `AcademicDivisionRepository.findById` (same-module call, no cross-module port). This is a deliberate DIVERGENCE from Divisiones' optional `directorPersonId` — that optionality existed only because of the Person-provisioning blocker, which does NOT apply here since `AcademicDivision` genuinely exists.
- **Uniqueness: BOTH `code` AND the pair `(offerName, modality)`.** The modality-is-identity invariant means two programs sharing `offerName`+`modality` are a real duplicate even with different `code`s.
- `continuityProgramId`: **schema-only** nullable self-referential FK column — NO use case wires it in this change.

### Out of Scope
- `AcademicPlan`, `ProgramAdmissionConfig`, any catalog referencing `programId`.
- TSU→Continuidad linking use case (HU-PROG-007 — depends on Planes, not this HU).
- **Division→Program status cascade** (see Decision 3 below — documented gap, not silent).
- All frontend work (see Frontend Follow-up below).

## Capabilities

### New Capabilities
- `academic-program-management`: create/update/list/get/activate/deactivate `AcademicProgram`, with `divisionId` FK validation and dual uniqueness (`code`, `(offerName, modality)`).

### Modified Capabilities
- None.

## Approach

Hexagonal per `00-ARQUITECTURA.md`, mirroring the proven `AcademicDivision` template:
`domain/model|port/in|port/out|service`, `infrastructure/persistence|web`, module-local
`shared/exception`. **EXTEND** (not duplicate) the existing `academic_config`
`GlobalExceptionHandler` and `UseCaseConfig`; **EXTEND** identity's `SecurityFilterConfig`
with 4 verb-split `/programs` matchers. Authorization mirrors Divisiones: `ADMIN` +
`SERVICIOS_ESCOLARES` full CRUD (nothing in RF-PROG-004/HU-PROG-010 suggests a narrower split).
`AcademicProgramRepository` out-port; `divisionId` validated in-service via the existing
Division repository. Persist `continuityProgramId` as a nullable column, no endpoint.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/model/AcademicLevel.java`, `ProgramModality.java` | New | Shared-kernel enums (first beyond RoleType) |
| `academic_config/domain/**` | New | `AcademicProgram`, `ProgramStatus`, 6 ports-in, out-port, 6 services |
| `academic_config/infrastructure/persistence/**` | New | JPA repo + adapter (incl. nullable `continuityProgramId` column) |
| `academic_config/infrastructure/web/**` | New | Controller + DTOs (`/programs`) |
| `academic_config/infrastructure/web/GlobalExceptionHandler.java` | Modified | Extend with new mappings |
| `academic_config/infrastructure/config/UseCaseConfig.java` | Modified | Extend with new @Bean methods |
| `academic_config/shared/exception/**` | New | NotFound / DuplicateCode / DuplicateOfferModality / DivisionNotFound |
| `identity/infrastructure/security/SecurityFilterConfig.java` | Modified | 4 verb-split `/programs` matchers |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Orphan `divisionId` (deleted/invalid) | Low | NOT NULL + `findById` validation in-service |
| `(offerName, modality)` constraint too strict | Low | Matches domain identity invariant; DB unique + 409 mapping |
| Division deactivated while it has ACTIVE programs | Med | ACCEPTED gap this slice (Decision 3) — documented, future change can add validation |
| Route naming (`/programs` vs `/academic-programs`) | Low | Follow Divisiones' plural-noun no-prefix convention (`/divisions` → `/programs`) — confirm in design |

## Rollback Plan

Additive only (new aggregate + two enums, extensions to existing config beans).
Revert the change commit(s). H2 in-memory schema recreated on boot; no migration.
No other module depends on `AcademicProgram` yet.

## Dependencies

- Existing `academic_config` module (`AcademicDivision` + its `AcademicDivisionRepository.findById`).
- JDK 21 for `./mvnw test`/`package` (JAVA_HOME gotcha — see project.md).

## Success Criteria

- [ ] Create program with required valid `divisionId`; invalid/absent division → rejected.
- [ ] Duplicate `code` → 409; duplicate `(offerName, modality)` → 409; differing modality allowed.
- [ ] List (paginated/searchable), Get-by-id, Activate, Deactivate all work, mirroring Divisiones.
- [ ] `AcademicLevel` + `ProgramModality` exist in `shared.model` and are used by the aggregate.
- [ ] `continuityProgramId` column exists, nullable, with NO endpoint/use case setting it.
- [ ] `ADMIN` + `SERVICIOS_ESCOLARES` authorized for full CRUD; others rejected.
- [ ] `./mvnw clean package` + `./mvnw test` green under JDK 21.

## Resolved Decisions (settled by PO, 2026-07-07)

1. `divisionId` REQUIRED (NOT NULL) — deliberate divergence from Divisiones' optional director FK.
2. Dual uniqueness: `code` AND `(offerName, modality)`.
3. NO cascade/validation on division deactivation this slice — explicit, documented gap.

## Frontend Follow-up (separate change, NOT this backend-only proposal)

- `ProgramasList.tsx`/`ProgramasForm.tsx` carry a stale `claveDGP` mock field that does
  NOT belong on `AcademicProgram` (conceptually closer to `AcademicPlan.titulationKey`) —
  flag as "drop, don't carry forward" for whoever wires the frontend later.
- `/programas*` routes are NOT `RequireRole`-guarded yet (same gap Divisiones had before its
  wiring fixed it) — fix in that same frontend follow-up, not here.
