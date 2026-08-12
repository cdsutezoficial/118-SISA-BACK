# Plan: Catálogos State/Municipality (INEGI) + HighSchoolType — Fase A de la extensión de Person

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md` (secciones `State`, `Municipality`, `HighSchoolType`). Primera fase de 2 para extender `Person` de cara al registro de candidatos (RF-ADM-011). Fase B (siguiente) extiende `Person` en sí + crea las tablas normalizadas `person_address`/`person_health_profile`/`person_diversity_profile`/`person_employment_info`/`person_high_school_background` (decisión de José, 2026-07-28: normalizar en tablas propias, NO usar `@Embeddable`/Value Objects de JPA).

## 2. Datos INEGI — origen y verificación

`State`/`Municipality` son catálogos **cerrados, sembrados una sola vez, sin CRUD ni pantalla** ("no se gestiona desde la UI", `00-shared-kernel.md`).

**Fuente de los datos**: repositorio público [`developarts/AGEEML`](https://github.com/developarts/AGEEML) en GitHub — dump SQL del Catálogo Único de Claves de Áreas Geoestadísticas Estatales y Municipales del INEGI (versión 2020.10.v01). Descargado, descomprimido y verificado manualmente en esta sesión:
- 32 estados (tabla `estados`: id, clave INEGI de 2 dígitos, nombre, abreviatura).
- 2,469 municipios (tabla `municipios`: id, FK a estado, clave INEGI de 3 dígitos, nombre).
- Verificado explícitamente: Morelos (clave `17`) tiene 36 municipios reales, incluyendo "Emiliano Zapata" (clave `008`) — coincide con el nombre de la propia universidad. Los 32 códigos de estado están representados en los municipios.

**Ya extraídos y listos para usar** (no hace falta volver a parsear el dump SQL): `src/main/resources/seed/inegi_estados.csv` (columnas `inegi_code,name`) y `src/main/resources/seed/inegi_municipios.csv` (columnas `state_inegi_code,inegi_code,name`) — colocados en este plan, listos para que el seed runner los lea directamente como classpath resources.

## 3. Modelo

### `State` (tabla `state`)
| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| name | String | No nulo — ej. "Morelos" |
| inegiCode | String(2) | Único |

### `Municipality` (tabla `municipality`)
| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| stateId | UUID | FK → `State` |
| name | String | No nulo |
| inegiCode | String(3) | Único por `stateId` (no global — varios estados reutilizan la clave "001", etc.) |

Ambos viven en `shared/model/` (shared kernel, no pertenecen a ningún bounded context) — mismo paquete que `Person`.

### `HighSchoolType` (tabla `high_school_type`) — CRUD simple, a diferencia de los dos anteriores
| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| name | String | No nulo — ej. "Conalep", "Cobaem", "Cecyte", "Bachillerato General" |
| status | Enum | ACTIVE, INACTIVE |

`HighSchoolType` vive en `admission` (es específico del formulario de registro de candidato) — mismo patrón de 5 casos de uso que `OutreachChannel` (aggregate más simple, sin FKs).

## 4. Seed runner para State/Municipality

- `StateAndMunicipalitySeedRunner` (mismo patrón que `AdminSeedRunner`/`ServiciosEscolaresSeedRunner`) — corre en el arranque, **solo si la tabla `state` está vacía** (idempotente, no re-siembra en cada reinicio).
- Lee `classpath:seed/inegi_estados.csv` primero (inserta los 32 estados, guarda el mapeo `inegiCode → State.id` generado), después `classpath:seed/inegi_municipios.csv` (resuelve `state_inegi_code` contra ese mapeo para setear `stateId`, inserta los 2,469 municipios).
- Usar `saveAll()` por lotes para los municipios (2,469 filas), no una fila a la vez.
- Sin exponer ningún endpoint — no hay `StateController`/`MunicipalityController`. Estas tablas se consultarán desde Fase B (los `SearchSelectField` de State/Municipality en el futuro formulario de candidato) vía un endpoint de solo lectura simple (`GET /states`, `GET /municipalities?stateId=`) — **incluido en esta fase** porque sin eso las tablas quedan inútiles para cualquier consumidor futuro, aunque no haya CRUD de escritura.

## 5. Endpoints (solo lectura, sin CRUD de escritura)

- `GET /states` — lista completa (32 filas, sin paginación — igual que `PaymentRate`'s historial, volumen bajo y fijo).
- `GET /municipalities?stateId=` — lista de municipios de un estado (`stateId` obligatorio como filtro; sin paginación, ningún estado tiene más de ~570 municipios — Oaxaca es el caso extremo real de México, pero no bloquea nada).
- `HighSchoolType`: `POST/GET /high-school-types`, `GET/PUT /high-school-types/{id}`, `PATCH /high-school-types/{id}/status` — CRUD completo, mismo patrón que `OutreachChannel`.

**Seguridad**: `GET /states`/`GET /municipalities` → cualquier usuario autenticado (`authenticated()`, sin restricción de rol — son catálogos de referencia que cualquier formulario puede necesitar consultar, no hay razón de negocio para restringirlos). `HighSchoolType` → `ADMIN`/`SERVICIOS_ESCOLARES` (mismo criterio que `OutreachChannel`).

## 6. Fuera de alcance

- Cualquier campo de `Person` (Fase B).
- `Candidate`/`RegisterCandidateUseCase` (Fase C).
- Frontend (fase posterior).

## 7. Execution Log (2026-07-28)

### Archivos creados

**Shared kernel (`State`/`Municipality`)**
- `src/main/java/mx/edu/utez/sisa/shared/model/State.java`
- `src/main/java/mx/edu/utez/sisa/shared/model/Municipality.java`
- `src/main/java/mx/edu/utez/sisa/shared/persistence/StateJpaRepository.java`
- `src/main/java/mx/edu/utez/sisa/shared/persistence/MunicipalityJpaRepository.java`
- `src/main/java/mx/edu/utez/sisa/shared/bootstrap/StateAndMunicipalitySeedRunner.java`
- `src/main/java/mx/edu/utez/sisa/shared/web/StateController.java`
- `src/main/java/mx/edu/utez/sisa/shared/web/MunicipalityController.java`
- `src/main/java/mx/edu/utez/sisa/shared/web/dto/StateListItemResponse.java`, `StateListResponse.java`, `MunicipalityListItemResponse.java`, `MunicipalityListResponse.java`
- Tests: `src/test/java/mx/edu/utez/sisa/shared/bootstrap/StateAndMunicipalitySeedRunnerIT.java`, `src/test/java/mx/edu/utez/sisa/shared/web/StateControllerIT.java`, `MunicipalityControllerIT.java`

**`admission` — `HighSchoolType`** (full clone of `OutreachChannel`'s stack)
- `domain/model/HighSchoolType.java`, `HighSchoolTypeStatus.java`
- `domain/port/in/{Create,Update,List,Get,ChangeStatus}HighSchoolTypeUseCase.java`
- `domain/port/out/HighSchoolTypeRepository.java`
- `domain/service/{Create,Update,List,Get,ChangeStatus}HighSchoolTypeUseCaseImpl.java`
- `shared/exception/HighSchoolTypeNotFoundException.java`
- `infrastructure/persistence/HighSchoolTypeJpaRepository.java`, `HighSchoolTypeRepositoryAdapter.java`
- `infrastructure/web/HighSchoolTypeController.java` + 6 DTOs
- Tests: domain unit test, 5 use-case tests, repository-adapter IT, controller unit test (`@WebMvcTest`), controller IT (`@SpringBootTest`) — 6 files total, mirroring `OutreachChannel`'s test suite 1:1.

### Archivos modificados

- `admission/infrastructure/config/UseCaseConfig.java` — added 5 `HighSchoolType` use-case beans.
- `admission/infrastructure/web/GlobalExceptionHandler.java` — added `HighSchoolTypeNotFoundException` -> 404 handler.
- `identity/infrastructure/security/SecurityFilterConfig.java` — added `/high-school-types` (4 verb matchers, ADMIN/SERVICIOS_ESCOLARES) and `/states`, `/municipalities` (GET only, `.authenticated()`, no role restriction).
- `identity/infrastructure/web/GlobalExceptionHandler.java` — added a `MissingServletRequestParameterException` -> 400 handler (see deviation below).

### Decisiones técnicas

1. **Parsing de CSV**: se detectó (grep manual sobre las 2469 filas) exactamente **una** fila con coma embebida y comillas RFC-4180 (`20,549,"Heroica Villa Tezoatlán de Segura y Luna, Cuna de la Independencia de Oaxaca"` — Oaxaca). Un `String.split(",")` ingenuo la habría partido en 4 campos en lugar de 3. Se implementó un parser mínimo consciente de comillas (`parseCsvLine` en `StateAndMunicipalitySeedRunner`) en vez de asumir CSV simple — spot-check manual confirmó que ninguna otra fila tiene el mismo problema.
2. **`State`/`Municipality` sin puertos hexagonales**: siguiendo el brief, se usó `JpaRepository` plano (`StateJpaRepository`/`MunicipalityJpaRepository`) sin port-in/port-out ni casos de uso — son catálogos cerrados, de solo lectura, sin lógica de negocio. Se verificó el patrón existente de `PersonRepository` (que sí tiene puerto hexagonal completo) y se decidió NO replicarlo aquí porque, a diferencia de `Person`, no hay un módulo dueño con casos de uso propios consumiendo estas entidades todavía.
3. **Ubicación del controlador de solo lectura**: `shared.web.StateController`/`MunicipalityController` — mismo paquete que `shared.web.dto.ErrorResponse`, ya existente. No hay bounded context dueño de estos catálogos aún (Fase B con `Person` será el primer consumidor real), así que viven junto al resto del shared kernel en lugar de forzar su entrada a `academic_config` o `admission`.
4. **`MissingServletRequestParameterException` en `identity.GlobalExceptionHandler`**: `GET /municipalities?stateId=` es el primer endpoint del proyecto con un `@RequestParam` requerido sin default. Sin un handler dedicado, la falta del parámetro caía en el catch-all `Exception.class` y devolvía 500 en vez de 400. Se agregó el handler específico (mismo criterio que el ya existente para `MethodArgumentTypeMismatchException`) — pequeña desviación no listada explícitamente en el brief original, pero necesaria para que el endpoint se comporte correctamente ante uso normal (parámetro omitido por error del cliente).
5. **Idempotencia del seed runner**: se verifica `stateRepository.count() > 0` (no un invariante de rol como `AdminSeedRunner`) — este catálogo no tiene una regla de negocio más estrecha que revisar; o está completamente sembrado o no lo está.

### Resultados de pruebas

- Baseline antes de esta tarea (JDK 21, `./mvnw verify`): **680 unit / 361 IT**, 0 fallos/errores (confirmado por ejecución real, no asumido — el baseline de 646/361 mencionado en el brief había subido a 680 unit por trabajo de `Generation`/`Person management` ya mergeado o en curso).
- Después de esta tarea: ejecución completa de `./mvnw verify` — ver resultado final en el reporte de la tarea (unit y IT ambos en verde, incluyendo:
  - `StateAndMunicipalitySeedRunnerIT`: 5/5 — confirma exactamente 32 estados y 2,469 municipios, y el spot-check Morelos (`17`) / Emiliano Zapata (`008`) / 36 municipios totales en Morelos.
  - `StateControllerIT`/`MunicipalityControllerIT`: 3/3 cada uno — incluyendo el spot-check vía API y el 400 por `stateId` faltante.
  - `HighSchoolTypeControllerIT`: 24/24, `HighSchoolTypeRepositoryAdapterSearchIT`: 8/8, más 6 archivos de test unitario para el dominio/casos de uso/controlador.

### Desviaciones del brief

- Se agregó un handler de `MissingServletRequestParameterException` a `identity.GlobalExceptionHandler` (no mencionado explícitamente en el brief) para evitar que `GET /municipalities` sin `stateId` devolviera 500 en vez de 400 — ver decisión técnica 4.
- Todo lo demás sigue el brief sin desviaciones: mismos endpoints, misma seguridad, mismo patrón de `OutreachChannel` clonado para `HighSchoolType`.
