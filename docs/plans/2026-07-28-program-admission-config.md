# Plan: ProgramAdmissionConfig (Fase 1 — Configuración de venta de fichas)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` (líneas 205-226, `ProgramAdmissionConfig`) + `docs/requirements/02-ADMISION.md` (RF-ADM-001).

**Aclaración importante, igual que con Conceptos de Pago**: aunque el requerimiento vive en "Módulo 2 — Admisión", el diseño de dominio ubica `ProgramAdmissionConfig` dentro de **`academic_config`** (el mismo bounded context de siempre) — sería su **décimo aggregate**. El bounded context `admission` propio (`Candidate`, `AdmissionExamResult`, `InductionCourseResult`, `AdmissionPayment`) es trabajo aparte, futuro, y depende de que este aggregate exista primero (`GetAvailableProgramsForAdmissionUseCase` lo consulta para saber qué programas tienen cupo).

`ProgramAdmissionConfig` controla **qué programas se ofertan en cada proceso de admisión**: cupo, ventana de venta de fichas (fecha/hora apertura-cierre) y generación destino de los aceptados.

## 2. Comparación docs vs. implementación actual

Cero código — no existe ni `ProgramAdmissionConfig` ni nada del bounded context `admission`.

## 3. Alcance de esta fase

Solo el aggregate `ProgramAdmissionConfig` — el catálogo/configuración. **Fuera de alcance explícito**: el "panel de control: fichas disponibles, vendidas, pagadas" que pide RF-ADM-001 — ese panel necesita contar `AdmissionPayment`/`Candidate` (bounded context `admission`, 0% construido) para calcular fichas vendidas/pagadas; no se puede construir hasta que exista `Candidate`. Este plan solo cubre la configuración en sí (cupo máximo, ventana de fechas, generación destino), no las métricas en vivo.

También fuera de alcance: cualquier caso de uso del bounded context `admission` (`RegisterCandidateUseCase`, etc.) y el cambio de `selectionStatus` a `PUBLISHED` (eso lo dispara `PublishAdmissionResultsUseCase`, que vive en `admission` y no existe todavía) — `selectionStatus` se modela en el aggregate pero permanece en `IN_REVIEW` desde su creación, sin caso de uso propio para cambiarlo en esta fase.

## 4. Modelo (campos exactos del doc)

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| programId | UUID | FK → `AcademicProgram`, obligatorio, validado (debe existir) |
| periodId | UUID | FK → `AcademicPeriod` — periodo DESTINO (al que ingresarán los aceptados, NO el periodo en que corre la venta de fichas), obligatorio, validado |
| targetGenerationId | UUID | FK → `Generation` — generación que recibirá a los aceptados, obligatorio, validado |
| isOffered | boolean | Equivalente al "ofertada" del SISA actual |
| maxCandidates | int | > 0 — cupo máximo de fichas **pagadas** por programa |
| opensAt | Instant | Apertura de venta de fichas — puede ser meses antes del `periodId` destino |
| closesAt | Instant | Cierre de venta de fichas — debe ser posterior a `opensAt` |
| status | Enum | `OPEN, CLOSED` — controla la ventana de venta (toggle, mismo patrón que el resto del módulo) |
| selectionStatus | Enum | `IN_REVIEW, PUBLISHED` — se crea siempre en `IN_REVIEW`; sin caso de uso para cambiarlo en esta fase (ver sección 3) |

**Restricción única**: `(programId, periodId)` — no puede haber dos configuraciones de admisión para el mismo programa en el mismo periodo destino.

## 5. Casos de uso

- **`OpenProgramAdmissionUseCase`** (nombre exacto ya documentado en `02-config-academica.md`, puerto in) — crea la configuración. Valida: `programId` existe, `periodId` existe, `targetGenerationId` existe, unicidad `(programId, periodId)`, `maxCandidates > 0`, `closesAt > opensAt`. `selectionStatus` se fija en `IN_REVIEW`, no es parámetro de entrada.
- **`UpdateProgramAdmissionConfigUseCase`** — actualiza los datos de catálogo (no toca `status` ni `selectionStatus`), mismo criterio que el resto del módulo. Revalida unicidad `(programId, periodId)` excluyendo el propio registro si `programId`/`periodId` cambian.
- **`ListProgramAdmissionConfigsUseCase`** — paginado, filtrable por `status`, `programId`.
- **`GetProgramAdmissionConfigUseCase`** — detalle por id.
- **`ChangeProgramAdmissionConfigStatusUseCase`** — toggle `OPEN`/`CLOSED` únicamente (NO `selectionStatus` — eso queda fuera de alcance, ver sección 3).

