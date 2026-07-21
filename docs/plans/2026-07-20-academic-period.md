# Plan: AcademicPeriod (aggregate root, academic_config)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` (líneas 151-166) + `docs/requirements/01-PROGRAMACION.md` (RF-PROG-003, RN-PROG-001/007).

`AcademicPeriod` es un **aggregate root propio** (a diferencia de `GradeScale`) — mismo estilo que `AcademicDivision`/`AcademicProgram`/`SubjectClassification`: tabla, repositorio y controller propios.

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| name | String | Ej: "Enero-Abril 2026" |
| year | int | |
| periodNumber | int | 1, 2, 3 dentro del año |
| type | Enum | CUATRIMESTRAL, SEMESTRAL, BIMESTRAL |
| startDate | LocalDate | |
| endDate | LocalDate | |
| enrollmentStart | LocalDate | Inicio del periodo de inscripciones |
| enrollmentEnd | LocalDate | Cierre del periodo de inscripciones |
| status | Enum | CONFIGURATION, ENROLLMENT, ACTIVE, CLOSED |

RF-PROG-003 confirma: "NO asumir cuatrimestres fijos — 100% configurable", "debe soportar bimestrales/semestrales a futuro (posgrados)". RN-PROG-001 refuerza lo mismo. Nada de esto es nuevo respecto al modelo — ya está cubierto por el enum `type`.

**Diferencia importante respecto a todo lo construido hoy**: `status` no es un toggle binario (ACTIVE/INACTIVE) como Division/Program/Classification — son **4 estados** que representan un ciclo de vida (CONFIGURATION → ENROLLMENT → ACTIVE → CLOSED). El dominio dice que el estado "controla qué operaciones están habilitadas en el sistema en cada momento del ciclo escolar" pero no especifica las transiciones válidas.

## 2. Comparación docs vs. implementación actual

`118-SISA-BACK`: nada implementado (`find ... -iname "*period*"` → vacío). Greenfield, igual que `SubjectClassification` fue al principio.

Consumidores futuros ya documentados que dependen de `AcademicPeriod` pero están fuera de alcance de este plan: `Generation.startPeriodId`, `Group.periodId` (ninguno de los dos existe todavía en el backend).

## 3. Patrón a clonar

Mismo patrón que `SubjectClassification`/`AcademicDivision` (aggregate root propio, hexagonal, mismo módulo `academic_config`):

```
domain/model/AcademicPeriod.java + PeriodType.java (enum) + PeriodStatus.java (enum)
domain/port/in/{Create,Update,List,Get,ChangeStatus}AcademicPeriodUseCase.java
domain/port/out/AcademicPeriodRepository.java
domain/service/{...}UseCaseImpl.java
infrastructure/persistence/AcademicPeriodJpaRepository.java + ...RepositoryAdapter.java
infrastructure/web/AcademicPeriodController.java + dto/*
shared/exception/{PeriodNotFoundException, InvalidPeriodStatusTransitionException}.java
```

Endpoints: `GET/POST /periods`, `GET/PUT /periods/{id}`, `PATCH /periods/{id}/status`. Nuevos matchers en `SecurityFilterConfig.java` por verbo (mismo gotcha ya visto con `SubjectClassification`: cada verbo nuevo necesita su propia línea, el wildcard de GET no se hereda a otros verbos).

## 4. Validaciones de negocio propuestas (no todas confirmadas)

1. `startDate < endDate` — básica, sin ambigüedad.
2. `enrollmentStart < enrollmentEnd` — básica, sin ambigüedad.
3. **Propuesto, no confirmado**: unicidad `(year, periodNumber)` — el propio atributo dice "1, 2, 3 dentro del año", lo cual implica que no puede repetirse el número de periodo en el mismo año. Se implementa así salvo que corrijas.
4. **Propuesto, no confirmado**: `enrollmentEnd <= endDate` (las inscripciones no pueden cerrar después de que termina el periodo) — razonable pero no está escrito en el dominio, avisar si no aplica.

## 5. Decisión de PO (confirmada 2026-07-20)

Transiciones de `status` **estrictamente secuenciales y hacia adelante**: `CONFIGURATION → ENROLLMENT → ACTIVE → CLOSED`, sin saltos (no CONFIGURATION→ACTIVE directo) ni retrocesos (no CLOSED→ACTIVE). Nueva excepción `InvalidPeriodStatusTransitionException` (400) si se pide una transición fuera de esa secuencia.

## 6. Entrega

Un solo tramo (mismo criterio que `GradeScale`): modelo + Create + List + Get + Update + ChangeStatus con las reglas de transición secuencial, TDD de punta a punta.

## Execution Log

**Estado: implementado y verificado (2026-07-20).**

### Archivos creados

Dominio:
- `domain/model/AcademicPeriod.java` — entidad, aggregate root propio (tabla `academic_period`)
- `domain/model/PeriodType.java`, `PeriodStatus.java` — enums
- `domain/port/out/AcademicPeriodRepository.java`
- `domain/port/in/{Create,Update,List,Get,ChangeStatus}AcademicPeriodUseCase.java`
- `domain/service/{Create,Update,List,Get,ChangeStatus}AcademicPeriodUseCaseImpl.java`

