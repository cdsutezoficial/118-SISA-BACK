# Plan: PaymentConcept (Fase 1 de 4 — Conceptos de Pago)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` (líneas 234-252, `PaymentConcept`) + `docs/requirements/07-PAGOS.md` (RF-PAG-001).

**Aclaración importante**: aunque el requerimiento vive en "Módulo 7 — Pagos y Finanzas", el diseño de dominio ubica `PaymentConcept` (y `PaymentRate`, Fase 2) dentro de **`academic_config`** — el mismo bounded context que ya construimos (Divisiones, Programas, Planes, Periodos, Generaciones, Grupos). El módulo Finance real (`Payment`, `PaymentBenefit`, `Debt` — el que necesita que exista `Student`) es un bounded context aparte, no tocado en este plan ni en los que siguen de este módulo.

Es la cuarta fase de trabajo confirmada con José sobre este tema (ver conversación 2026-07-28): Fase 1 (esta) → Fase 2 `PaymentRate` → Fase 3 wiring de `ConceptosList`/`ConceptosForm` → Fase 4 UI de tarifas.

## 2. Comparación docs vs. implementación actual

**Backend**: cero código — ni `PaymentConcept` ni `PaymentRate` existen en ningún paquete.

**Frontend** (fuera de alcance de este plan, se retoma en Fase 3): `ConceptosForm.tsx` es mock y no coincide con el modelo real — el campo "Tipo" tiene 2 valores inventados ("Recurrente"/"Una vez") en vez del enum documentado de 5 valores, y faltan 8 campos completos (`description`, `policies`, `isTuition`, `isStandalone`, `maxPerStudent`, `maxPerPeriod`, `requiresValidation`, `availableFrom`/`availableUntil`).

## 3. Alcance de esta fase

Solo `PaymentConcept` — el catálogo. Sin tarifas (`PaymentRate` es la Fase 2, con su propio diseño técnico para el historial append-only).

## 4. Modelo (campos exactos del doc, sin inventar ninguno adicional)

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| name | String | No nulo |
| description | Text | Nullable — rich text |
| policies | Text | Nullable — rich text |
| type | Enum | `ENROLLMENT, REINSCRIPTION, EXTRAORDINARY, DOCUMENT, OTHER` |
| isTuition | boolean | |
| isStandalone | boolean | |
| maxPerStudent | Integer | Nullable — null = ilimitado |
| maxPerPeriod | Integer | Nullable — null = ilimitado |
| requiresValidation | boolean | |
| availableFrom | LocalDate | Nullable |
| availableUntil | LocalDate | Nullable |
| status | Enum | ACTIVE, INACTIVE |

**Validaciones a nivel de uso de caso** (el doc no las detalla explícitamente para este aggregate — inferencias de sentido común, marcadas para que José las corrija si algo no aplica):
- `name` — **NO se exige unicidad**. El doc no lo documenta como restricción para `PaymentConcept` (a diferencia de `AcademicDivision`, que sí dice explícitamente "único") — mismo criterio ya usado en `SubjectClassification`, donde José confirmó que el nombre no es único.
- `maxPerStudent`/`maxPerPeriod`, si vienen, deben ser > 0.
- `availableFrom` <= `availableUntil` cuando ambos vienen — mismo tipo de validación de rango ya usada en otros catálogos de este módulo.

## 5. Casos de uso

`CreatePaymentConceptUseCase` (nombre exacto ya documentado en `02-config-academica.md` línea 299), `UpdatePaymentConceptUseCase`, `ListPaymentConceptsUseCase`, `GetPaymentConceptUseCase`, `ChangePaymentConceptStatusUseCase` — mismo shape de 5 casos de uso que `SubjectClassification` (aggregate simple, sin entidades hijas, toggle de estado idempotente, sin secuencia de varios pasos).

## 6. Endpoints y seguridad — nota importante, distinta al resto de `academic_config`

`POST/GET /payment-concepts`, `GET/PUT /payment-concepts/{id}`, `PATCH /payment-concepts/{id}/status`.

**Seguridad**: `ADMIN` + `PERSONAL_FINANZAS` — **NO** el par `ADMIN`/`SERVICIOS_ESCOLARES` que usa el resto de `academic_config`. Esto no es un descuido — RF-PAG-001 dice textualmente *"Como finanzas quiero gestionar..."*, y `PaymentBenefit.approvedBy` está tipado explícitamente como `FK → User (PERSONAL_FINANZAS)` en el propio diseño de dominio. Este catálogo lo administra Finanzas, no Servicios Escolares.

## 7. Patrón a clonar

`SubjectClassification` es el más parecido (aggregate simple, sin hijos, toggle de estado) — misma capas hexagonales: `domain/model` → `port/in` (5 casos de uso) → `service` (5 impls) → `port/out` (repositorio) → `infrastructure/persistence` (JPA) → `infrastructure/web` (controller + 6 DTOs) → tests.

## 8. Fuera de alcance (de esta fase)

- `PaymentRate` (Fase 2) — ninguna tarifa, ningún campo relacionado a montos.
- Frontend (Fase 3/4).
- El módulo Finance real (`Payment`, `PaymentBenefit`, `Debt`).

## 9. Execution Log (2026-07-28)

