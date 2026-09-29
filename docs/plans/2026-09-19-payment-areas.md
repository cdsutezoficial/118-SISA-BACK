# Plan: PaymentArea (CRUD de Áreas de Conceptos de Pago)

> Recurso: `PaymentArea` — catálogo compañero de `PaymentConcept` que agrupa
> conceptos de pago (p. ej. "Cuotas de Inscripción", "Colegiaturas").
> Bounded context: `academic_config`. Endpoint raíz: `/payment-areas`.

## 1. Contexto (docs-first)

- **Código fuente del módulo:** `118-SISA-BACK`, paquete
  `mx.edu.utez.sisa.academic_config`.
- **Frontend consumidor (ya implementado):**
  - `118-SISA-FRONT/src/app/modules/config-academica/pages/AreasList.tsx`
    (lista, filtro por status, búsqueda, toggle de estado).
  - `118-SISA-FRONT/src/app/modules/config-academica/pages/AreasForm.tsx`
    (alta/edición/ver: `name`, `code`, `description`).
  - `118-SISA-FRONT/src/app/modules/config-academica/pages/ConceptosForm.tsx`
    (dropdown de `Área` alimentado por `GET /payment-areas?status=ACTIVE`).
  - Rutas front: `/areas`, `/areas/new`, `/areas/form`.

- **Proveniencia documental (importante):** el catálogo `PaymentArea` **no
  aparece** en la documentación de dominio/requisitos revisada:
  - `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` define
    `PaymentConcept` y `PaymentRate`, pero **no** `PaymentArea`.
  - `118-SISA-CLAUDE/docs/requirements/07-PAGOS.md` (RF-PAG-001, HU-PAG-001)
    describe el catálogo de conceptos, sin mención de "áreas".
  - `118-SISA-CLAUDE/docs/design/figma/prompts/08-pagos.md` tampoco lo incluye.

  Es decir: `PaymentArea` nace del **frontend/spec de pantalla ya existente**
  (agrupador de conceptos, mostrado como "clave — nombre" en el formulario de
  conceptos). Se implementa como catálogo independiente dentro de
  `academic_config`, sin FK todavía desde `PaymentConcept` (ver §8).

- **Fase de trabajo:** backend del CRUD completo (5 casos de uso + controller +
  seguridad + permisos). El frontend ya está listo y define el contrato.

## 2. Comparación docs vs. implementación actual

| Capa | Estado hoy |
|---|---|
| Docs de dominio | Sin `PaymentArea`; sí `PaymentConcept`/`PaymentRate` |
| Backend | **Cero código**: no existe `PaymentArea` ni `/payment-areas` (verificado por grep en todo el repo) |
| Seguridad | Sin matchers `/payment-areas` en `SecurityFilterConfig` |
| Permisos | Sin claves `PAYMENT_AREAS_*` en el seed |
| Frontend | **Implementado** y esperando el API (`/payment-areas`) |
| Respaldo | 18 archivos `PaymentArea` en `Temp\opencode\sisa-reset-backup\BACK`, **no compilan** y nunca se commitearon; se usan solo como referencia de contenido |

**Desviaciones del respaldo que este plan corrige** (para que quede constancia):

1. `CreatePaymentAreaCommand` incluía `UUID id` y anotaciones `jakarta.validation`
   en el dominio → se eliminan (validación solo en DTO web; id lo genera JPA).
2. `ChangePaymentAreaStatusCommand` sin `callerId` → se agrega (convención del repo).
3. `GetPaymentAreaUseCase` devolvía un sentinel `EMPTY` → debe **lanzar**
   `PaymentAreaNotFoundException` (404), como `GetPaymentConceptUseCase`.
4. `ListPaymentAreasUseCase` lanzaba por `size<=0` en vez de normalizar → se
   normaliza con `DEFAULT_PAGE_SIZE`/`MAX_PAGE_SIZE`.
5. Port-out y servicios filtraban Spring `Page`/`Pageable` al dominio → el
   dominio usa records propios (`PaymentAreaSearchCriteria` /
   `PaymentAreaSearchPage`); `Page`/`Pageable` solo en `infrastructure/persistence`.
