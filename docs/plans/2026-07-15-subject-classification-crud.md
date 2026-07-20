# Plan: SubjectClassification CRUD (academic_config)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` (líneas 15-24).

`SubjectClassification` es un aggregate propio dentro del bounded context `academic_config`, ya existente en el backend (paquete `mx.edu.utez.sisa.academic_config`, ya contiene `AcademicDivision` y `AcademicProgram`).

> Catálogo de clasificaciones de materias. La clasificación de una materia determina qué escala de calificaciones (equivalencia numérico → letra) se aplica al momento de evaluar. Distintos planes pueden tener equivalencias diferentes para la misma clasificación.

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| name | String | Ej: "Integradora", "Regular" — **no marcado como único en el doc de dominio** (a diferencia de `AcademicDivision.name`) |
| code | String | Único — ej: "INT", "REG" |
| status | Enum | ACTIVE, INACTIVE |

Gap confirmado (sesión 2026-07-15, ver `118-SISA-CLAUDE/docs/design/pendientes/2026-07-15-planes-form-wiring.md`, punto 3): documentado en el dominio, **cero implementación** en `118-SISA-BACK` — ni entidad, ni puerto, ni tabla. `Subject.classificationId` existe como campo pero es *"schema-only, sin validación de existencia"* (javadoc de `Subject.java`). Sin este catálogo, esa referencia no se puede validar de verdad.

`GradeScale`/`GradeScaleEntry` (unicidad `planId + classificationId`) depende de que este catálogo exista primero — queda fuera de este plan, es el siguiente servicio en la cola.

## 2. Patrón a clonar

Mismo template que `AcademicDivision`/`AcademicProgram` (arquitectura hexagonal, mismo módulo `academic_config`):

```
domain/model/SubjectClassification.java + ClassificationStatus.java (enum, o reusar DivisionStatus si el shape calza)
domain/port/in/{Create,Update,List,Get,ChangeStatus}SubjectClassificationUseCase.java
domain/port/out/SubjectClassificationRepository.java
domain/service/{...}UseCaseImpl.java
infrastructure/persistence/SubjectClassificationJpaRepository.java + ...RepositoryAdapter.java
infrastructure/web/SubjectClassificationController.java + dto/*
shared/exception/{DuplicateClassificationCodeException, ClassificationNotFoundException}.java
```

Sin Flyway en este repo — `spring.jpa.hibernate.ddl-auto=create-drop` sobre H2 in-memory (`application.properties`), la tabla sale sola de las anotaciones JPA de la entidad.

Seguridad: mismo patrón de matchers por verbo en `identity/infrastructure/security/SecurityFilterConfig.java` (líneas 83-94), roles `ADMIN` + `SERVICIOS_ESCOLARES` en todos los verbos, igual que `/divisions` y `/programs`. Se agregan 4 líneas nuevas para `/subject-classifications`.

## 3. Entrega servicio por servicio (pedido explícito de José: no un plan/PR gigante — un endpoint, probás, seguimos)

| # | Servicio | Endpoint | Incluye |
|---|---|---|---|
| **1** | **Consulta (List)** | `GET /subject-classifications` | Fundacional: modelo de dominio, tabla (JPA), repositorio (puerto + adapter), matcher de seguridad GET, y el caso de uso List. Sin esto no hay nada que listar — es la base de la que cuelgan los demás. |
| 2 | Registro (Create) | `POST /subject-classifications` | CreateUseCase + validación de `code` único + matcher POST |
| 3 | Detalle (Get by id) | `GET /subject-classifications/{id}` | GetUseCase + 404 si no existe |
| 4 | Actualización (Update) | `PUT /subject-classifications/{id}` | UpdateUseCase + revalidación de `code` único |
| 5 | Cambio de estado | `PATCH /subject-classifications/{id}/status` | ChangeStatusUseCase (ACTIVE ⇄ INACTIVE) |

Cada número = su propia implementación, su propia revisión adversarial, su propio commit. José prueba (Postman/H2 console) antes de pasar al siguiente.

## 4. Fuera de alcance de este plan

