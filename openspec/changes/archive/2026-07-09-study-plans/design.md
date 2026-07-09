# Design: Study Plan Management (AcademicPlan CRUD)

## Technical Approach

Third `academic_config` aggregate, same hexagonal layering as `AcademicProgram`
(`domain/model|port/in|port/out|service`, `infrastructure/web|web/dto|persistence`).
The novelty is `AcademicPlan`'s owned child graph, `PlanLevel` → `Subject`. Both
children are plain JPA `@Entity` classes mapped as an intra-aggregate
`@OneToMany(cascade=ALL, orphanRemoval=true)` composition — never their own
Spring Data repository, controller, or out-port. `AcademicPlanRepository` is the
only persistence out-port for the whole aggregate; every child mutation loads
the plan, calls a mutator method on it, and saves the plan (cascade persists
the child).

## Architecture Decisions

### Decision: Child persistence — JPA composition, not separate tables reachable outside the aggregate

| Option | Tradeoff | Choice |
|---|---|---|
| `@ElementCollection`/embeddable | No own PK/id, awkward for REST resource identity (`levelId`, `subjectId`) | Rejected |
| Separate `PlanLevelRepository`/`SubjectRepository` out-ports | Lets other modules query children directly — breaks aggregate boundary | Rejected |
| `@OneToMany(cascade=ALL, orphanRemoval=true)`, bidirectional `@ManyToOne` back-ref, no child repository | Standard JPA aggregate pattern; child entity intra-aggregate `@ManyToOne` is NOT the same as `AcademicProgram.divisionId`'s plain-UUID cross-aggregate rule — that rule only bans references to *other aggregates* | **Chosen** |

`plan_level` gets `plan_id` FK column (`@ManyToOne @JoinColumn`); `subject` gets
`plan_level_id` FK (`@ManyToOne`) plus a denormalized plain `plan_id` UUID
column (kernel spec, no relation) for future flat queries. `AcademicPlan.levels`
is `@OrderBy("levelNumber ASC")`; `PlanLevel.subjects` `@OrderBy("displayOrder ASC")`.

### Decision: Boundary enforcement — no path to children except through the plan

**Choice**: `AcademicPlan` exposes `addLevel(...)`, `updateLevel(...)`,
`removeLevel(levelId)`, `addSubject(levelId, ...)`, `updateSubject(...)`,
`removeSubject(...)`. All child in-ports (below) depend only on
`AcademicPlanRepository`; they load the plan, invoke one mutator, then
`repository.save(plan)`. `removeLevel` throws `PlanLevelHasSubjectsException`
if the level still has subjects (must empty it first) or
`PlanLevelInUseException` if it equals `socialServiceMinLevelId`.
**Alternatives considered**: exposing `PlanLevelRepository` for direct
CRUD — rejected, defeats the whole point of the aggregate boundary.
**Rationale**: since JPA assigns generated ids on flush to the same
in-memory instance returned by the mutator, `addLevel`/`addSubject` can
return the created child directly for the use case to map to a response DTO.

### Decision: Use-case granularity — one interactor per child operation

**Choice**: 6 dedicated in-ports — `AddPlanLevelUseCase`,
`UpdatePlanLevelUseCase`, `RemovePlanLevelUseCase`, `AddSubjectToPlanUseCase`,
`UpdateSubjectUseCase`, `RemoveSubjectUseCase` — plus the 5 root CRUD ports
(Create/Update/Get/List/ChangeStatus), 11 total.
**Alternatives considered**: one fat `UpdateAcademicPlanUseCase` with a
discriminated command for every child op — rejected.
**Rationale**: matches the existing convention (one `XxxUseCase` per `XxxPort`,
`CreateAcademicProgramUseCase` never absorbed `AddPlanLevel`-style concerns).
A fat interactor would need an internal switch, losing the single-responsibility
clarity and testability the module already has.

### Decision: REST shape — nested paths, child-scoped payloads