6. Errores de compilación: imports `PaymentAreaResult`/`UpdatePaymentAreaResult`/
   `ChangePaymentAreaStatusResult` inexistentes, `pageable<head>`,
   `pageableipse;`, import duplicado de `UUID`.
7. Unicidad de `name`: el respaldo solo cubría `code`, pero el front muestra 409
   "El nombre o la clave ya están en uso" → se valida **name y code**, con dos
   excepciones (patrón `AcademicDivision`).

## 3. Alcance de esta fase

- Entidad `PaymentArea` + enum `PaymentAreaStatus` (`ACTIVE`/`INACTIVE`).
- Port-out `PaymentAreaRepository` (save, findById, findByName, findByCode,
  search, findOptions).
- 5 casos de uso: `Create`, `Update`, `Get`, `List`, `ChangeStatus`.
- Adaptador JPA + repositorio Spring Data.
- Controller REST `/payment-areas` (5 endpoints + `GET /payment-areas/options`).
- 6 DTOs web (`records`) + 3 excepciones.
- Seguridad: matchers en `SecurityFilterConfig` (ADMIN + PERSONAL_FINANZAS) y
  permisos `PAYMENT_AREAS_READ/CREATE/UPDATE/CHANGE_STATUS` en el seed.
- Wiring en `UseCaseConfig`.
- Tests (modelo, 5 use cases, persistencia IT, controller unit + IT).
- Documento de Execution Log al cerrar la implementación.

## 4. Modelo (campos exactos; sin invariantes inventadas)

### `PaymentArea` — `@Table(name = "payment_area")`

| Atributo | Tipo | Restricciones |
|---|---|---|
| `id` | `UUID` | `@Id @GeneratedValue(strategy = GenerationType.UUID)` |
| `name` | `String` | `@Column(nullable = false, unique = true)` — unicidad también validada en dominio |
| `code` | `String` | `@Column(nullable = false, unique = true)` — clave corta mostrada junto al nombre |
| `description` | `String` | `@Column(columnDefinition = "TEXT")`, nullable |
| `status` | `PaymentAreaStatus` | `@Enumerated(EnumType.STRING)`, `nullable = false` |

### `PaymentAreaStatus` — `enum { ACTIVE, INACTIVE }` (propio del agregado)

### Comportamiento de la entidad

- Constructor `protected PaymentArea()` (JPA) + público
  `PaymentArea(String name, String code, String description)` → `status = ACTIVE`.
- `updateDetails(String name, String code, String description)` — **no** toca `status`.
- `activate()` / `deactivate()` idempotentes (no-op si ya está en ese estado;
  nunca hay borrado físico).
- `equals`/`hashCode` por `id`.

### Validaciones a nivel de caso de uso

El doc no las detalla; se infieren del patrón `AcademicDivision`
(marcadas para que José las corrija):

- `name` y `code` obligatorios (no blank) → `InvalidPaymentAreaDataException` (400).
- `code` duplicado (case-insensitive, excluyendo el propio id en Update) →
  `DuplicatePaymentAreaCodeException` (409).
- `name` duplicado (case-insensitive, excluyendo el propio id en Update) →
  `DuplicatePaymentAreaNameException` (409).
- No hay validaciones numéricas ni de fechas en este agregado.

## 5. Casos de uso

Cinco casos, con el mismo patrón hexagonal que `PaymentConcept`
(`domain/port/in`, `domain/service`, wiring en `UseCaseConfig`):

