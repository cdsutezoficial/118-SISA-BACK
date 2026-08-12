# Plan: PaymentRate (Fase 2 de 4 — Conceptos de Pago)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` (líneas 254-267, `PaymentRate`) + `docs/requirements/07-PAGOS.md` (RF-PAG-002). Depende de `PaymentConcept` (Fase 1, ya cerrada — `docs/plans/2026-07-28-payment-concept.md`).

`PaymentRate` es la tabla de tarifas de un `PaymentConcept`: una fila por combinación `conceptId` + `programId` (nullable) + `level` (nullable) que fija un monto, con historial completo (nunca se edita ni se borra una fila — se "cierra" y se agrega una nueva).

## 2. Diseño técnico resuelto (confirmado con José, 2026-07-28)

**La pregunta abierta**: el doc dice que `validTo` "se cierra automáticamente al crear la siguiente tarifa para la misma combinación `conceptId+programId+level`" — pero no aclara qué pasa con `periodId`. Se resolvió así:

- **Tarifas continuas** (`periodId = null`): forman una cadena de vigencia por fecha. Crear una tarifa nueva para la misma combinación exacta (`conceptId`, `programId`, `level`, con `periodId = null` en ambas) **cierra automáticamente** la anterior — la fila vieja pasa a `validTo = nuevaValidFrom.minusDays(1)` (rangos sin solape, cada día pertenece a una sola tarifa vigente).
- **Tarifas por periodo puntual** (`periodId` específico): son **independientes** entre sí y de la cadena continua — crear una NO cierra ninguna otra fila. Cada combinación exacta `conceptId`+`programId`+`level`+`periodId` es su propio casillero — **unicidad**: no puede haber dos tarifas activas para la misma combinación exacta con el mismo `periodId` (rechazar con 409 si ya existe una).
- **Fuera de alcance de esta fase**: la lógica de "qué tarifa aplica a un pago concreto" (prioridad `periodId` exacto → fallback continuo por fecha) — es responsabilidad de `GeneratePaymentReferenceUseCase`, que vive en el futuro módulo Finance (no existe todavía, no tiene consumidor real hoy). Esta fase solo construye el almacenamiento y el historial, no el motor de resolución de precios.

## 3. Modelo

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| conceptId | UUID | FK → `PaymentConcept`, obligatorio, validado (debe existir) |
| programId | UUID | Nullable — FK → `AcademicProgram`, validado si viene (debe existir) |
| level | AcademicLevel (shared kernel) | Nullable |
| amount | BigDecimal | > 0 |
| periodId | UUID | Nullable — FK → `AcademicPeriod`, validado si viene (debe existir) |
| validFrom | LocalDate | Obligatorio |
| validTo | LocalDate | Nullable — gestionado por el propio caso de uso, nunca lo envía el cliente |

## 4. Casos de uso

- **`SetPaymentRateUseCase`** — único caso de uso de escritura (no hay Update ni Delete — el doc solo documenta este, coherente con "nunca se edita ni se borra"):
  1. Valida `conceptId` (existe), `programId` si viene (existe), `periodId` si viene (existe).
  2. Valida `amount > 0`.
  3. Si `periodId` es null: busca la fila activa (`validTo IS NULL`) con la misma combinación exacta `conceptId`+`programId`+`level` (tratando null como valor propio de la combinación, no como comodín) → si existe, la cierra (`validTo = nuevaValidFrom.minusDays(1)`) antes de insertar la nueva.
  4. Si `periodId` no es null: valida que NO exista ya una fila con la misma combinación exacta `conceptId`+`programId`+`level`+`periodId` → si existe, `DuplicatePaymentRateException` (409). No cierra ninguna otra fila.
- **`ListPaymentRatesUseCase`** — historial completo de tarifas de un `conceptId` (para la Fase 4, UI de historial). Sin paginación — el volumen esperado por concepto es bajo (a diferencia de los listados paginados del resto del módulo, que consultan colecciones potencialmente grandes); devuelve un arreglo plano, ordenado por `programId`, `level`, `periodId`, `validFrom` descendente.

## 5. Persistencia — repositorio propio, no encapsulado en `PaymentConcept`

A diferencia de `PlanLevel`/`Subject` (encapsulados dentro de `AcademicPlan`, sin repo propio, colección acotada por `totalLevels`), `PaymentRate` tiene **su propio repositorio JPA**. Razón: el historial de tarifas crece indefinidamente con el tiempo (cada cambio de precio agrega una fila que nunca se borra) — cargar toda la colección en memoria vía el aggregate root cada vez que se necesita consultar sería ineficiente y no escala como sí escala `PlanLevel` (acotado a `totalLevels`, siempre chico).

