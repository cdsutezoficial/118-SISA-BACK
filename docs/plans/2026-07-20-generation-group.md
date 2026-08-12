# Plan: Generation + Group (academic_config)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` (líneas 170-201) + `docs/requirements/01-PROGRAMACION.md` (RF-PROG-002, RF-PROG-005, RN-PROG-003).

### Generation (Aggregate Root)

> Cohorte de estudiantes que ingresan juntos a un plan en un periodo específico.

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| planId | UUID | FK → AcademicPlan |
| startPeriodId | UUID | FK → AcademicPeriod |
| number | int | Consecutivo de la generación **dentro del programa** |
| code | String | Ej: "2024-1" — **generado automáticamente** |
| status | Enum | ACTIVE, FINISHED |

RF-PROG-002: crear generación asociada a un plan, "hereda la estructura del plan vigente al momento de apertura" (esto ya lo resuelve el propio `planId` — al ser FK a una versión específica de `AcademicPlan`, no hace falta copiar/snapshot nada aparte), ver activas e históricas.

### Group (Aggregate Root)

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| generationId | UUID | FK → Generation |
| periodId | UUID | FK → AcademicPeriod — periodo en que está activo |
| planLevelId | UUID | FK → PlanLevel — nivel/cuatrimestre que cursa |
| programId | UUID | FK → AcademicProgram — denormalizado para consultas |
| code | String | Ej: "3A", "3B" |
| maxCapacity | int | |
| shift | Shift | MORNING, AFTERNOON, MIXED — Shared Kernel, **no implementado aún en el backend** (`shared/model/` no tiene `Shift.java` — confirmado, solo existen `AcademicLevel`, `ProgramModality`, `RoleType`) |
| status | Enum | OPEN, CLOSED |

**Regla documentada, fuera de alcance de este plan**: al cerrar un periodo, si no existe el grupo del nivel siguiente para el periodo siguiente, el sistema lo crea automáticamente. Depende de un hook sobre la transición `AcademicPeriod.CLOSED` — no se implementa acá, se deja como trabajo futuro explícito.

## 2. Comparación docs vs. implementación actual

**Backend**: nada implementado — ni `Generation` ni `Group` ni `Shift` (confirmado: `find ... -iname "*Generation*" -o -iname "*Group*"` → vacío).

**Frontend** (`GruposList.tsx`/`GruposForm.tsx`, mock): no tiene ningún concepto de Generación — ni como filtro, ni como campo del form, ni en los datos de ejemplo. Tampoco tiene `shift` (turno). El prompt de Figma (Pantallas 8-9) tampoco lo contempla — **es un gap de diseño, no solo de implementación**, mismo tipo de problema que encontramos con Clasificaciones (pantalla nunca especificada).

## 3. Decisión de PO (confirmada 2026-07-20)

`Generation.code` = `{año del startPeriod}-{number}`, donde `number` es un consecutivo **puramente secuencial por programa, que NUNCA reinicia** — el año en el código es solo informativo (de cuándo arrancó esa generación), no parte de una regla de reinicio anual.

**Contexto real de negocio, importante para no inventar una restricción incorrecta**: José confirmó que **puede abrir más de una generación del mismo programa en el mismo año calendario** — ej. una generación de ingreso Septiembre 2026, y si algunos aspirantes no quedaron esa vez, una siguiente generación de ingreso Enero 2027, y otra en Septiembre 2027. Esto depende de su planeación de venta de fichas de admisión, no es fijo. **No hay que asumir "una generación por año por programa"** — el `number` simplemente avanza cada vez que se abre una generación nueva para ese programa, sin importar el calendario. No agregar ninguna unicidad sobre `(programId, año)` — solo unicidad de `number` dentro del mismo programa (el consecutivo en sí no puede repetirse).

## 4. Patrón a clonar

Ambos son aggregates root propios (como `AcademicPeriod`), mismo módulo `academic_config`.

**Orden de entrega** (mismo criterio que Clasificación→Escala hoy): primero `Generation` (bloqueante), después `Group` (que la consume).