| Interfaz (`port/in`) | Comando / Consulta | Resultado |
|---|---|---|
| `CreatePaymentAreaUseCase` | `CreatePaymentAreaCommand(String name, String code, String description)` | `CreatePaymentAreaResult(UUID id, String name, String code, String description, PaymentAreaStatus status)` |
| `UpdatePaymentAreaUseCase` | `UpdatePaymentAreaCommand(UUID id, String name, String code, String description)` | `UpdatePaymentAreaResult(...)` |
| `GetPaymentAreaUseCase` | `getPaymentArea(UUID id)` | `GetPaymentAreaResult(...)`; lanza `PaymentAreaNotFoundException` |
| `ListPaymentAreasUseCase` | `ListPaymentAreasQuery(PaymentAreaStatus status, String search, int page, int size)` | `ListPaymentAreasResult(List<PaymentAreaSummary> items, long totalElements, int totalPages, int page, int size)` con `PaymentAreaSummary(UUID id, String name, String code, String description, PaymentAreaStatus status)` |
| `ChangePaymentAreaStatusUseCase` | `ChangePaymentAreaStatusCommand(UUID callerId, UUID id, PaymentAreaStatus target)` | `ChangePaymentAreaStatusResult(...)` |

- Los `record` de comando/consulta/resultado van **anidados en la interfaz**.
- `ListPaymentAreasQuery` incluye las constantes `DEFAULT_PAGE_SIZE = 20` y
  `MAX_PAGE_SIZE = 100` (normalización en el impl, no excepción).
- `callerId` se reserva para auditoría futura (hoy no se lee), igual que en
  `ChangePaymentConceptStatusUseCase`.

### Port-out — `domain/port/out/PaymentAreaRepository`

```java
PaymentArea save(PaymentArea area);
Optional<PaymentArea> findById(UUID id);
Optional<PaymentArea> findByName(String name);   // case-insensitive en el adapter
Optional<PaymentArea> findByCode(String code);   // case-insensitive en el adapter
PaymentAreaSearchPage search(PaymentAreaSearchCriteria criteria);
List<PaymentAreaOption> findOptions();           // solo ACTIVE, para pickers

record PaymentAreaSearchCriteria(PaymentAreaStatus status, String search, int page, int size) {}
record PaymentAreaSearchPage(List<PaymentArea> content, long totalElements, int totalPages) {}
record PaymentAreaOption(UUID id, String name, String code) {}
```

Sin Spring `Page`/`Pageable` en el dominio. Orden determinista
`name ASC, id ASC` (tie-break porque `name` podría repetirse a nivel de collation).

## 6. Endpoints y seguridad

| Verbo | Ruta | Respuesta | Notas |
|---|---|---|---|
| `POST` | `/payment-areas` | `201 Created` + `PaymentAreaResponse` | body `{ name, code, description }` |
| `PUT` | `/payment-areas/{id}` | `200` + `PaymentAreaResponse` | 404 si no existe; `status` no se toca |
| `GET` | `/payment-areas/{id}` | `200` + `PaymentAreaResponse` | 404 si no existe |
| `GET` | `/payment-areas?status=&search=&page=0&size=20` | `200` + `{ items[], totalElements, totalPages, page, size }` | `search` matchea `name` **o** `code` |
| `PATCH` | `/payment-areas/{id}/status` | `200` + `PaymentAreaResponse` | body `{ "status": "ACTIVE" \| "INACTIVE" }` |
| `GET` | `/payment-areas/options` | `200` + `List<OptionResponse>` | solo `ACTIVE`; para pickers (ver §8) |

**Seguridad** — sin `@PreAuthorize`; matchers en
`identity/infrastructure/security/SecurityFilterConfig.java` (mismo par de roles
que `/payment-concepts`, justificado por RF-PAG-001 "finanzas"):

```java
.requestMatchers(HttpMethod.GET, "/payment-areas/options").authenticated()
.requestMatchers(HttpMethod.GET, "/payment-areas", "/payment-areas/**")
        .hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
.requestMatchers(HttpMethod.POST, "/payment-areas")
        .hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
.requestMatchers(HttpMethod.PUT, "/payment-areas/**")
        .hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
.requestMatchers(HttpMethod.PATCH, "/payment-areas/**")
        .hasAnyRole("ADMIN", "PERSONAL_FINANZAS")
```

> Nota: `GET /payment-areas/options` debe ir **antes** del matcher genérico
> `GET /payment-areas/**` (el orden importa; los específicos primero).

**Permisos RBAC** — en
`identity/infrastructure/bootstrap/IdentityAuthorizationCatalogSeedRunner.java`:

```java
permission("PAYMENT_AREAS_READ", "Consultar áreas de conceptos de pago"),
permission("PAYMENT_AREAS_CREATE", "Crear área de conceptos de pago"),
permission("PAYMENT_AREAS_UPDATE", "Actualizar área de conceptos de pago"),
permission("PAYMENT_AREAS_CHANGE_STATUS", "Cambiar estatus de área de conceptos de pago"),
// ...
permissionsByRole.put(RoleType.PERSONAL_FINANZAS, orderedSet(
        "PAYMENT_AREAS_READ", "PAYMENT_AREAS_CREATE",
        "PAYMENT_AREAS_UPDATE", "PAYMENT_AREAS_CHANGE_STATUS", /* ...existentes... */));
```

`ADMIN` los recibe vía `allPermissions`. El seed es idempotente
(`findByKey().orElseGet(save)` + solo inserta `RolePermission` faltantes).

**Excepciones** (`academic_config/shared/exception/`) y sus handlers en
`academic_config/infrastructure/web/GlobalExceptionHandler.java`:

| Excepción | HTTP | Mensaje |
|---|---|---|
| `PaymentAreaNotFoundException` | 404 | "No se encontró el área de conceptos de pago solicitada." |
| `InvalidPaymentAreaDataException` | 400 | "Revisa la información proporcionada para el área de conceptos de pago." |
| `DuplicatePaymentAreaCodeException` | 409 | "La clave del área ya está en uso." |
| `DuplicatePaymentAreaNameException` | 409 | "El nombre del área ya está en uso." |

## 7. Patrón a clonar

- **Base:** `PaymentConcept` (mismo bounded context, mismos 5 use cases,
  controller delgado, DTOs record, wiring manual).
- **Extra de unicidad por dominio:** `AcademicDivision`
  (`findByName`/`findByCode` case-insensitive + `DuplicateDivisionNameException`/
  `DuplicateDivisionCodeException`), replicado aquí.

### Archivos a crear

`src/main/java/mx/edu/utez/sisa/academic_config/`:

1. `domain/model/PaymentArea.java`
2. `domain/model/PaymentAreaStatus.java`
3. `domain/port/in/CreatePaymentAreaUseCase.java`
4. `domain/port/in/UpdatePaymentAreaUseCase.java`
5. `domain/port/in/GetPaymentAreaUseCase.java`
6. `domain/port/in/ListPaymentAreasUseCase.java`
7. `domain/port/in/ChangePaymentAreaStatusUseCase.java`
8. `domain/port/out/PaymentAreaRepository.java`
9. `domain/service/CreatePaymentAreaUseCaseImpl.java`
10. `domain/service/UpdatePaymentAreaUseCaseImpl.java`
11. `domain/service/GetPaymentAreaUseCaseImpl.java`
12. `domain/service/ListPaymentAreasUseCaseImpl.java`
13. `domain/service/ChangePaymentAreaStatusUseCaseImpl.java`
14. `infrastructure/persistence/PaymentAreaJpaRepository.java`
15. `infrastructure/persistence/PaymentAreaRepositoryAdapter.java`
16. `infrastructure/web/PaymentAreaController.java`
17. `infrastructure/web/dto/CreatePaymentAreaRequest.java`
18. `infrastructure/web/dto/UpdatePaymentAreaRequest.java`
19. `infrastructure/web/dto/ChangePaymentAreaStatusRequest.java`
20. `infrastructure/web/dto/PaymentAreaResponse.java`
21. `infrastructure/web/dto/PaymentAreaListItemResponse.java`
22. `infrastructure/web/dto/PaymentAreaListResponse.java`
23. `shared/exception/PaymentAreaNotFoundException.java`
24. `shared/exception/InvalidPaymentAreaDataException.java`
25. `shared/exception/DuplicatePaymentAreaCodeException.java`
26. `shared/exception/DuplicatePaymentAreaNameException.java`

### Archivos a modificar

