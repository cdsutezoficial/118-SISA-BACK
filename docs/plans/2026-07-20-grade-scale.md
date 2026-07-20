# Plan: GradeScale / GradeScaleEntry (dentro de AcademicPlan)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` (líneas 121-147) + `docs/requirements/01-PROGRAMACION.md` (RF-PROG-001c, RN-PROG-010/011/012).

`GradeScale` y `GradeScaleEntry` son entidades hijas de `AcademicPlan` (NO aggregates propios, a diferencia de `SubjectClassification`) — resuelven RF-PROG-001c: "cada plan de estudios puede tener una escala de calificaciones distinta, tanto en la calificación mínima aprobatoria como en la equivalencia a letras".

| Entidad | Atributo | Tipo | Restricciones |
|---|---|---|---|
| **GradeScale** | id | UUID | PK |
| | planId | UUID | FK → `AcademicPlan` |
| | classificationId | UUID | FK → `SubjectClassification` (ya existe, cerrado 2026-07-20) |
| | numericMin / numericMax | Decimal | rango válido de la escala |
| | *(única)* | — | `(planId, classificationId)` |
| **GradeScaleEntry** | id | UUID | PK |
| | scaleId | UUID | FK → `GradeScale` |
| | fromValue / toValue | Decimal | rango inclusive |
| | letter | String | clave corta, ej. "CO" |
| | description | String | nombre largo, ej. "Competente" |
| | passed | Boolean | si el rango aprueba |

Importante: `minPassingGrade` (mínima aprobatoria) **NO** vive acá — ya está en `AcademicPlan` (implementado en Study Plans). `GradeScale`/`GradeScaleEntry` resuelven solo la nomenclatura de letras, no la mínima aprobatoria.

El dominio ya nombra el puerto: `SetGradeScaleUseCase` — "Define la escala de calificaciones para un plan + clasificación" (línea 298) — sugiere una operación de **reemplazo completo** (escala + todos sus rangos en un solo submit), no CRUD granular por rango. Coincide con el prompt de Figma (Pantalla 18: un solo formulario con la clasificación, min/max, y una tabla de rangos que se guarda toda junta).

**Consumidor real ya documentado**: `05-calificaciones.md` línea 171 — `AcademicConfigQueryPort.getGradeScale(planId)`, usado para calcular `finalLetter`/`passed` al cerrar actas. Fuera de alcance de este plan (ese puerto lo implementa el módulo de Calificaciones cuando se construya).

## 2. Patrón a clonar — el de `PlanLevel`/`Subject`, NO el de `SubjectClassification`

`GradeScale` está "dentro de `AcademicPlan`" en el dominio, igual que `PlanLevel`/`Subject` — confirmado en el código existente (`PlanLevel.java`, comentario de clase): **composición JPA, sin repositorio ni controller propio** — toda mutación pasa por los métodos de `AcademicPlan`, constructor/mutators package-private (solo `AcademicPlan`, mismo paquete, puede crear/cambiar un `PlanLevel`).

Endpoints anidados bajo `/plans/{id}/...` (mismo estilo que niveles/materias en `AcademicPlanController.java` líneas 164-211):

| Verbo | Endpoint | Equivale a |
|---|---|---|
| POST | `/plans/{id}/grade-scales` | `addLevel` — crea la escala + todos sus rangos en un solo request |
| PUT | `/plans/{id}/grade-scales/{scaleId}` | `updateLevel` — reemplaza clasificación/min/max/rangos completos |
| DELETE | `/plans/{id}/grade-scales/{scaleId}` | `removeLevel` |

Sin GET independiente — igual que niveles/materias, las escalas viajan embebidas en `AcademicPlanResponse` (agregar campo `gradeScales: List<GradeScaleResponse>`, cada uno con `entries: List<GradeScaleEntryResponse>`), no en un endpoint propio.

Seguridad: `SecurityFilterConfig.java` ya cubre `/plans`, `/plans/**` con un patrón único por verbo (líneas 105-112, ADMIN + SERVICIOS_ESCOLARES) — **no hace falta agregar matchers nuevos**, a diferencia de lo que pasó fase a fase con `SubjectClassification`.

## 3. Validaciones de negocio a implementar