### Generation
- `POST/GET /generations`, `GET/PUT /generations/{id}`, `PATCH /generations/{id}/status` (ACTIVE→FINISHED,¿secuencial de 2 pasos o toggle simple? Al ser solo 2 valores, se trata como toggle simple salvo que digas lo contrario — no es como los 4 estados de Period).
- Validar `planId` contra `AcademicPlanRepository`, `startPeriodId` contra `AcademicPeriodRepository` (llamadas directas, mismo patrón intra-módulo ya usado).
- Unicidad `number` dentro del mismo `planId`'s programId — requiere resolver `programId` desde `planId` (join indirecto) o denormalizar `programId` en `Generation` también (a decidir en el diseño técnico, no es una decisión de negocio).

### Group
- `POST/GET /groups`, `GET/PUT /groups/{id}`, `PATCH /groups/{id}/status` (OPEN↔CLOSED, toggle simple).
- Nuevo `Shift.java` en `shared/model/` (MORNING, AFTERNOON, MIXED) — primer consumidor.
- Validar `generationId`, `periodId`, `planLevelId` contra sus repositorios respectivos (todas llamadas directas intra-módulo, `planLevelId` es más indirecto: `PlanLevel` no tiene repo propio, hoy solo se accede vía `AcademicPlan.getLevels()` — a resolver en el diseño técnico cómo validar su existencia sin romper el encapsulamiento ya establecido para `PlanLevel`/`Subject`).
- `programId` se autocompleta desde `generationId`→`planId`→`AcademicPlan.programId` (denormalizado, no lo captura el usuario).

## 5. Frontend — gap de diseño, no solo de wiring

No hay pantalla de "Generaciones" especificada en ningún lado. Antes de cablear `GruposList`/`GruposForm`, hace falta:
1. Especificar 2 pantallas nuevas de Generaciones en el Figma prompt de `118-SISA-CLAUDE` (mismo criterio que se hizo con Clasificaciones).
2. Corregir Pantallas 8-9 de Grupos: agregar filtro/campo de Generación y campo de Turno (`shift`), ninguno de los dos existe hoy.

## 6. Fuera de alcance