Infraestructura:
- `infrastructure/persistence/AcademicPeriodJpaRepository.java`, `AcademicPeriodRepositoryAdapter.java`
- `infrastructure/web/AcademicPeriodController.java`
- `infrastructure/web/dto/{Create,Update}AcademicPeriodRequest.java`, `ChangePeriodStatusRequest.java`, `AcademicPeriodResponse.java`, `AcademicPeriodListItemResponse.java`, `AcademicPeriodListResponse.java`

Excepciones nuevas:
- `shared/exception/AcademicPeriodNotFoundException.java` (404)
- `shared/exception/DuplicatePeriodException.java` (409)
- `shared/exception/InvalidPeriodStatusTransitionException.java` (400)
- Reutilizada `InvalidPlanDataException` (400) para las 3 validaciones de rango de fechas — ver "Decisiones de diseño" abajo.

Archivos modificados:
- `infrastructure/web/GlobalExceptionHandler.java` — 3 handlers nuevos
- `infrastructure/config/UseCaseConfig.java` — 5 beans nuevos
- `identity/infrastructure/security/SecurityFilterConfig.java` — 4 matchers nuevos (`GET/POST/PUT/PATCH /periods`, `/periods/**`, roles ADMIN + SERVICIOS_ESCOLARES)

Tests (TDD, todos escritos antes o junto con la implementación de cada capa):
- `domain/model/AcademicPeriodTest.java` (21 tests: construcción, 3 reglas de fecha, `updateDetails`, máquina de estados completa)
- `domain/service/{Create,Update,List,Get,ChangeStatus}AcademicPeriodUseCaseImplTest.java` (4+4+4+2+3 = 17 tests)
- `infrastructure/persistence/AcademicPeriodRepositoryAdapterSearchIT.java` (8 tests, `@DataJpaTest`)
- `infrastructure/web/AcademicPeriodControllerTest.java` (14 tests, `@WebMvcTest`)
- `infrastructure/web/AcademicPeriodControllerIT.java` (26 tests, JWT real end-to-end, escrito desde el inicio — no se repitió el error de Fase 1 de `SubjectClassification` de omitir el IT)

Total: 52 tests unitarios nuevos + 34 tests de integración nuevos = 86 tests nuevos, 0 fallos.

### Algoritmo de transición de estado

`AcademicPeriod#changeStatus(PeriodStatus target)` usa un mapa fijo
`NEXT_STATUS` (`EnumMap`): `CONFIGURATION→ENROLLMENT`, `ENROLLMENT→ACTIVE`,
`ACTIVE→CLOSED`. `CLOSED` no tiene entrada (terminal). La validación es:
`expectedNext = NEXT_STATUS.get(currentStatus); if (expectedNext == null ||
expectedNext != target) throw InvalidPeriodStatusTransitionException`. Esto
cubre saltos, retrocesos, y también **transición al mismo estado** (p. ej.
`CONFIGURATION → CONFIGURATION` es rechazada) — a diferencia de los toggles
binarios ACTIVE/INACTIVE del resto del módulo, esta máquina de estados NO es
idempotente, porque "el estado actual nunca es su propio siguiente
inmediato". Confirmado con 12 tests dedicados en `AcademicPeriodTest`
(incluyendo el caso explícito de mismo-estado).

### Decisiones de diseño

1. **Validación de rango de fechas vive en la entidad, no en el use case**
   (a diferencia de `minPassingGrade` en `AcademicPlan` o `numericMin` en
   `GradeScale`, que se validan en el use case). Razón: las 3 reglas
   (`startDate < endDate`, `enrollmentStart < enrollmentEnd`, `enrollmentEnd
   <= endDate`) comparan campos de la MISMA entidad entre sí — son invariantes
   estructurales puros, sin necesidad de repositorio — igual que
   `GradeScale#validateEntries`. Se reutilizó `InvalidPlanDataException`
   (favoreciendo reuso, como pidió la tarea) en vez de crear una excepción
   nueva, ya que esa excepción ya cubre "simple caller-input range
   validation" para varios aggregates de este módulo.
2. **Unicidad `(year, periodNumber)` sí vive en el use case** (no en la
   entidad) porque requiere acceso a repositorio — mismo patrón que la
   unicidad de `code`/`version` en `SubjectClassification`/`AcademicPlan`.
3. **Gotcha de columna SQL reservada**: el campo `year` colisiona con la
   palabra reservada `YEAR` de H2/SQL estándar. La generación de DDL y la
   query JPQL con `p.year` fallaban con "expected identifier" hasta mapear
   la columna explícitamente a `period_year` vía `@Column(name =
   "period_year")` — el nombre del campo Java (`year`) no cambió, solo el
   nombre de columna. Encontrado en el primer run del IT de persistencia.

### Resultados de tests

- Baseline real (medido con `clean verify` antes de empezar): **314 unit /
  109 IT**, 0 fallos — coincide con lo esperado en el prompt de la tarea. (Una
  primera medición sin `clean` dio 360/109 por reportes `target/`
  desactualizados de una corrida manual anterior — descartada.)
- Final (`clean verify`): **366 unit / 143 IT**, 0 fallos, 0 errores.
- Delta: +52 unit, +34 IT — exactamente los tests nuevos de este aggregate.