## 6. Endpoints y seguridad

`POST/GET /program-admission-configs`, `GET/PUT /program-admission-configs/{id}`, `PATCH /program-admission-configs/{id}/status`.

**Seguridad**: `ADMIN` + `SERVICIOS_ESCOLARES` — mismo par que el resto de `academic_config` (Divisiones, Programas, Planes, Periodos, Generaciones, Grupos). Confirmado con José (2026-07-28): a diferencia de `PaymentConcept` (que usó `PERSONAL_FINANZAS` porque el RF decía explícitamente "como finanzas"), acá no existe un rol dedicado a "admisión" en el catálogo de 11 roles, así que se sigue el patrón por defecto del módulo.

## 7. Excepciones nuevas

- `DuplicateProgramAdmissionConfigException` (409) — ya existe una configuración para ese `(programId, periodId)`.
- `InvalidProgramAdmissionConfigDataException` (400) — `maxCandidates <= 0` o `closesAt <= opensAt`.
- Reutilizar excepciones de referencia 400 ya existentes para `programId` (`ProgramNotFoundException`), `periodId` (`PeriodNotFoundException`), y crear una nueva si hace falta para `targetGenerationId` (revisar si ya existe una 400 "referencia no existe" para `Generation` desde el trabajo de `Group`, y reutilizarla si aplica).

## 8. Patrón a clonar

Mismo criterio hexagonal que el resto de `academic_config` — aggregate propio (como `AcademicPeriod`/`Generation`), sin entidades hijas.

## 9. Fuera de alcance (de esta fase)

- Panel de control de fichas disponibles/vendidas/pagadas (necesita `Candidate`/`AdmissionPayment`).
- Cambio de `selectionStatus` a `PUBLISHED`.
- Cualquier caso de uso del bounded context `admission` (`Candidate`, `AdmissionExamResult`, `InductionCourseResult`, `AdmissionPayment`, `OutreachChannel`).
- Frontend (fase posterior).

## 10. Execution Log (2026-07-28)

### Investigación previa (paso 1 del proceso)

- `GenerationReferenceNotFoundException` (400) YA existía — creada durante el trabajo de `Group` para validar `generationId`. Reutilizada tal cual para `targetGenerationId`, sin duplicado.
- `ProgramNotFoundException` (400, ya usada por `CreateAcademicPlanUseCaseImpl` para `programId`) y `PeriodNotFoundException` (400, ya usada por `CreateGenerationUseCaseImpl` para `startPeriodId`) confirmadas existentes en `academic_config/shared/exception/` y reutilizadas para `programId`/`periodId` respectivamente.
- Javadoc de `SecurityFilterConfig` documentaba hasta el octavo aggregate (`/payment-concepts`) de forma explícita, con `/payment-concepts/.../rates` (Fase 2, sin numeral propio) inmediatamente después. Se documentó el décimo aggregate como indica `02-config-academica.md`/este plan (línea 7: "sería su décimo aggregate"), continuando la secuencia narrativa sin renumerar los párrafos previos.
- `AcademicPeriod.validateDateRanges` confirmado como el precedente de estilo para la validación `closesAt > opensAt`: es un invariante puro de dos campos de la misma entidad, sin acceso a repositorio, así que vive en el constructor/`updateDetails` de `ProgramAdmissionConfig` — no en la capa de casos de uso (a diferencia de `PaymentConcept.maxPerStudent`/`maxPerPeriod`, que son checks independientes por campo y sí viven en el use case).

### Archivos creados

**Dominio:**
- `academic_config/domain/model/ProgramAdmissionConfigStatus.java`, `SelectionStatus.java` (enums nuevos)
- `academic_config/domain/model/ProgramAdmissionConfig.java` (aggregate root)
- `academic_config/domain/port/out/ProgramAdmissionConfigRepository.java`
- `academic_config/domain/port/in/{OpenProgramAdmissionUseCase, UpdateProgramAdmissionConfigUseCase, GetProgramAdmissionConfigUseCase, ListProgramAdmissionConfigsUseCase, ChangeProgramAdmissionConfigStatusUseCase}.java`
- `academic_config/domain/service/{OpenProgramAdmissionUseCaseImpl, UpdateProgramAdmissionConfigUseCaseImpl, GetProgramAdmissionConfigUseCaseImpl, ListProgramAdmissionConfigsUseCaseImpl, ChangeProgramAdmissionConfigStatusUseCaseImpl}.java`