- `infrastructure/config/UseCaseConfig.java` — 5 `@Bean` (create/update/get/list/changeStatus).
- `infrastructure/web/GlobalExceptionHandler.java` — 4 handlers (404/400/409/409).
- `identity/infrastructure/security/SecurityFilterConfig.java` — matchers §6.
- `identity/infrastructure/bootstrap/IdentityAuthorizationCatalogSeedRunner.java` — permisos §6.

### DTOs (records)

```java
record CreatePaymentAreaRequest(@NotBlank String name, @NotBlank String code, String description) {}
record UpdatePaymentAreaRequest(@NotBlank String name, @NotBlank String code, String description) {}
record ChangePaymentAreaStatusRequest(@NotNull PaymentAreaStatus status) {}
record PaymentAreaResponse(UUID id, String name, String code, String description, PaymentAreaStatus status) {}
record PaymentAreaListItemResponse(UUID id, String name, String code, String description, PaymentAreaStatus status) {}
record PaymentAreaListResponse(List<PaymentAreaListItemResponse> items, long totalElements, int totalPages, int page, int size) {}
```

## 8. Fuera de alcance (de esta fase)

- **FK `PaymentConcept.areaId`** → `PaymentArea`: el javadoc del respaldo la
  anticipa, pero `PaymentConcept` real no tiene ese campo. La relación
  concepto→área queda para una fase posterior (el front ya captura `areaId`
  frontend-first).
- Migraciones versionadas: el repo **no usa Flyway**; la tabla `payment_area`
  la genera Hibernate (`ddl-auto` configurable por entorno; en local, vía `.env`).
- Auditoría (`createdAt`/`createdBy`) y uso real de `callerId`.
- Borrado físico: no existe; solo `ACTIVE`/`INACTIVE`.

## 9. Execution Log (2026-09-19)

### Archivos creados

**Dominio** (`academic_config/domain/`):
- `model/PaymentArea.java`, `model/PaymentAreaStatus.java`
- `port/in/CreatePaymentAreaUseCase.java`, `UpdatePaymentAreaUseCase.java`, `GetPaymentAreaUseCase.java`, `ListPaymentAreasUseCase.java`, `ChangePaymentAreaStatusUseCase.java`
- `port/out/PaymentAreaRepository.java`
- `service/CreatePaymentAreaUseCaseImpl.java`, `UpdatePaymentAreaUseCaseImpl.java`, `GetPaymentAreaUseCaseImpl.java`, `ListPaymentAreasUseCaseImpl.java`, `ChangePaymentAreaStatusUseCaseImpl.java`

**Excepciones** (`academic_config/shared/exception/`):
- `PaymentAreaNotFoundException.java` (404)
- `DuplicatePaymentAreaNameException.java` (409)
- `DuplicatePaymentAreaCodeException.java` (409)
- `InvalidPaymentAreaDataException.java` (400 — `name`/`code` en blanco)

**Persistencia** (`academic_config/infrastructure/persistence/`):
- `PaymentAreaJpaRepository.java`, `PaymentAreaRepositoryAdapter.java`

**Web** (`academic_config/infrastructure/web/`):
- `PaymentAreaController.java`
- `dto/CreatePaymentAreaRequest.java`, `UpdatePaymentAreaRequest.java`, `ChangePaymentAreaStatusRequest.java`, `PaymentAreaResponse.java`, `PaymentAreaListItemResponse.java`, `PaymentAreaListResponse.java`

**Tests** (9 archivos): `PaymentAreaTest` (modelo), 5 tests de casos de uso (`Create`/`Update`/`Get`/`List`/`ChangeStatus`), `PaymentAreaRepositoryAdapterSearchIT`, `PaymentAreaControllerTest`, `PaymentAreaControllerIT`.

### Archivos modificados