## 6. Endpoints

Anidados bajo el concepto (recurso siempre "de" un `PaymentConcept`):

| Verbo | Endpoint | Body/Query |
|---|---|---|
| POST | `/payment-concepts/{conceptId}/rates` | `{ programId?, level?, amount, periodId?, validFrom }` |
| GET | `/payment-concepts/{conceptId}/rates` | Historial completo, sin paginación |

**Seguridad**: mismo par que `PaymentConcept` (Fase 1) — `ADMIN` + `PERSONAL_FINANZAS`.

## 7. Excepciones nuevas

- `DuplicatePaymentRateException` (409) — ya existe una tarifa con `periodId` específico para la misma combinación exacta.
- Reutilizar `PaymentConceptNotFoundException`/`AcademicProgramNotFoundException`/`AcademicPeriodNotFoundException` (o el equivalente 400 "referencia no existe" ya usado en Generation/Group) para las validaciones de FK — no crear excepciones nuevas para esto, mismo criterio de reutilización ya aplicado en `Group`.

## 8. Fuera de alcance

- Cualquier caso de uso de "resolver la tarifa aplicable a un pago" — pertenece al futuro módulo Finance.
- Update/Delete de tarifas — no existen por diseño (historial inmutable).
- Frontend (Fase 3/4).

## 9. Execution Log (2026-07-28)

### Archivos creados

**Dominio** (`academic_config/domain/`):
- `model/PaymentRate.java`
- `port/in/SetPaymentRateUseCase.java`, `ListPaymentRatesUseCase.java`
- `port/out/PaymentRateRepository.java`
- `service/SetPaymentRateUseCaseImpl.java`, `ListPaymentRatesUseCaseImpl.java`

**Excepciones** (`academic_config/shared/exception/`):
- `DuplicatePaymentRateException.java` (409)
- `PaymentConceptReferenceNotFoundException.java` (400, nueva)
- `InvalidPaymentRateDataException.java` (400, nueva)

**Persistencia** (`academic_config/infrastructure/persistence/`):
- `PaymentRateJpaRepository.java`, `PaymentRateRepositoryAdapter.java`

**Web** (`academic_config/infrastructure/web/`):
- `PaymentRateController.java`
- `dto/CreatePaymentRateRequest.java`, `PaymentRateResponse.java`, `PaymentRateListResponse.java`

**Tests** (6 archivos): `PaymentRateTest` (modelo), `SetPaymentRateUseCaseImplTest` (14 casos, incluye todos los regression guards pedidos), `ListPaymentRatesUseCaseImplTest`, `PaymentRateRepositoryAdapterIT` (8 casos, cubre la lógica null-safe de combinación directamente contra H2), `PaymentRateControllerTest`, `PaymentRateControllerIT` (9 casos, incluye el guard de "cerrar la tarifa anterior" end-to-end).

### Archivos modificados

- `UseCaseConfig.java` (academic_config) — imports + 2 beans nuevos (`setPaymentRateUseCase`, `listPaymentRatesUseCase`).
- `GlobalExceptionHandler.java` (academic_config) — 3 handlers nuevos (`PaymentConceptReferenceNotFoundException` → 400, `InvalidPaymentRateDataException` → 400, `DuplicatePaymentRateException` → 409). `ProgramNotFoundException`/`PeriodNotFoundException` ya tenían handler (reutilizados, sin cambios).
- `SecurityFilterConfig.java` (identity) — nuevo párrafo Javadoc + 1 matcher nuevo: `POST "/payment-concepts/*/rates"` con `ADMIN`/`PERSONAL_FINANZAS`.

### Decisiones técnicas (con justificación)

1. **`conceptId` — nueva excepción `PaymentConceptReferenceNotFoundException` (400), NO reutilizar `PaymentConceptNotFoundException`**: el plan (sección 7) sugería reutilizar `PaymentConceptNotFoundException` directamente, pero esa excepción es 404 y está documentada como "el recurso PaymentConcept en sí no se encontró" (Get-by-id/Update). Investigación del código existente (`AddPlanLevelUseCaseImpl` vs. `CreateGenerationUseCaseImpl`/`CreateGroupUseCaseImpl`) mostró DOS convenciones distintas ya establecidas:
   - Hijo-de-la-misma-agregación (`PlanLevel` dentro de `AcademicPlan`): reutiliza el 404 del padre directamente (`AcademicPlanNotFoundException`).
   - Agregación separada referenciando a otra por FK (`Generation`→`AcademicPlan`, `Group`→`Generation`): SIEMPRE una excepción 400 propia, distinta del 404 del recurso referenciado (`PlanNotFoundException`≠`AcademicPlanNotFoundException`; `GenerationReferenceNotFoundException`≠`GenerationNotFoundException`).
   El plan (sección 5) es explícito: `PaymentRate` es arquitectónicamente una agregación separada de `PaymentConcept` (repositorio propio, no encapsulada). Por lo tanto sigue la SEGUNDA convención, no la primera — se creó `PaymentConceptReferenceNotFoundException` (400) siguiendo el patrón exacto de `GenerationReferenceNotFoundException`.