### Archivos creados

**Dominio** (`academic_config/domain/`):
- `model/PaymentConcept.java`, `model/PaymentConceptType.java`, `model/PaymentConceptStatus.java`
- `port/in/CreatePaymentConceptUseCase.java`, `UpdatePaymentConceptUseCase.java`, `ListPaymentConceptsUseCase.java`, `GetPaymentConceptUseCase.java`, `ChangePaymentConceptStatusUseCase.java`
- `port/out/PaymentConceptRepository.java`
- `service/CreatePaymentConceptUseCaseImpl.java`, `UpdatePaymentConceptUseCaseImpl.java`, `ListPaymentConceptsUseCaseImpl.java`, `GetPaymentConceptUseCaseImpl.java`, `ChangePaymentConceptStatusUseCaseImpl.java`

**Excepciones** (`academic_config/shared/exception/`):
- `PaymentConceptNotFoundException.java` (404)
- `InvalidPaymentConceptDataException.java` (400 — rango `maxPerStudent`/`maxPerPeriod`/`availableFrom`)

**Persistencia** (`academic_config/infrastructure/persistence/`):
- `PaymentConceptJpaRepository.java`, `PaymentConceptRepositoryAdapter.java`

**Web** (`academic_config/infrastructure/web/`):
- `PaymentConceptController.java`
- `dto/CreatePaymentConceptRequest.java`, `UpdatePaymentConceptRequest.java`, `ChangePaymentConceptStatusRequest.java`, `PaymentConceptResponse.java`, `PaymentConceptListItemResponse.java`, `PaymentConceptListResponse.java`

**Tests** (9 archivos): `PaymentConceptTest` (modelo), 5 tests de casos de uso (`Create`/`Update`/`List`/`Get`/`ChangeStatus`), `PaymentConceptRepositoryAdapterSearchIT`, `PaymentConceptControllerTest`, `PaymentConceptControllerIT`.

### Archivos modificados

- `UseCaseConfig.java` (academic_config) — 5 imports de port/in, 5 imports de service, `PaymentConceptRepository` import, 5 beans nuevos.
- `GlobalExceptionHandler.java` (academic_config) — 2 handlers nuevos (`PaymentConceptNotFoundException` → 404, `InvalidPaymentConceptDataException` → 400).
- `SecurityFilterConfig.java` (identity) — nuevo párrafo Javadoc documentando el aggregate #8 y por qué usa `PERSONAL_FINANZAS` en vez de `SERVICIOS_ESCOLARES`; 4 matchers verb-split (GET/POST/PUT/PATCH) para `/payment-concepts` con `ADMIN`/`PERSONAL_FINANZAS`, colocados después de `/persons`.

### Decisiones técnicas (con justificación)

1. **Sin `code`, sin unicidad de `name` en absoluto**: a diferencia de `SubjectClassification` (que sí valida unicidad de `code`), `PaymentConcept` no tiene ningún campo de clave de negocio único — el plan (sección 4) no documenta ninguna restricción de unicidad. `Create`/`Update` no hacen ningún lookup previo por nombre.

2. **Validación de rango compartida vía método `static`**: a diferencia de `CreateAcademicPlanUseCaseImpl`/`UpdateAcademicPlanUseCaseImpl` (que duplican el chequeo de `minPassingGrade` en cada clase), aquí `CreatePaymentConceptUseCaseImpl.validate(...)` es `static` y reutilizado por `UpdatePaymentConceptUseCaseImpl` — evita duplicar la misma lógica de 3 reglas (maxPerStudent > 0, maxPerPeriod > 0, availableFrom <= availableUntil).

3. **`description`/`policies` como columnas `TEXT`**: el plan tipa estos campos explícitamente como "Text — rich text", así que se usó `@Column(columnDefinition = "TEXT")` en vez del `@Column String` simple usado en otros campos de texto del módulo (p. ej. `PlanLevel#description`), para no truncar a `VARCHAR(255)`.

4. **Sort de paginación por `name` + `id` (no `name` + `code`)**: como no existe `code`, se usa `id` (siempre único) como desempate para paginación determinística cuando hay nombres duplicados — mismo problema que `SubjectClassification` resolvió con `code`, pero aquí no hay campo de negocio disponible para eso.

5. **Sin anotaciones `@Positive`/`@DecimalMin` en los DTOs para los rangos**: siguiendo el patrón explícito que pidió José (mirroring `AcademicPlan`'s `minPassingGrade`, NO `maxExtraordinaryExamsPerPeriod`'s `@Min`), las validaciones de `maxPerStudent`/`maxPerPeriod`/`availableFrom` se resuelven en la capa de casos de uso vía `InvalidPaymentConceptDataException`, no vía bean validation.

### Resultado de tests

- Antes: 507 unit / 243 IT.
- Después: 555 unit / 280 IT (+48 unit, +37 IT).
- `./mvnw verify` (JDK 21 Corretto): **BUILD SUCCESS**, 0 failures, 0 errors en ambos módulos (unit + failsafe IT).

### Desviaciones del plan

Ninguna — el modelo, los 5 casos de uso, los endpoints y el par de roles `ADMIN`/`PERSONAL_FINANZAS` se implementaron exactamente como se documentó en las secciones 4-7.
