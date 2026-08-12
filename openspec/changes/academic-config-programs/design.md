# Design: AcademicProgram CRUD (HU-PROG-010)

## Technical Approach

Second aggregate in the existing `academic_config` module. Clone the shipped
`AcademicDivision` template exactly: `domain/model|port/in|port/out|service`,
`infrastructure/persistence|web`, module-local `shared/exception`. EXTEND the
existing `academicConfigGlobalExceptionHandler` and `academicConfigUseCaseConfig`
beans with new `@ExceptionHandler`/`@Bean` methods — no new `@Configuration`/
`@Component` classes, so no bean-naming collision risk (that bug class doesn't
recur here). `divisionId` is validated in-service via the existing
`AcademicDivisionRepository.findById` — a direct same-module dependency, not a
cross-module port (unlike `PersonLookupPort`, which exists solely because
Person lives in `identity`).

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|---|---|---|---|
| Result record reuse | Single `AcademicProgramResult` reused by Create/Update/Get/ChangeStatus | Separate result per use case | Mirrors `AcademicDivisionResult`; all 4 ops return identical full-state shape |
| `ProgramStatus` sharing | Own enum, NOT shared with `DivisionStatus` | Reuse `DivisionStatus` | Same reasoning as Division/User non-sharing — different aggregate lifecycle, sharing couples unrelated domains |
| `divisionId` FK validation | Direct call to existing `AcademicDivisionRepository.findById` | New `DivisionLookupPort` | Same-module aggregates may depend on each other directly; ports-out are only for cross-module boundaries (`config.yaml` rule applies to cross-module, not intra-module) |
| Division-not-found exception | New `DivisionNotFoundException` (400), distinct from `AcademicDivisionNotFoundException` (404) | Reuse `AcademicDivisionNotFoundException` | Semantically different: 404 means "the Division resource itself wasn't found"; here the *Program* request references a bad FK — same shape as `DirectorNotFoundException` (400), not a resource-not-found |
| `divisionId` JPA mapping | Plain `UUID` column, no `@ManyToOne` | JPA relation to `AcademicDivision` | Matches `directorPersonId`'s precedent — no cross-aggregate object references, keeps aggregates independently loadable |
| Dual uniqueness enforcement | DB-level: unique `code`; composite unique `(offer_name, modality)` | App-only check | Matches Divisions' unique-column precedent; DB constraint is the source of truth, service-level check is UX-only pre-check |
| Route naming | `/programs` (plural, no prefix) | `/academic-programs` | Matches Divisions' `/divisions` convention exactly |

## Data Flow

    Controller (/programs) ──→ UseCaseImpl ──→ AcademicProgramRepository ──→ JPA
                                    │
                                    └──→ AcademicDivisionRepository.findById (FK check, same module)

## File Changes

| File | Action | Description |
|------|--------|--------------|
| `shared/model/AcademicLevel.java` | Create | Enum: TSU, CONTINUIDAD, INGENIERIA, LICENCIATURA, POSGRADO — plain enum, `RoleType.java` style |
| `shared/model/ProgramModality.java` | Create | Enum: PRESENCIAL, MIXTA |
| `academic_config/domain/model/AcademicProgram.java` | Create | Aggregate: id, divisionId, name, offerName, code, level, modality, continuityProgramId (nullable), status |
| `academic_config/domain/model/ProgramStatus.java` | Create | ACTIVE/INACTIVE, own enum |
| `academic_config/domain/port/in/{Create,Update,List,Get,ChangeStatus}AcademicProgramUseCase.java` | Create | 5 in-ports, `AcademicProgramResult` shared record on Create's file |
| `academic_config/domain/port/out/AcademicProgramRepository.java` | Create | save, findById, findByCode, findByOfferNameAndModality, search(criteria)→page |
| `academic_config/domain/service/*UseCaseImpl.java` | Create | 5 impls mirroring Division impls |
| `academic_config/shared/exception/{AcademicProgramNotFoundException,DuplicateProgramCodeException,DuplicateOfferNameModalityException,DivisionNotFoundException}.java` | Create | 404 / 409 / 409 / 400 |
| `academic_config/infrastructure/persistence/AcademicProgramJpaRepository.java` | Create | Spring Data + `@Query` search (divisionId/status/search filters) |
| `academic_config/infrastructure/persistence/AcademicProgramRepositoryAdapter.java` | Create | Sort by `name` ASC, mirrors Division adapter |
| `academic_config/infrastructure/web/AcademicProgramController.java` | Create | `/programs` — POST, PUT/{id}, GET, GET/{id}, PATCH/{id}/status |
| `academic_config/infrastructure/web/dto/*.java` | Create | Create/Update/ChangeStatus request, Response, ListItemResponse, ListResponse |
| `academic_config/infrastructure/web/GlobalExceptionHandler.java` | Modify | Add 4 `@ExceptionHandler` methods for the new exceptions |
| `academic_config/infrastructure/config/UseCaseConfig.java` | Modify | Add 5 `@Bean` methods |
| `identity/infrastructure/security/SecurityFilterConfig.java` | Modify | 4 verb-split `/programs` matchers, placed after `/divisions` matchers, before `anyRequest()` |

## Interfaces / Contracts

```java
record CreateAcademicProgramCommand(UUID divisionId, String name, String offerName, String code,
    AcademicLevel level, ProgramModality modality, UUID continuityProgramId) {}

record AcademicProgramResult(UUID id, UUID divisionId, String name, String offerName, String code,
    AcademicLevel level, ProgramModality modality, UUID continuityProgramId, ProgramStatus status) {}

// JPA entity
@Table(name = "academic_program",
    uniqueConstraints = @UniqueConstraint(columnNames = {"offer_name", "modality"}))
// code: @Column(nullable = false, unique = true)
```

`ListAcademicProgramsUseCase.ListAcademicProgramsQuery(ProgramStatus status, String search, UUID divisionId, int page, int size)` — `divisionId` filter is a plain optional-equality filter (`:divisionId IS NULL OR p.divisionId = :divisionId`), no `EXISTS` subquery needed (simple FK column, not a child collection — same reasoning as Divisions' search query).

## Testing Strategy

| Layer | What to Test | Approach |
|-------|--------------|----------|
| Unit | 5 use case impls: uniqueness rejection (code, offerName+modality), FK validation, status idempotency | Mocked `AcademicProgramRepository` + `AcademicDivisionRepository`, mirrors `*UseCaseImplTest` |
| Integration | Dual-uniqueness DB constraints, `divisionId` filter, status filter, search | `@DataJpaTest` mirroring `AcademicDivisionRepositoryAdapterSearchIT` |
| Integration | Role-based access (ADMIN/SERVICIOS_ESCOLARES allowed, others 403, unauthenticated 401) | Controller IT mirroring `AcademicDivisionControllerIT` |

## Migration / Rollout

No migration required — additive only, H2 in-memory schema recreated on boot.

## Open Questions

None — all decisions resolved by proposal + this design; PO already settled the 3 substantive product decisions in the proposal.