- `UseCaseConfig.java` (academic_config) — 5 imports de port/in, 5 imports de service, `PaymentAreaRepository` import, 5 beans nuevos.
- `GlobalExceptionHandler.java` (academic_config) — 4 handlers nuevos (404 `PaymentAreaNotFoundException`, 409 `DuplicatePaymentAreaNameException`, 409 `DuplicatePaymentAreaCodeException`, 400 `InvalidPaymentAreaDataException`).
- `SecurityFilterConfig.java` (identity) — párrafo Javadoc del nuevo aggregate y 5 matchers: `GET /payment-areas/options` → `authenticated()`; `GET /payment-areas`, `GET /payment-areas/**`, `POST`, `PUT /**`, `PATCH /**` → `hasAnyRole("ADMIN","PERSONAL_FINANZAS")`.
- `IdentityAuthorizationCatalogSeedRunner.java` (identity) — permisos `PAYMENT_AREAS_READ`/`CREATE`/`UPDATE`/`CHANGE_STATUS`, otorgados a `PERSONAL_FINANZAS` (ADMIN los hereda).
- `application.properties` (main) — `spring.test.database.replace=none` (ver decisiones técnicas #5).

### Decisiones técnicas (con justificación)

1. **`/options` lee directo del `JpaRepository`**: el endpoint `GET /payment-areas/options` (mismo patrón que `divisions`/`generations`) inyecta `PaymentAreaJpaRepository` y llama `findByStatusOrderByNameAsc(ACTIVE)`. Por eso se eliminó `findActiveOptions()` del out-port (quedaba como código muerto).

2. **Dos claves de negocio únicas, dos excepciones 409**: a diferencia de `PaymentConcept` (sin clave única), el contrato del front exige unicidad tanto de `name` como de `code`. Se usan `DuplicatePaymentAreaNameException` y `DuplicatePaymentAreaCodeException` para mensajes distintos, con lookup case-insensitive (`findByNameIgnoreCase`/`findByCodeIgnoreCase`).

3. **Validación de `name`/`code` en blanco en el dominio**: `InvalidPaymentAreaDataException` (400) desde el modelo/servicios, no vía bean validation, siguiendo el patrón del módulo.

4. **`description` como `TEXT` y presente en el item de lista**: el front (`AreasList.tsx`) lee `description`, así que `PaymentAreaListItemResponse` la incluye (no solo el detalle).

5. **Tests sobre MySQL, sin H2**: commit `32916bd` reemplazó H2 por `mysql-connector-j` en `pom.xml`, pero las 16 clases `@DataJpaTest` seguían intentando el swap a H2 embebido y fallaban con *Failed to replace DataSource with an embedded database*. Se agregó `spring.test.database.replace=none` para que las slices usen el datasource MySQL configurado (`.env`, `DB_DDL_AUTO=create-drop`).

### Resultado de tests

- Alcance PaymentArea: **87 tests, 0 failures, 0 errors** (9 clases).
  - Modelo `PaymentAreaTest`: 8
  - `CreatePaymentAreaUseCaseImplTest`: 7 · `UpdatePaymentAreaUseCaseImplTest`: 7 · `GetPaymentAreaUseCaseImplTest`: 2 · `ListPaymentAreasUseCaseImplTest`: 4 · `ChangePaymentAreaStatusUseCaseImplTest`: 4
  - `PaymentAreaRepositoryAdapterSearchIT`: 12 · `PaymentAreaControllerTest` (MockMvc): 17 · `PaymentAreaControllerIT`: 26
- Comando: `.\mvnw.cmd "-Dtest=*PaymentArea*" "-Dsurefire.failIfNoSpecifiedTests=false" test` → BUILD SUCCESS (JDK 21).
- **`mvn verify` global NO queda verde por fallos preexistentes ajenos a esta fase** (no tocados): 3 fallos en `identity.infrastructure.web.GlobalExceptionHandlerTest` (espera mensajes en inglés/raw, el handler devuelve español) y 48 errores + 2 fallos en ITs de `identity`/`shared` por compartir un único esquema MySQL entre contextos (`Too many connections` y `create-drop` que borra tablas bajo otros contextos). Todos los ITs de `academic_config` pasan.

### Desviaciones del plan

Ninguna funcional: el modelo, los 5 casos de uso, los endpoints y el par de roles `ADMIN`/`PERSONAL_FINANZAS` se implementaron como se documentó. La única desviación es de infraestructura de tests (decisión #5).