- `GradeScale`/`GradeScaleEntry` (depende de que este catálogo exista).
- Wiring de frontend (pantalla de gestión de Clasificaciones en `118-SISA-FRONT` — hoy es dropdown hardcodeado en `MateriasForm.tsx`/`EscalaForm.tsx`). Se planifica aparte una vez que el backend esté completo.
- Validación real de `Subject.classificationId` contra este catálogo (haría falta tocar el aggregate `Subject`, que ya está en producción/mergeado) — decisión de PO separada.

## Execution Log

### Fase 1 — Consulta (List) — COMPLETADA (2026-07-15)

Implementado el aggregate `SubjectClassification` (paquete `mx.edu.utez.sisa.academic_config`) siguiendo exactamente el patrón de `AcademicDivision`, adaptado al shape más simple del catálogo (sin `description`, sin `directorPersonId`, sin `programCount`).

**Archivos creados:**
- `domain/model/SubjectClassification.java` — entidad JPA, `name` NO único (a diferencia de `AcademicDivision.name`), `code` único. Sin `activate()`/`deactivate()`/`updateDetails()` todavía (YAGNI — fases futuras).
- `domain/model/ClassificationStatus.java` — enum propio (ACTIVE, INACTIVE), no reutiliza `DivisionStatus`/`ProgramStatus`.
- `domain/port/in/ListSubjectClassificationsUseCase.java` — query/result/summary records, misma convención de paginación que `ListAcademicDivisionsUseCase` (DEFAULT_PAGE_SIZE=20, MAX_PAGE_SIZE=100).
- `domain/port/out/SubjectClassificationRepository.java` — SOLO el método `search(...)` (YAGNI: sin `save`/`findById`/`findByCode`, se agregan en la fase de Create).
- `domain/service/ListSubjectClassificationsUseCaseImpl.java`.
- `infrastructure/persistence/SubjectClassificationJpaRepository.java` + `SubjectClassificationRepositoryAdapter.java` — tabla `subject_classification` generada por `ddl-auto=create-drop`.
- `infrastructure/web/SubjectClassificationController.java` + `dto/SubjectClassificationListResponse.java` + `dto/SubjectClassificationListItemResponse.java` — SOLO `GET /subject-classifications`.
- Tests: `SubjectClassificationTest`, `ListSubjectClassificationsUseCaseImplTest`, `SubjectClassificationRepositoryAdapterSearchIT`, `SubjectClassificationControllerTest`.

**Archivos modificados:**
- `identity/infrastructure/security/SecurityFilterConfig.java` — agregado matcher GET-only para `/subject-classifications`, `/subject-classifications/**` (roles ADMIN, SERVICIOS_ESCOLARES), sin matchers de POST/PUT/PATCH todavía.
- `academic_config/infrastructure/config/UseCaseConfig.java` — registrado el bean `listSubjectClassificationsUseCase` (composition root del módulo; sin este bean el contexto de Spring no levanta — ver gotcha abajo).

**Decisión de diseño no obvia:** el adapter ordena por `name ASC, code ASC` (no solo `name ASC` como `AcademicDivision`) porque `name` NO es único en este aggregate — el desempate por `code` (que sí es único) garantiza paginación determinística entre páginas.

**Gotcha encontrado:** al clonar el patrón de `AcademicDivision` es fácil olvidar el paso de wiring en `UseCaseConfig.java` (composition root del módulo) — el controlador y el use case impl compilan bien de forma aislada, pero el contexto completo de Spring (`CoreApplicationTests`, `AuthenticationEntryPointTest`, `CorsConfigTest`) falla en tiempo de arranque con `NoSuchBeanDefinitionException` si el `@Bean` no se registra ahí. Los tests con `@WebMvcTest` (que mockean el use case) no detectan este problema porque no cargan el contexto completo — solo aparece corriendo `./mvnw test` sin filtro.

**Resultado de tests:** `./mvnw test` completo → 240 tests, 0 failures, 0 errors, BUILD SUCCESS. `./mvnw clean package -DskipTests` → BUILD SUCCESS.