2. **`programId` → reutiliza `ProgramNotFoundException`** (400, ya existente, usado por `CreateAcademicPlanUseCaseImpl`) — confirmado que ya es exactamente el "400 referencia no existe" para `AcademicProgram` que pedía investigar el encargo.
3. **`periodId` → reutiliza `PeriodNotFoundException`** (400, ya existente, usado por `CreateGenerationUseCaseImpl`) — según instrucción explícita del encargo.
4. **`amount > 0` — nueva excepción `InvalidPaymentRateDataException` (400)**: el plan no la mencionaba explícitamente en la sección 7, pero sí lista la validación como paso propio del caso de uso (sección 4, paso 2). Siguiendo el mismo patrón que `InvalidPaymentConceptDataException`/`InvalidPlanDataException` (cada aggregate tiene su propia excepción de "mi propio campo es inválido", no comparten una genérica entre aggregates), se creó una excepción específica de `PaymentRate` en vez de reutilizar la de `PaymentConcept`.
5. **Matchers de seguridad — solo 1 nuevo, no 2**: se investigó si el matcher existente de `GET /payment-concepts` (`"/payment-concepts", "/payment-concepts/**"`, con wildcard) ya cubría `GET /payment-concepts/{conceptId}/rates` — SÍ, lo cubre. El matcher de `POST /payment-concepts`, en cambio, es un patrón EXACTO sin wildcard (a diferencia de GET/PUT/PATCH en el mismo endpoint), así que NO cubre la ruta anidada — se agregó `POST "/payment-concepts/*/rates"` como único matcher nuevo.
6. **`ListPaymentRatesUseCase` no valida existencia de `conceptId`**: un `conceptId` inexistente simplemente devuelve una lista vacía, igual que cualquier otro listado filtrado del módulo (p. ej. `GET /generations?programId=` con un id inexistente devuelve página vacía, no 404) — decisión no cubierta explícitamente por el plan, aplicada por consistencia con el resto del módulo.
7. **`PaymentRateListResponse` como wrapper `{items: [...]}`**, no un array JSON crudo — interpretación de "flat array wrapper" del encargo: "wrapper" implica un record que envuelve el arreglo (igual que `PaymentConceptListResponse` pero sin metadata de paginación), no la respuesta HTTP siendo un arreglo top-level.
8. **Consulta null-safe de combinación exacta**: tanto `findActiveContinuousRate` como `existsByExactCombination` usan el patrón JPQL `(:param IS NULL AND columna IS NULL) OR columna = :param` para `programId`/`level` — JPQL `=` nunca hace match con `NULL`, así que sin este patrón un `programId`/`level` nulo se comportaría como comodín en vez de como su propio valor en la combinación (sección 2/4 del plan, explícitamente: "tratando null como valor propio de la combinación, no como comodín").

### Resultado de tests

- Antes: 555 unit / 280 IT.
- Después: 584 unit / 297 IT (+29 unit, +17 IT).
- `./mvnw verify` (JDK 21 Corretto): **BUILD SUCCESS**, 0 failures, 0 errors en ambos módulos (unit + failsafe IT).

### Desviaciones del plan

- Sección 7 mencionaba reutilizar `PaymentConceptNotFoundException`/`AcademicProgramNotFoundException`/`AcademicPeriodNotFoundException` "o el equivalente 400" — en la práctica el código ya usaba nombres distintos (`ProgramNotFoundException`, `PeriodNotFoundException`) y no existía ningún 400 para `PaymentConcept`, así que se investigó y resolvió según la convención real del código (ver decisión 1), no según el texto literal del plan.
- Se crearon 2 excepciones nuevas no listadas explícitamente en la sección 7 (`PaymentConceptReferenceNotFoundException`, `InvalidPaymentRateDataException`) — ambas justificadas arriba, siguiendo convenciones ya establecidas en el módulo (`Group`/`Generation` para la primera, `PaymentConcept`/`AcademicPlan` para la segunda).
- El resto del modelo, casos de uso, endpoints y seguridad se implementaron exactamente como se documentó en las secciones 2-6.