1. **Unicidad `(planId, classificationId)`** — un plan no puede tener dos escalas para la misma clasificación (documentado, línea 133). Nueva excepción `DuplicateGradeScaleException` (409), mismo estilo que `DuplicatePlanLevelNumberException`.
2. **`classificationId` debe existir** — validar contra `SubjectClassificationRepository` (llamada directa al repo existente, mismo patrón cross-aggregate-mismo-módulo que `AcademicProgram`→`AcademicDivision`, NO un puerto nuevo). Nueva excepción `ClassificationNotFoundException` — **ya existe**, creada en la Fase 3 de `SubjectClassification` (409/400, reutilizable tal cual).
3. **`numericMin < numericMax`** — validación de rango básica.
4. **Rangos de `GradeScaleEntry` dentro de `[numericMin, numericMax]`** — cada `fromValue`/`toValue` debe caer dentro del rango de la escala.

## 4. Decisión de PO (confirmada 2026-07-20)

El dominio **no especificaba** si los rangos de `GradeScaleEntry` dentro de una escala debían cubrir `[numericMin, numericMax]` completo sin huecos y sin solaparse entre sí. **Decisión: SÍ, validar ambas cosas al guardar la escala** (cobertura completa + sin solapes) — evita que, al calificar, un valor numérico se quede sin letra asignada o caiga en dos tramos a la vez. Nueva excepción `InvalidGradeScaleEntriesException` (400) para ambos casos (hueco o solape).

## 5. Entrega

Al ser una sola operación de "set completo" (no 5 fases como `SubjectClassification`), este plan se entrega en un solo tramo: modelo (`GradeScale`+`GradeScaleEntry`), mutators en `AcademicPlan` (`setGradeScale`/`removeGradeScale`), persistencia (cascada JPA vía `AcademicPlan`, sin repos propios), web layer (3 endpoints + campo nuevo en `AcademicPlanResponse`), TDD, IT con JWT real reutilizando `AcademicPlanControllerIT`.

## Execution Log

**Estado: implementado y verificado (2026-07-20).**

### Qué se construyó

Se clonó exactamente el patrón `PlanLevel`/`Subject` (composición JPA dentro de `AcademicPlan`, sin repositorio ni controller propios, constructores/mutators package-private):

- **Dominio** (`domain/model/`): `GradeScale.java` (hija de `AcademicPlan`, dueña de `entries`), `GradeScaleEntry.java` (hija de `GradeScale`), `GradeScaleEntryData.java` (record de transferencia use-case → dominio, NO depende de `port.in`, mirrors cómo `addSubject` recibe primitivos planos). `AcademicPlan.java` ganó `setGradeScale`/`updateGradeScale`/`removeGradeScale` + `getGradeScales()`.
- **Puertos** (`domain/port/in/`): `SetGradeScaleUseCase`, `UpdateGradeScaleUseCase`, `RemoveGradeScaleUseCase`. `CreateAcademicPlanUseCase` ganó `GradeScaleResult`/`GradeScaleEntryResult` (mismo patrón de reuso que `PlanLevelResult`/`SubjectResult`) y `AcademicPlanResult.gradeScales`.
- **Casos de uso** (`domain/service/`): `SetGradeScaleUseCaseImpl`, `UpdateGradeScaleUseCaseImpl`, `RemoveGradeScaleUseCaseImpl`. Validan `classificationId` contra `SubjectClassificationRepository` (llamada directa, sin puerto nuevo — mismo patrón que `AcademicProgram`→`AcademicDivision`) y el rango `numericMin < numericMax` (reutilizando `InvalidPlanDataException`, igual que la validación de `minPassingGrade`/`levelNumber`). `CreateAcademicPlanUseCaseImpl` ganó `toGradeScaleResult`/`toGradeScaleEntryResult` (package-visible, reusados por los 2 primeros).
- **Excepciones nuevas**: `DuplicateGradeScaleException` (409), `InvalidGradeScaleEntriesException` (400), `GradeScaleNotFoundException` (404). `ClassificationNotFoundException` se reutilizó tal cual (ya existía, 404) — no se duplicó.
- **Web** (`infrastructure/web/`): 3 endpoints nuevos en `AcademicPlanController` (`POST`/`PUT`/`DELETE /plans/{id}/grade-scales[/{scaleId}]`), DTOs `SetGradeScaleRequest`/`GradeScaleEntryRequest`/`GradeScaleResponse`/`GradeScaleEntryResponse`, campo `gradeScales` agregado a `AcademicPlanResponse`. `GlobalExceptionHandler` extendido con las 3 excepciones nuevas. `UseCaseConfig` registra los 3 beans nuevos.
- **Seguridad**: confirmado que `SecurityFilterConfig` ya cubre `/plans/**` para los 5 verbos (ADMIN + SERVICIOS_ESCOLARES) — no hizo falta ningún matcher nuevo, tal como anticipaba el plan.