**Revisión adversarial fresca (post-commit `fd13b0d`):** sin hallazgos de correctness, seguridad manual, alcance ni consistencia. Un IMPORTANTE: faltaba `SubjectClassificationControllerIT` (test de seguridad end-to-end con JWT real, mismo patrón que `AcademicDivisionControllerIT`/`AcademicProgramControllerIT`) — `SubjectClassificationControllerTest` es `@WebMvcTest` con `addFilters = false`, corre con la cadena de seguridad desactivada, así que el matcher nuevo no tenía cobertura automatizada real (se verificó manualmente que estaba bien escrito). Corregido: se agregó `SubjectClassificationControllerIT` (4 tests: ADMIN y SERVICIOS_ESCOLARES listan OK, DOCENTE 403, sin token 401), seedeando filas directo vía `SubjectClassificationJpaRepository.save` (no hay `POST` en esta fase). `./mvnw verify` completo (incluye todos los `*IT`) → 72 ITs + suite completa, 0 failures, BUILD SUCCESS.

### Fase 2 — Registro (Create) — COMPLETADA (2026-07-20)

Implementado `CreateSubjectClassificationUseCase` clonando exactamente el patrón de `CreateAcademicDivisionUseCase`, simplificado al shape de este catálogo: sin `description`, sin `directorPersonId`, y — a diferencia de Division — **sin validación de unicidad de `name`** (regla de negocio confirmada en `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` líneas 15-24: solo `code` es único en este aggregate).

**Archivos creados:**
- `domain/port/in/CreateSubjectClassificationUseCase.java` — `CreateClassificationCommand(name, code)` / `ClassificationResult(id, name, code, status)`.
- `domain/service/CreateSubjectClassificationUseCaseImpl.java` — valida solo `code` único vía `findByCode`, construye la entidad (constructor ya defaultea a `ACTIVE`, heredado de Fase 1), guarda y mapea a `ClassificationResult`.
- `shared/exception/DuplicateClassificationCodeException.java` — mismo patrón que `DuplicateDivisionCodeException`/`DuplicateProgramCodeException`.
- `infrastructure/web/dto/CreateSubjectClassificationRequest.java` (`@NotBlank name`, `@NotBlank code`) y `dto/SubjectClassificationResponse.java` (id, name, code, status).
- Tests: `CreateSubjectClassificationUseCaseImplTest` (3 tests: creación exitosa, rechazo de código duplicado, **acepta nombres duplicados explícitamente** — este último test documenta la regla de negocio que diferencia esta aggregate de `AcademicDivision`).

**Archivos modificados:**
- `domain/port/out/SubjectClassificationRepository.java` — agregado `save(SubjectClassification)` y `findByCode(String)` (case-insensitive, misma convención que `AcademicDivisionRepository`). `findById` sigue sin existir (YAGNI — se agrega en la fase de Get).
- `infrastructure/persistence/SubjectClassificationJpaRepository.java` — agregado `findByCodeIgnoreCase(String)`.
- `infrastructure/persistence/SubjectClassificationRepositoryAdapter.java` — implementa `save`/`findByCode` delegando al JPA repository.
- `infrastructure/web/SubjectClassificationController.java` — agregado `POST /subject-classifications` (201, body `SubjectClassificationResponse`).
- `infrastructure/config/UseCaseConfig.java` — registrado el bean `createSubjectClassificationUseCase` (composition root del módulo — ver gotcha de Fase 1, se aplicó desde el principio esta vez).
- `identity/infrastructure/security/SecurityFilterConfig.java` — agregado matcher `POST /subject-classifications` (roles `ADMIN`/`SERVICIOS_ESCOLARES`, mismo par que el matcher GET).
- `infrastructure/web/GlobalExceptionHandler.java` (academic_config) — agregado `@ExceptionHandler(DuplicateClassificationCodeException.class)` → 409 CONFLICT.
- Tests extendidos: `SubjectClassificationControllerTest` (+4 tests: 201 con body, 400 nombre en blanco, 400 código en blanco, 409 código duplicado). `SubjectClassificationControllerIT` (+5 tests: ADMIN puede crear, SERVICIOS_ESCOLARES puede crear, DOCENTE 403, sin token 401, código duplicado real end-to-end → 409). `SubjectClassificationRepositoryAdapterSearchIT` (+2 tests: `save` inserta fila nueva, `findByCode` case-insensitive).

