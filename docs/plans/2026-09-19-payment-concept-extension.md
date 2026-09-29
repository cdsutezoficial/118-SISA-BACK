# Plan: Extensión de `PaymentConcept` (campos nuevos del spec de Pagos)

> Recurso: `PaymentConcept` — catálogo de conceptos de pago.
> Bounded context: `academic_config`. Endpoint raíz: `/payment-concepts`.
> Fase previa: `docs/plans/2026-07-28-payment-concept.md` (Fase 1, 5 use cases).

## 1. Contexto (docs-first)

- **Código fuente:** `118-SISA-BACK`, paquete
  `mx.edu.utez.sisa.academic_config`.
- **Frontend consumidor:** `118-SISA-FRONT/src/app/modules/config-academica/pages/ConceptosForm.tsx`.
  Hoy captura en estado local 9 campos del spec que **no se envían** al API
  (comentario "FRONTEND-FIRST" en el propio archivo):
  `areaId, costo, costoExterno, esExterno, esAcumulable, esMulticoncepto,
  vinculados, carreras, limiteCuotas`.
- **Proveniencia documental:** `02-config-academica.md` (líneas 234-252)
  define `PaymentConcept` con 11 campos y **no** incluye ninguno de los 9
  nuevos. Igual que `PaymentArea`, nacen del **spec de pantalla del
  usuario**. Se persisten por decisión explícita del PO ("Persistir todos los
  campos nuevos").
- **Decisión de producto:** `cost`/`costExternal` conviven con `PaymentRate`
  (historial de tarifas por carrera+nivel+periodo). `cost` es el **monto base
  informativo del concepto**; el precio efectivo por combinación sigue
  viviendo en `PaymentRate` (sección Tarifas del form).

## 2. Alcance

Extender el agregado existente (sin nuevos endpoints ni use cases):

1. `PaymentConcept` + 9 campos nuevos (uno de ellos `@ElementCollection` ×2).
2. `CreatePaymentConceptCommand` / `UpdatePaymentConceptCommand` /
   `PaymentConceptResult`.
3. Validaciones nuevas en `CreatePaymentConceptUseCaseImpl`
   (compartidas con `Update`).
4. DTOs web `CreatePaymentConceptRequest` / `UpdatePaymentConceptRequest` /
   `PaymentConceptResponse`.
5. `PaymentConceptController` (mapeo).
6. Wiring en `UseCaseConfig` (2 beans ganan dependencias).
7. Tests: actualización mínima + cobertura de las validaciones nuevas.
8. Front: `ConceptosForm.tsx` (payload + carga + tipos).

## 3. Modelo (campos nuevos)

Se **anexan** al final del agregado para no reordenar lo existente. El
constructor y `updateDetails` de 11 argumentos se **conservan** como
sobrecargas de conveniencia (delegan a la versión extendida) para no romper
los ~11 archivos de test existentes.

| Atributo | Tipo | Columna / JPA | Restricciones |
|---|---|---|---|
| `areaId` | `UUID` | `area_id`, nullable | Si viene, debe existir en `PaymentArea` |
| `cost` | `BigDecimal` | `precision=12, scale=2`, nullable | Si viene, `>= 0` |
| `isExternal` | `boolean` | `nullable=false` | — |
| `costExternal` | `BigDecimal` | `precision=12, scale=2`, nullable | Obligatorio `>= 0` si `isExternal` |
| `isAccumulable` | `boolean` | `nullable=false` | — |
| `isMulticoncept` | `boolean` | `nullable=false` | — |
| `quotaLimit` | `Integer` | nullable | Si viene, `> 0` |
| `linkedConceptIds` | `List<UUID>` | `@ElementCollection` → tabla `payment_concept_linked_concept` (`concept_id`, `linked_concept_id`) | Cada id debe existir; sin duplicados; no puede ser el propio id (Update) |
| `programIds` | `List<UUID>` | `@ElementCollection` → tabla `payment_concept_program` (`concept_id`, `program_id`) | Cada id debe existir en `AcademicProgram`; sin duplicados |

- Colecciones inicializadas a `new ArrayList<>()`; en `updateDetails` se
  mutan con `clear()`/`addAll()` (nunca se reasigna la referencia, requisito
  de Hibernate).
- `toResult` copia a `List.copyOf(...)` dentro de la transacción.

### Validaciones nuevas (400 `InvalidPaymentConceptDataException`)

- `cost < 0`.
- `isExternal && costExternal == null` (y `costExternal < 0`).
- `quotaLimit <= 0`.
- Duplicados en `programIds` / `linkedConceptIds`.
- Auto-referencia en `linkedConceptIds` (Update).

### Validaciones de referencia (400 `PaymentConceptReferenceNotFoundException`)

- `areaId` → `PaymentAreaRepository#findById` (no se exige `ACTIVE`: si el
  área se desactiva, un concepto existente debe seguir siendo editable).
- `programIds` → `AcademicProgramRepository#findById`.
- `linkedConceptIds` → `PaymentConceptRepository#findById`.

## 4. Dependencias nuevas de servicios

`CreatePaymentConceptUseCaseImpl` y `UpdatePaymentConceptUseCaseImpl` pasan de
1 a 3 dependencias (mismo precedente que `CreateAcademicProgramUseCaseImpl`
valida `divisionId` contra `AcademicDivision`):

```
PaymentConceptRepository, PaymentAreaRepository, AcademicProgramRepository
```

`GetPaymentConceptUseCaseImpl` se anota `@Transactional(readOnly = true)` para
poder materializar las colecciones al construir el `PaymentConceptResult`.

## 5. Contrato de API (campos anexados al final)

```jsonc
{
  "name": "…", "description": "…", "policies": "…", "type": "OTHER",
  "isTuition": false, "isStandalone": false,
  "maxPerStudent": null, "maxPerPeriod": null, "requiresValidation": false,
  "availableFrom": null, "availableUntil": null,
  "areaId": "uuid|null",
  "cost": "1234.50|null",
  "isExternal": false, "costExternal": null,
  "isAccumulable": false, "isMulticoncept": false,
  "quotaLimit": null,
  "linkedConceptIds": ["uuid"],
  "programIds": ["uuid"]
}
```

`PaymentConceptResponse` refleja los mismos campos.

## 6. Mapeo front (estado local → payload)

| Form (`ConceptosForm.tsx`) | API |
|---|---|
| `areaId` | `areaId` |
| `costo` | `cost: Number(costo)` |
| `esExterno` | `isExternal` |
| `costoExterno` | `costExternal: esExterno ? Number(costoExterno) : null` |
| `esAcumulable` | `isAccumulable` |
| `esMulticoncepto` | `isMulticoncept` |
| `limiteCuotasOn/limiteCuotas` | `quotaLimit` (`null` si off) |
| `esVinculados/vinculados` | `linkedConceptIds` (`[]` si off) |
| `aplicaCarrera/carreras` | `programIds` (`[]` si off) |

Carga (`GET`): reconstruye los switches y arrays desde el response.

## 7. Fuera de alcance

- Cambiar `PaymentRate` ni la sección Tarifas.
- Migraciones versionadas (el repo no usa Flyway; Hibernate `ddl-auto`).
- Reordenar campos existentes del agregado.

## 8. Riesgos

- **Auto-referencia en Create**: el id aún no existe; solo se valida en Update.
- **Colecciones lazy**: mitigado con `@Transactional(readOnly=true)` en Get y
  copiado dentro de la transacción en Create/Update.
- **Datos existentes**: las columnas nuevas son nullables o con default, así
  que las filas previas siguen siendo válidas.

## 9. Execution Log (2026-09-19)

### Archivos modificados

**Dominio:**
- `domain/model/PaymentConcept.java` — 9 campos, constructor extendido (20 args)
  + sobrecarga de 11, `updateDetails` extendido (20 args) + sobrecarga de 11
  (que preserva los campos nuevos), getters; `@ElementCollection` para
  `linkedConceptIds` (`payment_concept_linked_concept`) y `programIds`
  (`payment_concept_program`).
- `domain/port/in/CreatePaymentConceptUseCase.java` — `CreatePaymentConceptCommand`
  y `PaymentConceptResult` extendidos + constructor de conveniencia.
- `domain/port/in/UpdatePaymentConceptUseCase.java` — comando extendido +
  constructor de conveniencia.
- `domain/service/CreatePaymentConceptUseCaseImpl.java` — 2 dependencias nuevas
  (`PaymentAreaRepository`, `AcademicProgramRepository`), validaciones de
  extensión (`validate` + `validateReferences`/`validateIds`), `toResult`.
- `domain/service/UpdatePaymentConceptUseCaseImpl.java` — 2 dependencias
  nuevas, mismas validaciones con `selfId`, `updateDetails` extendido.
- `domain/service/GetPaymentConceptUseCaseImpl.java` — `@Transactional(readOnly=true)`.

**Web:**
- `infrastructure/web/dto/{Create,Update}PaymentConceptRequest.java`,
  `PaymentConceptResponse.java` — campos anexados.
- `infrastructure/web/PaymentConceptController.java` — mapeo de comandos/respuesta.

**Config:**
- `infrastructure/config/UseCaseConfig.java` — beans create/update con las
  dependencias nuevas.

**Tests (actualizados):**
- `CreatePaymentConceptUseCaseImplTest` — mocks nuevos + 8 pruebas de extensión
  (persistencia, costo negativo, externo sin costo, costo externo negativo,
  `quotaLimit`, área/programa/vinculado inexistente, duplicados).
- `UpdatePaymentConceptUseCaseImplTest` — mocks nuevos + 1 prueba
  (auto-vinculación).

**Front:**
- `118-SISA-FRONT/src/app/modules/config-academica/pages/ConceptosForm.tsx` —
  tipos response/payload, carga y `handleSubmit` mapean los 9 campos; se
  eliminaron los comentarios "FRONTEND-FIRST / NO se envían".

### Decisiones técnicas

1. **Constructores de conveniencia**: el agregado y los `record` de
   comando/resultado conservan su aridad original como sobrecarga que delega
   con null/false/`List.of()`. Así los ~11 archivos de test preexistentes
   siguen compilando y las filas/consumidores viejos no se rompen (plan §3).
   El front usa siempre la versión extendida.
2. **Campos anexados al final** del JSON y del constructor para no reordenar
   el contrato existente.
3. **Validación de referencias (400)**: `areaId`, `programIds` y
   `linkedConceptIds` se validan contra `PaymentAreaRepository`,
   `AcademicProgramRepository` y `PaymentConceptRepository`; `areaId` no exige
   `ACTIVE` para no bloquear conceptos si un área se desactiva después.
4. **Auto-vinculación** solo se valida en Update (`selfId` null en Create).
5. **`@ElementCollection`**: tablas join generadas por Hibernate
   (`ddl-auto`); `Get` es `@Transactional(readOnly=true)` para materializar las
   colecciones en el `PaymentConceptResult`.

### Resultado de tests

- Backend: `.\mvnw.cmd "-Dtest=*PaymentConcept*,*PaymentRate*" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  → **141 tests, 0 failures, 0 errors** (incluye
  `PaymentConceptControllerIT`/`PaymentRateControllerIT` sobre MySQL,
  `PaymentConceptRepositoryAdapterSearchIT`, `PaymentConceptControllerTest`,
  `PaymentConceptTest` y las 5 suites de casos de uso).
- `-DskipTests test-compile` global: OK (ningún test de otro módulo se rompió
  por el cambio de aridad).
- Front: `npm run typecheck` OK y `npm run build` OK.

### Desviaciones

- Se añadió `validateIds` (null/duplicados) además de lo planeado en §3; el
  primer intento usó `List.contains(null)`, que lanza `NullPointerException`
  sobre listas inmutables — corregido con `HashSet`.
- Sin nuevos endpoints, DTOs de lista ni excepciones: los tipos existentes
  (`InvalidPaymentConceptDataException`, `PaymentConceptReferenceNotFoundException`)
  se reutilizan.