### Algoritmo de cobertura/hueco/solape (PO-confirmado)

`GradeScale.validateEntries` (privado, invocado desde el constructor y desde `updateDetails`):

1. Ordena las entries por `fromValue`.
2. Verifica que cada entry tenga `fromValue <= toValue`.
3. Verifica que la primera entry empiece exactamente en `numericMin`.
4. Recorre pares consecutivos: `current.toValue + STEP` debe ser exactamente igual al `next.fromValue` del siguiente — si es menor, hay hueco; si es mayor, hay solape.
5. Verifica que la última entry termine exactamente en `numericMax`.

**Decisión de diseño no anticipada por el dominio**: `numericMin`/`numericMax`/`fromValue`/`toValue` se persisten como `BigDecimal` de **enteros** (`scale = 0`, `STEP = BigDecimal.ONE`). El doc de dominio solo decía "Decimal" sin precisión — se necesitaba un "siguiente valor" concreto para distinguir un límite legítimo (`[0,69]` seguido de `[70,100]`) de un hueco real, y el propio plan (línea de ejemplo con enteros) apuntaba a esa granularity. Si en el futuro se necesitan fronteras fraccionarias, hay que cambiar `STEP` y la escala de columna juntos (documentado en el javadoc de `GradeScale`).

### Gotchas

- `java.util.List` no estaba importado en `AcademicPlanController.java` ni en `AcademicPlanControllerIT.java` — ambos solo usaban `.stream().map().toList()` antes, que no requiere el import explícito por tipo de retorno inferido en variables locales, pero sí lo requiere como tipo de parámetro/campo declarado. Se agregó en ambos.
- El re-fetch tras `save()` sigue el mismo patrón "buscar por clave de negocio única" que `AddPlanLevelUseCaseImpl` documentó como gotcha (Fase 4 de PlanLevel): en `SetGradeScaleUseCaseImpl` se re-busca por `classificationId` (única dentro del plan), no por la referencia en memoria devuelta por `setGradeScale()`.

### Resultados de tests

- Baseline antes de empezar: 270 tests unitarios (`./mvnw test`), 0 fallos.
- Final: **310 tests unitarios** (+40) y **109 tests de integración** (`./mvnw verify`, incluye los `*IT`), 0 fallos/errores, `BUILD SUCCESS`.
- Nuevos: 18 tests de dominio (`AcademicPlanTest`, cobertura exacta/hueco/solape/single-entry/boundary-adjacent/duplicados/not-found), 7+7+3 tests de casos de uso (`Set`/`Update`/`RemoveGradeScaleUseCaseImplTest`), 9 tests de controller (`AcademicPlanControllerTest`, `@WebMvcTest`), 11 tests de integración con JWT real (`AcademicPlanControllerIT`: ADMIN/SERVICIOS_ESCOLARES éxito, DOCENTE 403, sin token 401, plan/scale desconocidos 404, clasificación desconocida 404, clasificación duplicada 409, hueco 400, solape 400).

### Corrección post-implementación (2026-07-20, mismo día — decisión de PO confirmada)

**Motivo**: la decisión de diseño de la sección anterior ("`numericMin`/`numericMax`/`fromValue`/`toValue` como `BigDecimal` de enteros, `scale = 0`") se marcó explícitamente como una suposición del implementador pendiente de confirmar con el PO — no algo que el dominio o `02-config-academica.md` pidieran. José la corrigió el mismo día: **las calificaciones SÍ son decimales**, con un ejemplo real de escala de 4 tramos:

```
7.0–7.5  → letra X
7.6–8.5  → letra Y
8.6–9.5  → letra Z
9.6–10.0 → letra W
```

Es decir, el "siguiente valor" (`STEP`) entre tramos adyacentes es **0.1**, no 1.

**Qué cambió**:

- `GradeScale.STEP`: `BigDecimal.ONE` → `new BigDecimal("0.1")`.
- `GradeScale.numericMin`/`numericMax` y `GradeScaleEntry.fromValue`/`toValue`: columna `@Column(scale = 0)` → `@Column(scale = 1)` — una cifra decimal, igual que `AcademicPlan.minPassingGrade` (`precision = 3, scale = 1`), que ya establecía la convención de columnas tipo-calificación en este agregado. **`precision` se dejó en `5`** (no se copió el `precision = 3` de `minPassingGrade`) porque el rango de una escala (`numericMin`/`numericMax`) no está acotado a `[0,10]` como una calificación mínima individual — hay escalas existentes en el propio test suite que van de `[0,100]`, y `precision = 3` habría rechazado en silencio un límite de `100.0` (4 dígitos significativos). Documentado en el javadoc de `GradeScale`.
- Javadoc de la clase `GradeScale` (líneas ~38-47 antes de la corrección): reescrito para describir el diseño decimal confirmado por el PO en vez de plantearlo como una suposición abierta.
- **Robustez `.equals()` vs `.compareTo()` en `validateEntries`**: se revisó específicamente si la comparación de huecos/solapes dependía de `BigDecimal.equals()` (sensible a `scale`, p. ej. `7.50` ≠ `7.5` aunque sean el mismo número) en vez de `BigDecimal.compareTo()` (insensible a `scale`). **Resultado: no había ningún bug** — las cuatro comparaciones en `validateEntries` (arranque en `numericMin`, cierre en `numericMax`, `fromValue <= toValue`, y el hueco/solape entre entries consecutivas) ya usaban `compareTo()` desde la implementación original, no `equals()`. Se agregó un test (`setGradeScale_toleratesTrailingZeroScaleDifferenceBetweenAdjacentEntries`) que construye un límite como `new BigDecimal("7.50")` adyacente a `new BigDecimal("7.6")` y confirma que NO se marca como hueco, dejando la garantía cubierta por regresión en vez de solo verificada manualmente.

**Tests actualizados/agregados**:

- Fixtures de `AcademicPlanTest`, `SetGradeScaleUseCaseImplTest.setGradeScale_successfulCreation` y `UpdateGradeScaleUseCaseImplTest.updateGradeScale_successfulReplace` que usaban límites adyacentes enteros (p. ej. `[0,69]`+`[70,100]`, `[0,6]`+`[7,10]`) se ajustaron a límites decimales adyacentes con paso `0.1` (`[0,69.9]`+`[70.0,100]`, `[0,6.9]`+`[7.0,10]`) — con `STEP = 0.1` esos límites enteros ya no son adyacentes y el test habría fallado por "Gap detected" al no reflejar la nueva granularidad. Los tests de hueco/solape "genuino" (`rejectsAGapBetweenEntries`, `rejectsAnOverlapBetweenEntries`, y sus equivalentes en los casos de uso) no necesitaron cambios: siguen siendo hueco/solape real bajo cualquier `STEP`.
- 4 tests nuevos en `AcademicPlanTest` con el ejemplo real de José: `setGradeScale_decimalFourTierScalePasses` (los 4 tramos exactos deben pasar), `setGradeScale_decimalBoundaryOffByOneTenthIsAGap` (un límite corrido 0.1, p. ej. `7.6`→`7.7`, debe fallar como hueco), `setGradeScale_decimalOverlapAtSharedBoundaryIsRejected` (dos tramos compartiendo `7.5` debe fallar como solape), y `setGradeScale_toleratesTrailingZeroScaleDifferenceBetweenAdjacentEntries` (la prueba de robustez `.equals()` vs `.compareTo()` descrita arriba).
- `AcademicPlanControllerIT.adminCanSetUpdateAndRemoveGradeScale`: mismos límites enteros adyacentes ajustados a decimales (`69.9`/`70.0`, `6.9`/`7.0`) por la misma razón. `AcademicPlanControllerTest` (`@WebMvcTest`, casos de uso mockeados) no necesitó cambios — los use cases están stubbeados con `when(...).thenReturn(...)`, así que `validateEntries` nunca se ejecuta ahí.
- No se tocaron: DTOs (`SetGradeScaleRequest`/`GradeScaleEntryRequest`/`*Response`) ni casos de uso — todos usan `BigDecimal` genérico sin anotaciones de escala propias, y la validación `numericMin < numericMax` en los use cases ya usaba `compareTo()`.

**Resultados de tests**: baseline antes de la corrección (recomprobado, `./mvnw test`): **310 tests unitarios, 0 fallos**. Después de la corrección (`./mvnw verify`): **314 tests unitarios** (+4, todos nuevos — ningún test se eliminó) y **109 tests de integración**, 0 fallos/errores, `BUILD SUCCESS`.