**Decisión de diseño no obvia:** a diferencia de `CreateAcademicDivisionUseCaseImpl` (que valida `findByName` Y `findByCode`), este use case deliberadamente NO llama a ningún método de búsqueda por nombre — el port `SubjectClassificationRepository` ni siquiera declara un `findByName`, para que sea imposible añadir esa validación por accidente al clonar el patrón de Division sin leer la regla de negocio primero.

**Gotcha evitado (no repetido):** el wiring en `UseCaseConfig.java` (composition root) se hizo en el mismo commit que el resto de la Fase 2, no como corrección posterior — se aprendió de la Fase 1.

**Revisión adversarial fresca (post-implementación, pre-commit):** se verificó explícitamente cada punto pedido:
- `DuplicateClassificationCodeException` SÍ está wireada en `GlobalExceptionHandler` — confirmado con el test `createClassificationWithDuplicateCodeReturns409` (WebMvcTest) y `duplicateCodeReturns409` (IT real, dos POSTs con el mismo código, la segunda devuelve 409).
- `SubjectClassificationControllerTest` SÍ corre con `addFilters = false` (igual que Fase 1), por lo que el matcher `POST` nuevo en `SecurityFilterConfig` no tiene cobertura ahí — cobertura real viene de `SubjectClassificationControllerIT`, que sí usa la cadena de seguridad completa.
- `SubjectClassificationControllerIT` cubre los 4 casos pedidos sobre el endpoint POST real con JWT real: ADMIN succeeds (`adminCanCreate`), SERVICIOS_ESCOLARES succeeds (`serviciosEscolaresCanCreate`), DOCENTE → 403 (`otherRoleIsForbiddenOnCreate`), sin token → 401 (`unauthenticatedCreateReturns401`), más el caso de negocio `duplicateCodeReturns409`.
- Sin hallazgos adicionales de correctness, alcance ni consistencia con el patrón de `AcademicDivision`/`AcademicProgram`.

**Resultado de tests:** `./mvnw test` → 247 tests (antes 240 en Fase 1, +7 nuevos: 3 unit de use case + 4 de controller). `./mvnw verify` completo (incluye todos los `*IT`) → 247 unit + 79 IT = 326 tests totales, 0 failures, 0 errors, BUILD SUCCESS.

### Fase 3 — Detalle (Get by id) — COMPLETADA (2026-07-20)

Implementado `GetSubjectClassificationUseCase` clonando exactamente el patrón de `GetAcademicDivisionUseCase`: 404 si no existe, sin reglas de negocio adicionales (confirmado contra `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` líneas 15-24 — sin cambios respecto a Fases 1 y 2, y sin FK que valide contra este catálogo todavía).

**Archivos creados:**
- `domain/port/in/GetSubjectClassificationUseCase.java` — `getById(UUID id)`, reutiliza `CreateSubjectClassificationUseCase.ClassificationResult` (mismo shape que Create, misma convención que `GetAcademicDivisionUseCase` reutilizando `AcademicDivisionResult`).
- `domain/service/GetSubjectClassificationUseCaseImpl.java` — `findById` + `orElseThrow(ClassificationNotFoundException)`, delega el mapeo a `CreateSubjectClassificationUseCaseImpl.toResult` (mismo helper estático que usa Create).
- `shared/exception/ClassificationNotFoundException.java` — mismo patrón que `AcademicDivisionNotFoundException`.
- Tests: `GetSubjectClassificationUseCaseImplTest` (2 tests: encontrado, no encontrado).