- Auto-creación de grupo del siguiente nivel al cerrar un periodo (regla documentada, requiere trabajo aparte sobre el hook de cierre de periodo).
- Asignación de estudiantes a grupos (RF-PROG-005, criterio "manual o aleatoria") — depende de que exista `Student`/`Enrollment` en este backend, que no existe (`118-SISA-FRONT`'s módulo Inscripciones está mockeado, sin contraparte real en `118-SISA-BACK`).

## Execution Log

### Generation — implementado (2026-07-21)

**Estado**: `Generation` completo end-to-end (dominio, casos de uso, persistencia, web, seguridad, tests). `Group` NO está implementado — sigue pendiente para una sesión posterior, incluyendo `Shift.java` (que este trabajo deliberadamente no tocó).

**Decisión técnica — resolución de `programId`**: se denormalizó `programId` directamente en `Generation` (opción b del prompt), en vez de resolverlo vía join indirecto `planId -> AcademicPlan.programId` en cada consulta. Razones:
1. `GET /generations` expone un filtro `programId` explícito. Ningún `search()` existente en el módulo (`AcademicPeriodJpaRepository`, `AcademicPlanJpaRepository`, `AcademicProgramJpaRepository`) filtra con un JOIN a otra tabla — todos filtran sobre columnas propias de la entidad consultada. Sin denormalizar, ese filtro habría exigido un patrón nuevo (join) inexistente en el resto del módulo.
2. La unicidad de `number` está scoped a `programId`, no a `planId` — con `programId` como columna propia, `findByProgramIdAndNumber` es una derived query trivial, igual que `AcademicPeriodRepository.findByYearAndPeriodNumber` o `AcademicPlanRepository.findByProgramIdAndVersion`.
3. Mismo criterio que el plan ya documentó para `Group.programId` ("denormalizado para consultas") — aplicar la misma regla a `Generation` mantiene el módulo consistente en vez de tener un caso resuelto por join y otro por denormalización sin razón aparente.

`programId` se resuelve UNA vez, en `CreateGenerationUseCaseImpl`/`UpdateGenerationUseCaseImpl`, vía `AcademicPlanRepository.findById(planId).getProgramId()` (llamada directa intra-módulo, mismo patrón que `CreateAcademicProgramUseCaseImpl` resolviendo `divisionId` contra `AcademicDivisionRepository`) — nunca se acepta `programId` como input del cliente.

**Excepciones nuevas** (no había ninguna reutilizable para "FK referenciada no existe" de `AcademicPlan`/`AcademicPeriod` — solo existían para `AcademicDivision`/`AcademicProgram`):
- `GenerationNotFoundException` (404) — el recurso Generation no existe.
- `DuplicateGenerationNumberException` (409) — `number` ya usado por otra generación del mismo programa.
- `PlanNotFoundException` (400, NUEVA) — `planId` no referencia un `AcademicPlan` existente. Distinta de `AcademicPlanNotFoundException` (404), que es para "el plan en sí no se encontró" (GET/PUT `/plans/{id}`).
- `PeriodNotFoundException` (400, NUEVA) — `startPeriodId` no referencia un `AcademicPeriod` existente. Misma distinción respecto a `AcademicPeriodNotFoundException` (404).

**Regla de negocio verificada con tests dedicados**: NO existe unicidad `(programId, year)` — un programa puede abrir múltiples generaciones en el mismo año calendario (ver `CreateGenerationUseCaseImplTest.createGeneration_allowsMultipleGenerationsForSameProgramInSameCalendarYear` y `GenerationControllerIT.multipleGenerationsForSameProgramInSameYearAreAllowed`, ambos regression guards explícitos).

**Archivos creados**:
- Dominio: `domain/model/Generation.java`, `domain/model/GenerationStatus.java`.
- Puertos-in: `CreateGenerationUseCase`, `UpdateGenerationUseCase`, `GetGenerationUseCase`, `ListGenerationsUseCase`, `ChangeGenerationStatusUseCase` (+ 5 impls en `domain/service/`).
- Puerto-out: `domain/port/out/GenerationRepository.java`.
- Excepciones: `GenerationNotFoundException`, `DuplicateGenerationNumberException`, `PlanNotFoundException`, `PeriodNotFoundException`.
- Persistencia: `infrastructure/persistence/GenerationJpaRepository.java`, `GenerationRepositoryAdapter.java`.
- Web: `infrastructure/web/GenerationController.java` + 6 DTOs (`GenerationResponse`, `GenerationListItemResponse`, `GenerationListResponse`, `CreateGenerationRequest`, `UpdateGenerationRequest`, `ChangeGenerationStatusRequest`).
- Tests: `GenerationTest` (10), `CreateGenerationUseCaseImplTest` (9), `UpdateGenerationUseCaseImplTest` (6), `GetGenerationUseCaseImplTest` (2), `ListGenerationsUseCaseImplTest` (5), `ChangeGenerationStatusUseCaseImplTest` (3), `GenerationRepositoryAdapterSearchIT` (9), `GenerationControllerTest` (15), `GenerationControllerIT` (22) — 81 tests nuevos en total.

**Archivos modificados**: `GlobalExceptionHandler.java` (4 handlers nuevos), `UseCaseConfig.java` (5 beans nuevos), `identity/infrastructure/security/SecurityFilterConfig.java` (4 matchers `/generations` por verbo, mismo par ADMIN/SERVICIOS_ESCOLARES).

**Resultados de tests**: baseline 366 unit / 143 IT -> final **416 unit / 174 IT**, 0 failures/errors (`./mvnw verify`).

**Pendiente para la próxima sesión (Group)**: implementar `Group` (aggregate root), `Shift.java` en `shared/model/` (MORNING, AFTERNOON, MIXED — primer consumidor), y resolver cómo validar `planLevelId` sin repo propio (hoy solo accesible vía `AcademicPlan.getLevels()`).

### Group — diseño técnico resuelto (2026-07-23), pendiente de implementar

**Validación de `planLevelId` sin repo propio**: no requiere ningún componente nuevo. `AcademicPlan.hasLevel(UUID levelId)` ya existe (usado hoy de forma intra-aggregate por `UpdateAcademicPlanUseCaseImpl` para `socialServiceMinLevelId`) y es público — reutilizable cross-aggregate sin romper el encapsulamiento de `PlanLevel`. `PlanLevelNotFoundException` (404) también ya existe en `academic_config/shared/exception/` y su Javadoc ya cubre el caso "el id pertenece a un plan distinto" — exactamente el escenario de `Group`.

**Flujo en `CreateGroupUseCaseImpl`/`UpdateGroupUseCaseImpl`**:
1. `GenerationRepository.findById(generationId)` → si no existe, **nueva** `GenerationReferenceNotFoundException` (400). No se reutiliza `GenerationNotFoundException` (esa es el 404 de `GET /generations/{id}`) — mismo criterio de separación 400-vs-404 ya usado para `PlanNotFoundException`/`AcademicPlanNotFoundException` y `PeriodNotFoundException`/`AcademicPeriodNotFoundException`.
2. `Group.programId` se copia directo de `generation.getProgramId()` (ya denormalizado en `Generation`) — **simplificación respecto al plan original**: no hace falta resolver `planId → AcademicPlan.programId` para esto, solo para el paso 3.
3. `AcademicPlanRepository.findById(generation.getPlanId())` → `plan.hasLevel(planLevelId)` → si `false`, reutilizar `PlanLevelNotFoundException` (404, ya existente, sin cambios).
4. `AcademicPeriodRepository.findById(periodId)` → si no existe, reutilizar `PeriodNotFoundException` (400, ya existente de Generation).

**Resultado**: cero cambios de schema, cero método nuevo en `AcademicPlanRepository`, una sola excepción nueva (`GenerationReferenceNotFoundException`). Todo lo demás (creación de `Shift.java`, endpoints CRUD, toggle OPEN/CLOSED, tests) sigue el mismo patrón ya usado en `Generation`/`AcademicPeriod`.

### Group — implementado (2026-07-27)

**Estado**: `Group` completo end-to-end (dominio, casos de uso, persistencia, web, seguridad, tests), incluyendo `Shift.java`. El diseño técnico ya estaba resuelto en la sección anterior — esta sesión lo implementó tal cual, sin desviaciones de fondo.

**Decisiones técnicas durante la implementación**:

1. **Nombre de tabla `"groups"` (plural), no `"group"`**: `GROUP` es palabra reservada SQL (usada por `GROUP BY`) en H2/ANSI SQL. Mismo tipo de gotcha que `AcademicPeriod`'s columna `period_year` (ahí se renombró la *columna*; acá, al ser el identificador de la *tabla* mismo, se renombró a su plural en inglés en vez de forzar el quoting del identificador reservado en cada query). Documentado en el javadoc de `Group.java`.
2. **`programId` se copia directo de `generation.getProgramId()`**, sin pasar por `AcademicPlanRepository` — tal como ya resolvía la sección de diseño técnico, más simple que el propio `Generation` porque `Generation` ya denormaliza `programId` desde su `planId`.
3. **`AcademicPlan.hasLevel(UUID)` se reutilizó sin cambios** para validar `planLevelId` cross-aggregate — cero método nuevo, cero cambio de encapsulamiento. `PlanLevelNotFoundException` (404, ya existente) cubre tanto "el id no existe" como "el id pertenece a otro plan", exactamente como su Javadoc ya documentaba.
4. **`CreateGenerationUseCaseImpl.requirePlan`/`requirePeriod` se reutilizaron como métodos estáticos** desde `CreateGroupUseCaseImpl`, en vez de duplicar la lógica de validación de `AcademicPlanRepository`/`AcademicPeriodRepository` — mismo patrón de reutilización que ya usaba `UpdateGenerationUseCaseImpl` con los helpers de `CreateGenerationUseCaseImpl`.
5. **`UpdateGroupUseCaseImpl` re-resuelve `programId` en cada `PUT`**, no lo deja intacto — a diferencia de otros aggregates donde un campo denormalizado se fija en la creación, un `Group` puede moverse a otra `generationId` en una actualización, y su `programId` denormalizado debe reflejar siempre la generación actualmente asociada.
6. **`Group.code` NO tiene regla de unicidad** — el diseño técnico resuelto (2026-07-23) no la especificaba, y no se inventó ninguna (a diferencia de `Generation.number`, que sí es único por programa).
7. **`ListGroupsUseCase` agrega el filtro `generationId`** además de `status`/`search`/`programId` (que ya tenía `Generation`) — `Group` es el único aggregate de este módulo con FK propia a otro aggregate del mismo módulo que además tiene sentido filtrar, a diferencia de `Generation` que no filtra por `planId`.

**Archivos creados**:
- Shared kernel: `shared/model/Shift.java` (MORNING, AFTERNOON, MIXED).
- Dominio: `domain/model/Group.java`, `domain/model/GroupStatus.java`.
- Puertos-in: `CreateGroupUseCase`, `UpdateGroupUseCase`, `GetGroupUseCase`, `ListGroupsUseCase`, `ChangeGroupStatusUseCase` (+ 5 impls en `domain/service/`).
- Puerto-out: `domain/port/out/GroupRepository.java`.
- Excepciones: `GenerationReferenceNotFoundException` (400, nueva), `GroupNotFoundException` (404, nueva). `PlanLevelNotFoundException`/`PeriodNotFoundException` reutilizadas sin cambios.
- Persistencia: `infrastructure/persistence/GroupJpaRepository.java`, `GroupRepositoryAdapter.java`.
- Web: `infrastructure/web/GroupController.java` + 6 DTOs (`GroupResponse`, `GroupListItemResponse`, `GroupListResponse`, `CreateGroupRequest`, `UpdateGroupRequest`, `ChangeGroupStatusRequest`).
- Tests: `GroupTest` (9), `CreateGroupUseCaseImplTest` (6), `UpdateGroupUseCaseImplTest` (5), `GetGroupUseCaseImplTest` (2), `ListGroupsUseCaseImplTest` (5), `ChangeGroupStatusUseCaseImplTest` (3), `GroupRepositoryAdapterSearchIT` (9), `GroupControllerTest` (14), `GroupControllerIT` (20) — 73 tests nuevos en total (44 unit / 29 IT).

**Archivos modificados**: `GlobalExceptionHandler.java` (2 handlers nuevos: `GenerationReferenceNotFoundException` → 400, `GroupNotFoundException` → 404), `UseCaseConfig.java` (5 beans nuevos), `identity/infrastructure/security/SecurityFilterConfig.java` (4 matchers `/groups` por verbo, mismo par ADMIN/SERVICIOS_ESCOLARES).

**Resultados de tests**: la sesión anterior reportó baseline 416 unit / 174 IT tras `Generation`; al arrancar esta sesión el conteo real ya era **447 unit / 174 IT** (el número de IT coincidía exactamente, el de unit estaba desactualizado en la doc por trabajo intermedio no relacionado con este plan — no se investigó más a fondo por ser irrelevante para Group). Final tras `Group`: **491 unit / 203 IT**, 0 failures/errors (`./mvnw verify`).

**Gotcha de entorno**: `JAVA_HOME` del shell apuntaba a Corretto 17 (release 21 no soportado por el compilador); el JDK 21 correcto vivía en un perfil de usuario distinto (`C:\Users\JoseNarvaez\.jdks\corretto-21.0.10`, con mayúscula/sin espacio, distinto del perfil `Jose Narvaez` usado por defecto). Se resolvió pasando `JAVA_HOME` explícito a cada invocación de `./mvnw`.

**Gotcha de tests IT**: `GroupControllerIT` inicialmente reusaba el año 2026 para sus periodos de prueba, con un contador estático propio arrancando en 1 — igual que `GenerationControllerIT`. Como ambas clases IT comparten la misma instancia H2 vía el mismo contexto `@SpringBootTest`, y `AcademicPeriod` tiene unicidad `(year, periodNumber)` global (no por test), esto colisionaba con los periodos que `GenerationControllerIT` ya había insertado para el año 2026. Solución: `GroupControllerIT` usa el año 2027 para sus propios periodos.