**Excepciones:**
- `academic_config/shared/exception/{DuplicateProgramAdmissionConfigException (409), InvalidProgramAdmissionConfigDataException (400), ProgramAdmissionConfigNotFoundException (404)}.java`

**Persistencia:**
- `academic_config/infrastructure/persistence/{ProgramAdmissionConfigJpaRepository, ProgramAdmissionConfigRepositoryAdapter}.java` — orden por `opensAt` asc + `id` asc (no hay `code`/`name` para ordenar, igual que `PaymentConcept` con `name`+`id`).

**Web:**
- `academic_config/infrastructure/web/ProgramAdmissionConfigController.java`
- `academic_config/infrastructure/web/dto/{CreateProgramAdmissionConfigRequest, UpdateProgramAdmissionConfigRequest, ChangeProgramAdmissionConfigStatusRequest, ProgramAdmissionConfigResponse, ProgramAdmissionConfigListItemResponse, ProgramAdmissionConfigListResponse}.java`

**Tests (nuevos, 62 unit + 32 IT = 94):**
- `domain/model/ProgramAdmissionConfigTest.java` (18)
- `domain/service/{OpenProgramAdmissionUseCaseImplTest (9), UpdateProgramAdmissionConfigUseCaseImplTest (7), GetProgramAdmissionConfigUseCaseImplTest (2), ListProgramAdmissionConfigsUseCaseImplTest (5), ChangeProgramAdmissionConfigStatusUseCaseImplTest (4)}.java`
- `infrastructure/web/ProgramAdmissionConfigControllerTest.java` (17, unit)
- `infrastructure/persistence/ProgramAdmissionConfigRepositoryAdapterSearchIT.java` (7, IT)
- `infrastructure/web/ProgramAdmissionConfigControllerIT.java` (25, IT) — usa año 2030 para periodos, distinto de 2026 (`GenerationControllerIT`) y 2027 (`GroupControllerIT`), ya que todas las IT comparten la misma instancia H2 vía `@SpringBootTest`.

### Archivos modificados

- `academic_config/infrastructure/config/UseCaseConfig.java` — 5 beans nuevos.
- `academic_config/infrastructure/web/GlobalExceptionHandler.java` — 3 handlers nuevos (404/409/400).
- `identity/infrastructure/security/SecurityFilterConfig.java` — 4 matchers verb-split (`GET/POST/PUT/PATCH /program-admission-configs`) con `ADMIN`/`SERVICIOS_ESCOLARES` (NO `PERSONAL_FINANZAS` — no existe rol dedicado a "admisión" en el catálogo de 11 roles) + párrafo de Javadoc.

### Decisiones técnicas (con justificación)

1. **`maxCandidates > 0` y `closesAt > opensAt` viven en el dominio, no en el use case** — mismo criterio que `AcademicPeriod.validateDateRanges` (invariante puro de campos propios de la entidad), reutilizando `InvalidProgramAdmissionConfigDataException` desde el constructor y `updateDetails`.
2. **`selectionStatus` no tiene setter alguno en el modelo** — ni el constructor lo acepta como parámetro, ni existe método mutador; solo se fija internamente a `IN_REVIEW`. Ningún caso de uso de esta fase lo puede tocar por diseño (verificado con tests explícitos en las 4 capas: modelo, `Open`, `Update`, `ChangeStatus`).
3. **Orden de validación de FKs en `OpenProgramAdmissionUseCaseImpl`/`UpdateProgramAdmissionConfigUseCaseImpl`**: `programId` → `periodId` → `targetGenerationId`, mismo orden documentado en la tabla del modelo (sección 4).
4. **Sin filtro de texto libre (`search`) en `ListProgramAdmissionConfigsUseCase`** — a diferencia de `Generation`/`Group`, este aggregate no tiene `code`/`name`, así que solo filtra por `status`/`programId`, tal como pide el prompt de la tarea.

### Resultado de `./mvnw -o verify`

BUILD SUCCESS. Sin fallas ni errores.

| | Antes | Después | Delta |
|---|---|---|---|
| Unit tests (surefire) | 584 | 646 | +62 |
| IT tests (failsafe) | 297 | 329 | +32 |

### Desviaciones del plan

Ninguna — implementado tal cual documentado en las secciones 4-7 de este plan.