**Archivos modificados:**
- `domain/port/out/SubjectClassificationRepository.java` — agregado `findById(UUID)` (antes YAGNI, como documentaba el javadoc de Fase 1/2).
- `infrastructure/persistence/SubjectClassificationRepositoryAdapter.java` — implementa `findById` delegando a `SubjectClassificationJpaRepository#findById` (heredado gratis de `JpaRepository`, ya lo usaban los tests de integración para sembrar filas desde Fase 1).
- `infrastructure/web/SubjectClassificationController.java` — agregado `GET /subject-classifications/{id}` (200 con body, reutiliza `SubjectClassificationResponse` — mismo shape que `ClassificationResult`).
- `infrastructure/config/UseCaseConfig.java` — registrado el bean `getSubjectClassificationUseCase` (composition root — se aplicó desde el principio, no como corrección posterior, siguiendo el gotcha aprendido en Fase 1).
- `infrastructure/web/GlobalExceptionHandler.java` (academic_config) — agregado `@ExceptionHandler(ClassificationNotFoundException.class)` → 404 NOT_FOUND.
- `identity/infrastructure/security/SecurityFilterConfig.java` — **sin matcher nuevo**: el matcher GET existente (`HttpMethod.GET, "/subject-classifications", "/subject-classifications/**"`, agregado en Fase 1) ya cubre `/subject-classifications/{id}` por el wildcard `/**`. Solo se actualizó el javadoc de la clase para dejar constancia explícita de que Fase 3 no necesitó una línea nueva (a diferencia de Fase 2 que sí agregó su propio matcher POST).
- Tests extendidos: `SubjectClassificationControllerTest` (+2 tests: 200 con body si existe, 404 si no existe — `@WebMvcTest` con `addFilters = false`, confirmado explícitamente en la revisión adversarial). `SubjectClassificationControllerIT` (+5 tests: ADMIN y SERVICIOS_ESCOLARES obtienen 200, DOCENTE 403, sin token 401, id desconocido → 404 real). `SubjectClassificationRepositoryAdapterSearchIT` (+2 tests: `findById` retorna la entidad si existe, vacío si no).

**Decisión de diseño no obvia:** `GetSubjectClassificationUseCase.ClassificationResult` es el mismo record que ya usa `CreateSubjectClassificationUseCase` — no se creó un DTO nuevo porque el shape (id, name, code, status) es idéntico y no hay campos exclusivos de "detalle" (sin `description`, sin relaciones anidadas, a diferencia de `AcademicDivision`). El controlador tampoco necesitó un nuevo response DTO: `SubjectClassificationResponse` (de Fase 2) se reutiliza tal cual.

**Gotcha evitado (no repetido):** el wiring en `UseCaseConfig.java` se hizo en el mismo commit que el resto de la Fase 3, no como corrección posterior — tercera vez seguida que se evita este gotcha desde que se documentó en Fase 1.

**Revisión adversarial fresca (post-implementación, pre-commit):** se verificó explícitamente cada punto pedido:
- `ClassificationNotFoundException` SÍ está wireada en `GlobalExceptionHandler` — confirmado con `getClassificationReturns404WhenNotFound` (WebMvcTest) y `getByIdWithUnknownIdReturns404` (IT real).
- `SubjectClassificationControllerTest` SÍ corre con `addFilters = false` (confirmado leyendo la anotación de clase directamente) — no puede validar el matcher de seguridad; esa cobertura viene solo de `SubjectClassificationControllerIT`.
- `SubjectClassificationControllerIT` cubre los 4 casos pedidos sobre el endpoint GET/{id} real con JWT real: ADMIN succeeds (`adminCanGetById`), SERVICIOS_ESCOLARES succeeds (`serviciosEscolaresCanGetById`, extra respecto a lo pedido pero consistente con el patrón de Fases 1-2), DOCENTE → 403 (`otherRoleIsForbiddenOnGetById`), sin token → 401 (`unauthenticatedGetByIdReturns401`), id desconocido → 404 (`getByIdWithUnknownIdReturns404`).
- Sin hallazgos adicionales de correctness, alcance ni consistencia con el patrón de `AcademicDivision`.

**Resultado de tests:** `./mvnw test` → 251 tests (antes 247 en Fase 2, +4 nuevos: 2 unit de use case + 2 de controller). `./mvnw verify` completo (incluye todos los `*IT`) → 251 unit + 86 IT = 337 tests totales, 0 failures, 0 errors, BUILD SUCCESS.

### Fase 4 — Actualización (Update) — COMPLETADA (2026-07-20)

Implementado `UpdateSubjectClassificationUseCase` clonando exactamente el patrón de `UpdateAcademicDivisionUseCase`: revalidación de `code` único EXCLUYENDO la propia fila (self-update con el mismo código debe tener éxito), sin regla de unicidad de `name` (sin cambios respecto a Fases 1-3), y `status` deliberadamente fuera de este comando (eso es Fase 5, `ChangeStatus`, un PATCH separado).