`POST/PUT/GET /plans`, `GET /plans?programId=&status=&search=&page=&size=`,
`PATCH /plans/{id}/status` (mirrors Program). Children:
`POST /plans/{id}/levels`, `PUT /plans/{id}/levels/{levelId}`,
`DELETE /plans/{id}/levels/{levelId}`, `POST /plans/{id}/levels/{levelId}/subjects`,
`PUT .../subjects/{subjectId}`, `DELETE .../subjects/{subjectId}`. Child
endpoints return only the child DTO (not the whole plan) — simpler for a UI
adding levels/subjects one at a time; `GET /plans/{id}` is the only endpoint
returning the full nested tree. Unlike the root (never hard-deleted, per
Program's precedent), children DO support real `DELETE` — they aren't
independently auditable resources.

### Decision: `classificationId` on `Subject` — schema-only, unvalidated

`SubjectClassification` (kernel doc) has no aggregate in this codebase yet and
`GradeScale` is explicitly out of scope. `Subject.classificationId` is kept as
a plain nullable UUID column with zero existence validation now — same
schema-only precedent as `AcademicProgram.continuityProgramId`.

## Data Flow

    POST /plans/{id}/levels/{levelId}/subjects
        → AcademicPlanController → AddSubjectToPlanUseCaseImpl
        → AcademicPlanRepository.findById(id)
        → plan.addSubject(levelId, cmd)   (domain: uniqueness + level-exists checks)
        → AcademicPlanRepository.save(plan)   (cascades INSERT subject)
        → SubjectResponse (id assigned post-flush)

## File Changes

| File | Action | Description |
|---|---|---|
| `domain/model/{AcademicPlan,PlanLevel,Subject,PlanStatus,PlanLevelType,SubjectType}.java` | Create | Aggregate + children + enums |
| `domain/port/in/{Create,Update,Get,List,ChangeStatus}AcademicPlanUseCase.java` | Create | Root CRUD ports |
| `domain/port/in/{Add,Update,Remove}PlanLevelUseCase.java`, `{Add,Update,Remove}SubjectUseCase.java` | Create | 6 child ports |
| `domain/port/out/AcademicPlanRepository.java` | Create | Sole persistence out-port (incl. `PlanSearchCriteria/Page`) |
| `domain/service/*UseCaseImpl.java` (11) | Create | Interactors, `@Transactional` |
| `shared/exception/{AcademicPlanNotFoundException,ProgramNotFoundException,PlanLevelNotFoundException,SubjectNotFoundException,PlanLevelHasSubjectsException,PlanLevelInUseException,DuplicateLevelNumberException,DuplicateSubjectCodeException,InvalidSocialServiceLevelException}.java` | Create | `ProgramNotFoundException` mirrors `DivisionNotFoundException` (FK-check variant, distinct from the existing `AcademicProgramNotFoundException` direct-404) |
| `infrastructure/persistence/AcademicPlanJpaRepository.java` + `AcademicPlanRepositoryAdapter.java` | Create | Spring Data + adapter, sort by `version` |
| `infrastructure/web/AcademicPlanController.java` + `dto/*.java` | Create | 11 endpoints, request/response records |
| `infrastructure/web/GlobalExceptionHandler.java` | Modify | 9 new `@ExceptionHandler` mappings |
| `infrastructure/config/UseCaseConfig.java` | Modify | 11 new `@Bean` methods |
| `identity/infrastructure/security/SecurityFilterConfig.java` | Modify | `/plans/**` GET/POST/PUT/PATCH/DELETE matchers, ADMIN/SERVICIOS_ESCOLARES, placed after `/programs` |

## Interfaces / Contracts

```java
public class AcademicPlan {
  PlanLevel addLevel(int levelNumber, PlanLevelType type, String description);
  void updateLevel(UUID levelId, int levelNumber, PlanLevelType type, String description);
  void removeLevel(UUID levelId); // throws if has subjects or == socialServiceMinLevelId
  Subject addSubject(UUID levelId, String code, String name, int credits, int weeklyHours,
                      int evaluationUnits, int displayOrder, SubjectType type,
                      boolean isRetakeable, UUID classificationId);
  void updateSubject(UUID subjectId, /* same fields */);
  void removeSubject(UUID subjectId);
}
```

Exception → HTTP: `*NotFoundException` → 404; `Duplicate*Exception`,
`PlanLevelHasSubjectsException`, `PlanLevelInUseException` → 409;
`InvalidSocialServiceLevelException`, `ProgramNotFoundException` → 400.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | `AcademicPlan` invariants: duplicate `levelNumber`/`code`, remove-blocked-with-subjects, social-service level must belong to plan | JUnit 5 + AssertJ |
| Unit | 11 use cases, mocked `AcademicPlanRepository`/`AcademicProgramRepository` | Mockito |
| Integration | `AcademicPlanController` full CRUD + nested level/subject flows, cascade delete on plan removal path (N/A — no root delete) | `@SpringBootTest` + H2 + `MockMvc` |

## Migration / Rollout

No migration — H2 `create-drop` regenerates schema each boot. Additive only;
rollback = revert the commit(s), no impact on Division/Program.

## Open Questions

- [ ] `(programId, version)` uniqueness for `AcademicPlan` — not in the binding
  decisions table; recommend sdd-spec confirm before `tasks`.
- [ ] Concurrent child mutations (two staff adding levels to the same plan
  simultaneously) can lose an update — no `@Version` column added, consistent
  with Division/Program having none; accepted risk given low concurrency.