**Archivos creados:**
- `domain/port/in/UpdateSubjectClassificationUseCase.java` — `UpdateClassificationCommand(classificationId, name, code)`, reutiliza `ClassificationResult` de `CreateSubjectClassificationUseCase` (mismo shape, misma convención que `GetSubjectClassificationUseCase` en Fase 3).
- `domain/service/UpdateSubjectClassificationUseCaseImpl.java` — `findById` + 404, revalida `findByCode` filtrando por id propio (`!found.getId().equals(classification.getId())`) antes de lanzar `DuplicateClassificationCodeException`, aplica `classification.updateDetails(...)`, guarda y mapea con el helper estático de Create.
- `infrastructure/web/dto/UpdateSubjectClassificationRequest.java` (`@NotBlank name`, `@NotBlank code`, sin `status`).
- Tests: `UpdateSubjectClassificationUseCaseImplTest` (4 tests: actualización exitosa con status sin cambios, mantiene su propio código actual, rechaza colisión de código con otro registro, rechaza id desconocido).

**Archivos modificados:**
- `domain/model/SubjectClassification.java` — agregado `updateDetails(String name, String code)` (sin tocar `status`, misma separación que `AcademicDivision#updateDetails`). Javadoc de clase actualizado: ya no dice "Update... no implementado" (quedó desactualizado desde Fase 2).
- `infrastructure/web/SubjectClassificationController.java` — agregado `PUT /subject-classifications/{id}` (200 con body, 404 si no existe, 409 si el código colisiona con otro registro).
- `infrastructure/config/UseCaseConfig.java` — registrado el bean `updateSubjectClassificationUseCase` (composition root — se aplicó desde el principio, cuarta vez seguida evitando el gotcha de Fase 1).
- `identity/infrastructure/security/SecurityFilterConfig.java` — agregado matcher `PUT /subject-classifications/**` (roles `ADMIN`/`SERVICIOS_ESCOLARES`). Confirmado leyendo el archivo real (no asumido): el matcher GET existente es verb-scoped (`HttpMethod.GET, ...`), NO cubre otros verbos pese al wildcard `/**` en el path — a diferencia de lo que podría sugerir el patrón de Fase 3 (que sí reutilizó el GET wildcard porque también era GET). PUT necesitaba su propia línea, tal como anticipaba el enunciado de esta fase.
- Tests extendidos: `SubjectClassificationControllerTest` (+5 tests: 200 con body, 400 nombre en blanco, 400 código en blanco, 409 código duplicado, 404 no encontrado — `@WebMvcTest` con `addFilters = false`, confirmado que se mantiene así). `SubjectClassificationControllerIT` (+7 tests: ADMIN puede actualizar, SERVICIOS_ESCOLARES puede actualizar, DOCENTE 403, sin token 401, id desconocido 404, código colisionando con OTRO registro 409, código sin cambios en el PROPIO registro 200 — este último cubre explícitamente la dirección "self-update no es falso conflicto").

**Decisión de diseño no obvia:** `GlobalExceptionHandler` NO necesitó ningún cambio — `DuplicateClassificationCodeException` (409) y `ClassificationNotFoundException` (404) ya estaban wireados desde las Fases 2 y 3 respectivamente, y este use case reutiliza las mismas excepciones (no se crearon excepciones nuevas "Update-specific").

**Gotcha evitado (no repetido):** el wiring en `UseCaseConfig.java` se hizo en el mismo commit que el resto de la Fase 4 — cuarta vez seguida sin repetir el gotcha de Fase 1.

**Revisión adversarial fresca (post-implementación, pre-commit):** se verificó explícitamente cada punto pedido:
- El caso self-update-mismo-código SÍ está testeado en ambas capas, no solo el caso de rechazo con código diferente: `updateClassification_allowsKeepingItsOwnCurrentCode` (unit, mockeando `findByCode` para que devuelva la propia entidad) y `updateWithUnchangedCodeOnOwnRecordSucceeds` (IT real, un solo registro, PUT con su propio código sin cambios → 200).
- `SubjectClassificationControllerTest` SÍ sigue corriendo con `addFilters = false` (confirmado leyendo la anotación de clase directamente) — el matcher `PUT` nuevo no tiene cobertura ahí; esa cobertura viene solo de `SubjectClassificationControllerIT`.
- `SubjectClassificationControllerIT` cubre los 5 casos pedidos sobre el endpoint PUT real con JWT real: ADMIN succeeds (`adminCanUpdate`), SERVICIOS_ESCOLARES succeeds (`serviciosEscolaresCanUpdate`), DOCENTE → 403 (`otherRoleIsForbiddenOnUpdate`), sin token → 401 (`unauthenticatedUpdateReturns401`), id desconocido → 404 (`updateWithUnknownIdReturns404`), más el caso de negocio código-colisiona-con-otro-registro → 409 (`updateWithCodeCollidingWithAnotherRecordReturns409`) y el caso complementario código-propio-sin-cambios → 200 (`updateWithUnchangedCodeOnOwnRecordSucceeds`).
- Sin hallazgos adicionales de correctness, alcance ni consistencia con el patrón de `AcademicDivision`.

**Resultado de tests:** `./mvnw test` → 260 tests (antes 251 en Fase 3, +9 nuevos: 4 unit de use case + 5 de controller). `./mvnw verify` completo (incluye todos los `*IT`) → 260 unit + 93 IT = 353 tests totales, 0 failures, 0 errors, BUILD SUCCESS.

### Fase 5 — Cambio de estado — COMPLETADA (2026-07-20)

Implementado `ChangeSubjectClassificationStatusUseCase` clonando exactamente el patrón de `ChangeAcademicDivisionStatusUseCase`: interactor único parametrizado por status destino (`ACTIVE`/`INACTIVE`), transiciones idempotentes (aplicar el mismo status que ya tiene no es error), y `PATCH /subject-classifications/{id}/status` con el mismo shape de body (`{ "status": "ACTIVE" | "INACTIVE" }`). Esta era la ÚLTIMA fase del plan — con ella el CRUD completo de `SubjectClassification` queda implementado.

**Archivos creados:**
- `domain/port/in/ChangeSubjectClassificationStatusUseCase.java` — `ChangeStatusCommand(callerId, classificationId, target)`, reutiliza `ClassificationResult` de `CreateSubjectClassificationUseCase` (mismo shape que Get/Update), mismo patrón que `ChangeAcademicDivisionStatusUseCase`/`ChangeAcademicProgramStatusUseCase` (incluyendo el `callerId` reservado para auditoría futura, no consultado todavía).
- `domain/service/ChangeSubjectClassificationStatusUseCaseImpl.java` — `findById` + 404, aplica `activate()`/`deactivate()` según el target, guarda y mapea con el helper estático de Create.
- `infrastructure/web/dto/ChangeClassificationStatusRequest.java` (`@NotNull status`).
- Tests: `ChangeSubjectClassificationStatusUseCaseImplTest` (4 tests: desactiva una activa, reactiva una inactiva, idempotente cuando el target coincide con el status actual, rechaza id desconocido).

**Archivos modificados:**
- `domain/model/SubjectClassification.java` — agregados `activate()`/`deactivate()` (idempotentes, mismo comportamiento que `AcademicDivision#activate`/`#deactivate` — deliberadamente diferidos como YAGNI desde la Fase 1 hasta esta fase, según lo anotado en su momento). Javadoc de clase actualizado: ya no dice "status-transition behavior... pending", ahora indica que las 5 fases están completas.
- `infrastructure/web/SubjectClassificationController.java` — agregado `PATCH /subject-classifications/{id}/status` (200 con body, 404 si no existe) y el helper `currentUserId()` (extrae el id del JWT vía `SecurityContextHolder`, mismo mecanismo que `AcademicDivisionController#currentUserId`, reimplementado aquí porque `identity.AuthenticatedCaller` es package-private).
- `infrastructure/config/UseCaseConfig.java` — registrado el bean `changeSubjectClassificationStatusUseCase` (composition root — se aplicó desde el principio, quinta vez seguida evitando el gotcha de Fase 1).
- `identity/infrastructure/security/SecurityFilterConfig.java` — agregado matcher `PATCH /subject-classifications/**` (roles `ADMIN`/`SERVICIOS_ESCOLARES`), confirmado leyendo el archivo real antes de asumir: igual que PUT en Fase 4, el matcher GET existente es verb-scoped y no cubre PATCH pese al wildcard `/**` en el path, así que hacía falta su propia línea — tal como anticipaba el enunciado de esta fase. Javadoc de clase actualizado: el CRUD de `/subject-classifications` queda completo con las 4 líneas de matcher (GET/POST/PUT/PATCH).
- Tests extendidos: `SubjectClassificationTest` (+4 tests: activate/deactivate y sus casos idempotentes). `SubjectClassificationControllerTest` (+2 tests: 200 con status actualizado, 404 no encontrado — `@WebMvcTest` con `addFilters = false`, se agregó el mismo setup/teardown de `SecurityContextHolder` con `callerId` que usa `AcademicDivisionControllerTest`, porque el controlador ahora lee `currentUserId()` de la autenticación simulada). `SubjectClassificationControllerIT` (+5 tests: ADMIN puede cambiar estado, SERVICIOS_ESCOLARES puede cambiar estado, DOCENTE 403, sin token 401, id desconocido 404).

**Decisión de diseño no obvia:** igual que Division/Program, el `ChangeStatusCommand` incluye `callerId` reservado para auditoría futura pero no consultado por el use case todavía — se clonó la forma exacta del patrón existente en vez de omitir el campo, para no divergir de la convención ya establecida en este módulo si en el futuro se agrega un sink de auditoría.

**Gotcha evitado (no repetido):** el wiring en `UseCaseConfig.java` se hizo en el mismo commit que el resto de la Fase 5 — quinta vez seguida sin repetir el gotcha de Fase 1.

**Revisión adversarial fresca (post-implementación, pre-commit):** se verificó explícitamente cada punto pedido:
- El nuevo matcher de seguridad SÍ tiene efecto real: `otherRoleIsForbiddenOnChangeStatus` (IT, DOCENTE → 403) es la prueba que importa — si el matcher PATCH faltara, la petición caería en la regla `anyRequest().authenticated()` (que solo exige estar autenticado, no el rol correcto) y DOCENTE recibiría 200 en vez de 403. Como el test espera y obtiene 403, el matcher está confirmado funcionando. `SubjectClassificationControllerTest` (WebMvcTest, `addFilters = false`) NO puede probar esto — solo valida el contrato HTTP del controlador con la cadena de seguridad desactivada.
- `unauthenticatedChangeStatusReturns401` (IT) confirma que la cadena JWT sigue exigiendo autenticación en este endpoint.
- Los 5 escenarios pedidos están cubiertos en el IT: ADMIN succeeds (`adminCanChangeStatus`), SERVICIOS_ESCOLARES succeeds (`serviciosEscolaresCanChangeStatus`), DOCENTE → 403 (`otherRoleIsForbiddenOnChangeStatus`), sin token → 401 (`unauthenticatedChangeStatusReturns401`), id desconocido → 404 (`changeStatusWithUnknownIdReturns404`).
- Sin hallazgos adicionales de correctness, alcance ni consistencia con el patrón de `AcademicDivision`/`AcademicProgram`.

**Resultado de tests:** `./mvnw test` → 270 tests (antes 260 en Fase 4, +10 nuevos: 4 de modelo de dominio + 4 unit de use case + 2 de controller). `./mvnw verify` completo (incluye todos los `*IT`) → 270 unit + 98 IT = 368 tests totales, 0 failures, 0 errors, BUILD SUCCESS.

## Plan CERRADO (2026-07-20)

Las 5 fases de este plan están completas: Consulta (List), Registro (Create), Detalle (Get by id), Actualización (Update) y Cambio de estado (ChangeStatus). El aggregate `SubjectClassification` tiene su CRUD completo en `118-SISA-BACK`, siguiendo exactamente el patrón hexagonal ya establecido por `AcademicDivision`/`AcademicProgram`/`AcademicPlan` en el módulo `academic_config`. Ninguna regla de negocio cambió entre fases (confirmado contra `118-SISA-CLAUDE/docs/design/dominio/02-config-academica.md` líneas 15-24 en cada fase). Lo que queda explícitamente **fuera de alcance** (ver sección 4 arriba) sigue fuera: `GradeScale`/`GradeScaleEntry`, el wiring de frontend, y la validación real de `Subject.classificationId` contra este catálogo — son decisiones de PO separadas, no deuda de este plan.
