# Plan: Persons + User Management Completion (identity)

## 1. Contexto (docs-first)

Fuentes: `118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md` (líneas 11-57, `Person`), `docs/requirements/00-TRANSVERSALES.md` (líneas 22-58, 71-100, Autenticación y Roles), `118-SISA-BACK/openspec/changes/archive/2026-07-05-identity-module/design.md` (líneas 156-164, decisión "Person creation is out of scope").

- `Person` (shared kernel) tiene ~25 campos en su forma completa (dirección, perfil de salud, antecedentes académicos, etc.), pero el doc dice explícitamente que esos campos nacen en el **formulario de registro de candidato de Admisión** — `Person` en `identity` hoy es deliberadamente un slice mínimo de 5 campos (curp, firstName, lastName1, lastName2, institutionalEmail), "lo que Identity necesita", el resto lo extenderá Admisión/Inscripciones cuando existan.
- `00-TRANSVERSALES.md` no documenta ningún flujo de alta manual de personal interno (Docente, Gestor Académico, etc.) — línea 34: "No se descarta extender a personal interno en el futuro (**TBD**)". Es una pregunta abierta, no una omisión de implementación.
- La decisión de diseño original de `identity` (2026-07-05) dice textualmente: "Person creation is out of scope — load-only reference... runtime Persons come only from the ADMIN seed." Hoy la única forma de crear un `Person` son `AdminSeedRunner`/`ServiciosEscolaresSeedRunner`, hardcodeados.

**Decisión de José (2026-07-28)**: sí hace falta un flujo de alta manual de personal interno, separado del flujo de candidatos de Admisión. También confirmó que Jefatura/Asistente de Estadías son roles de **scope global** (no de división) — coincide con lo que el backend ya implementa (`DIVISION_SCOPED_ROLES` = GESTOR_ACADEMICO, DIRECTOR_DIVISION, COORDINACION_ESTADIAS_DIVISION), el error estaba en el mock del frontend, no en el backend.

## 2. Comparación docs vs. implementación actual

- `Person`: solo `findById`/`save` en el repositorio (ambos "load-only", `save` documentado como uso exclusivo de los seed runners). Sin caso de uso de creación, sin búsqueda.
- `User`: `UserController` solo expone 3 endpoints — `POST /users`, `POST /users/{userId}/roles`, `GET /users`. **No existe** `GET /users/{id}`, ni desbloqueo de cuenta, ni revocar rol.
- `UserRole`: entidad de datos pura, sin comportamiento propio; `AssignRoleUseCaseImpl` es el único escritor. No hay ningún camino de borrado/revocación en ninguna capa.
- `User.registerFailedLogin()` bloquea la cuenta (`status = LOCKED`) al tercer intento fallido, pero no existe ningún método ni endpoint para revertirlo — hoy una cuenta bloqueada queda bloqueada para siempre.

## 3. Alcance confirmado (José, 2026-07-28) — las 5 piezas juntas

1. `POST /persons` — crear persona (personal interno).
2. `GET /persons` — buscar personas existentes (paginado).
3. `GET /users/{id}` — detalle completo de usuario.
4. `DELETE /users/{userId}/roles/{userRoleId}` — revocar rol.
5. `PATCH /users/{id}/unlock` — desbloquear cuenta.

## 4. Diseño técnico por pieza

### 4.1 `CreatePersonUseCase` / `POST /persons`
- Command: `{curp, firstName, lastName1, lastName2 (nullable), institutionalEmail}`.
- `institutionalEmail` es **obligatorio en este endpoint** (aunque la columna lo permite null a nivel entidad) — sin él no se puede crear después un `User` (`MissingInstitutionalEmailException` ya existente), así que no tiene sentido permitir un Person sin correo desde este flujo.
- Nuevos métodos en `PersonRepository`: `findByCurp(String)`, `findByInstitutionalEmail(String)` — chequeados antes del insert (mismo patrón de unicidad-por-consulta que el resto del módulo, no captura de excepción SQL).
- Nuevas excepciones: `DuplicateCurpException` (409), `DuplicateInstitutionalEmailException` (409).
- Response: `PersonResponse {id, curp, firstName, lastName1, lastName2, institutionalEmail}`.
- Seguridad: `ADMIN` únicamente (mismo nivel que crear usuarios).

### 4.2 `SearchPersonsUseCase` / `GET /persons`
- Query params: `search` (opcional, contra curp/nombre completo/correo), `page`, `size`.
- Nuevo `PersonRepository.search(PersonSearchCriteria)` → `PersonSearchPage`, mismo patrón record que `UserRepository.UserSearchCriteria`/`UserSearchPage` (mismo archivo, para consistencia intra-módulo).
- Cada item incluye `hasUser: boolean` (resuelto vía `userRepository.findByPersonId(...).isPresent()`), para que el frontend pueda distinguir personas que ya tienen cuenta.
- Seguridad: `ADMIN` + `SERVICIOS_ESCOLARES` (mismo nivel que `GET /users`).

### 4.3 `GetUserUseCase` / `GET /users/{id}`
- Carga `User` + `Person` (findById de ambos) + roles del usuario.
- Response: `UserDetailResponse {userId, personId, fullName, username, status, mustChangePassword, lastLoginAt, createdAt, roles: [{userRoleId, roleType, divisionId}]}`.
- Incluye `userRoleId` en cada rol — **no expuesto hoy** en `GET /users` (el listado solo trae `{roleType, divisionId}`) y necesario para poder revocar un rol específico desde el detalle.
- Seguridad: `ADMIN` + `SERVICIOS_ESCOLARES`.

### 4.4 `RevokeRoleUseCase` / `DELETE /users/{userId}/roles/{userRoleId}`
- Carga el `UserRole`, valida que pertenezca al `userId` de la URL (evita revocar el rol de otro usuario adivinando un id) → si no existe o no coincide, `UserRoleNotFoundException` (404).
- Sin regla de "no te quedes sin roles" ni "no te quedes sin ningún ADMIN en el sistema" — no está documentado en ningún lado; se mantiene minimalista, se agrega después si hace falta.
- Seguridad: `ADMIN` únicamente (mismo nivel que asignar rol).

### 4.5 `UnlockUserUseCase` / `PATCH /users/{id}/unlock`
- Nuevo método de dominio `User.unlock()`: `status = ACTIVE`, `failedLoginAttempts = 0` — idempotente (no-op si ya está `ACTIVE`), mismo criterio que `Generation.activate()`/`Group.open()`.
- Sin body en el request.
- Seguridad: `ADMIN` únicamente.

## 5. Fuera de alcance (explícito)

- Reseteo forzado de contraseña por un admin (distinto del self-service `POST /auth/change-password`) — no se pidió, se agrega en una pasada futura si aparece la necesidad real.
- Editar los datos de una Persona ya creada (corregir nombre/CURP) — no se pidió.
- El shape completo de 25 campos de `Person` del shared-kernel — se mantiene el slice mínimo de 5 campos ya establecido; el resto lo extenderá Admisión cuando se construya ese módulo.

## 6.5 Frontend (confirmado con José, 2026-07-28 — detalle en `118-SISA-FRONT/docs/plans/2026-07-28-usuarios-wiring.md`)

- "Registrar Usuario" pasa a ser un Wizard de 2 pasos (Persona → Cuenta), no un form plano — reutiliza `Wizard.tsx` (mismo componente ya usado en Admisión/Inscripciones).
- El modo "Editar" de `UsuariosForm.tsx` se elimina — no hay ningún campo de `User` editable vía `PUT` (no existe ese endpoint a propósito).
- `UsuarioDetalle.tsx` muestra la info de la `Person` asociada + gestión de roles (asignar/revocar) + botón "Desbloquear cuenta" (solo visible si `status === 'LOCKED'`).

## 6. Patrón a clonar

Mismo criterio hexagonal ya usado en todo `identity`/`academic_config`: `domain/port/in` + `domain/service` + `domain/port/out` + `infrastructure/persistence` + `infrastructure/web` + tests. Nuevo `PersonController.java` bajo `identity/infrastructure/web/`. Los 3 casos de uso nuevos de `User` (Get/RevokeRole/Unlock) se agregan a `UserController.java` existente.

## Execution Log

### Implementado (2026-07-28)

**Estado**: las 5 piezas completas end-to-end (dominio, casos de uso, persistencia, web, seguridad, tests). Sin desviaciones de fondo respecto al diseño técnico ya resuelto en las secciones 3/4 — la única ambigüedad real encontrada durante la implementación fue si los 5 casos de uso nuevos debían exigir el gate de `caller.assertCanOperate()` (el plan no lo menciona explícitamente para Person/GetUser/RevokeRole/Unlock). Se resolvió clonando el patrón exacto de `CreateUserUseCaseImpl`/`AssignRoleUseCaseImpl`/`ListUsersUseCaseImpl` — los tres casos de uso ADMIN-facing ya existentes en el módulo siempre cargan al caller vía `UserRepository#findById` y llaman `assertCanOperate()` antes de cualquier otra validación — por consistencia intra-módulo, ya que el plan no documenta ninguna excepción a esa regla para los endpoints nuevos.

**Decisiones técnicas durante la implementación**:

1. **`User.unlock()` usa asignación incondicional, no un guard `if`** — mismo criterio exacto que `Generation#activate`/`Group#open` en `academic_config` (confirmados por lectura directa: ninguno de los dos tiene un `if` de guarda, la "idempotencia" documentada es sobre el estado final tras repetir la llamada, no sobre saltarse la ejecución). `unlock()` siempre fija `status = ACTIVE`, `failedLoginAttempts = 0` y actualiza `updatedAt`, incluso si la cuenta ya estaba `ACTIVE`.
2. **`PersonRepository.search`/`PersonSearchCriteria`/`PersonSearchPage` NO incluyen `hasUser`** — el plan (4.2) especifica que `hasUser` se resuelve en `ListPersonsUseCaseImpl` vía `userRepository.findByPersonId(...).isPresent()`, un lookup por fila (mismo costo ya aceptado por `ListUsersUseCaseImpl` con sus roles por página). El out-port de `Person` solo devuelve las filas `Person` crudas.
3. **`RevokeRoleUseCaseImpl` NO valida que el `userId` de la URL exista como `User` real** — solo carga el `UserRole` por `userRoleId` y compara su `getUserId()` contra el `userId` de la URL; un `userId` inexistente y un `userId` real-pero-no-dueño producen el mismo 404 (`UserRoleNotFoundException`), exactamente la ambigüedad que el plan pide preservar ("evita revocar el rol de otro usuario adivinando un id") — no se inventó una validación adicional no pedida.
4. **`GET /users` matcher extendido a `"/users", "/users/**"`** en vez de agregar una línea nueva — cubre `GET /users/{id}` sin necesitar un segundo matcher GET, porque Spring Security evalúa por orden de declaración y el patrón ya cubre cualquier ruta bajo `/users`. Las otras 3 rutas nuevas (`DELETE .../roles/{userRoleId}`, `PATCH .../unlock`, y las existentes `POST /users`/`POST .../roles`) no son GET, así que siguen cayendo en el matcher blanket `/users/**` (ADMIN-only) sin cambios.
5. **`UserRoleJpaRepository` no requirió cambios** — ya extendía `JpaRepository<UserRole, UUID>`, así que `findById`/`delete` (necesarios para `RevokeRoleUseCaseImpl`) ya estaban heredados; solo se expusieron a través del out-port `UserRoleRepository` y del adapter.
6. **IT tests de `/persons` y de los 3 endpoints nuevos de `/users` NO pueden reusar el patrón `tokenFor()` de `GenerationControllerIT`** (que firma un JWT con un `UUID.randomUUID()` como subject sin fila real de respaldo) — a diferencia de `academic_config`, todo caso de uso ADMIN-facing de `identity` resuelve el caller vía `UserRepository#findById(callerId)` y lanza `UserNotFoundException`/`MustChangePasswordException` si no existe o no ha cambiado su password inicial. Se implementó un `tokenFor(RoleType)` propio en `PersonControllerIT`/`UserManagementControllerIT` que crea un `Person`+`User` real (ya con `changePassword` aplicado para limpiar `mustChangePassword`), le asigna el rol vía `userRoleRepository.save(new UserRole(...))` directo (sin pasar por `AssignRoleUseCase`, ya que no hace falta duplicar esa validación aquí), y firma el JWT con el id real de ese `User`.

**Archivos creados**:
- Excepciones: `identity/shared/exception/DuplicateCurpException.java`, `DuplicateInstitutionalEmailException.java`, `UserRoleNotFoundException.java`.
- Puertos-in: `CreatePersonUseCase`, `ListPersonsUseCase`, `GetUserUseCase`, `RevokeRoleUseCase`, `UnlockUserUseCase` (+ 5 impls en `domain/service/`).
- Web: `identity/infrastructure/web/PersonController.java` + 6 DTOs (`CreatePersonRequest`, `PersonResponse`, `PersonListItemResponse`, `PersonListResponse`, `UserDetailResponse`, `UnlockUserResponse`).
- Tests unitarios: `CreatePersonUseCaseImplTest` (5), `ListPersonsUseCaseImplTest` (8), `GetUserUseCaseImplTest` (5), `RevokeRoleUseCaseImplTest` (5), `UnlockUserUseCaseImplTest` (4), `PersonControllerTest` (8).
- Tests de integración: `PersonRepositoryAdapterSearchIT` (7), `PersonControllerIT` (12), `UserManagementControllerIT` (17).

**Archivos modificados**:
- Dominio: `identity/domain/model/User.java` (+ `unlock()`).
- Puertos-out: `PersonRepository.java` (+ `findByCurp`, `findByInstitutionalEmail`, `search`/`PersonSearchCriteria`/`PersonSearchPage`), `UserRoleRepository.java` (+ `findById`, `delete`).
- Persistencia: `PersonJpaRepository.java` (+ derived finders + `search` con `@Query`/`countQuery`), `PersonRepositoryAdapter.java`, `UserRoleRepositoryAdapter.java` (+ `findById`, `delete`).
- Web: `UserController.java` (+ 3 endpoints: `GET /users/{id}`, `DELETE /users/{userId}/roles/{userRoleId}`, `PATCH /users/{id}/unlock`), `GlobalExceptionHandler.java` (+ 3 mappings agrupados en los handlers existentes de 409/404).
- Seguridad: `SecurityFilterConfig.java` (+ matchers `/persons` GET/POST, `GET /users` extendido a `/users/**`).
- Wiring: `UseCaseConfig.java` (+ 5 beans nuevos).
- Tests existentes extendidos: `UserTest.java` (+2 para `unlock`), `GlobalExceptionHandlerTest.java` (+3), `UserControllerTest.java` (+6 para los 3 endpoints nuevos).

**Resultados de tests**: baseline documentado por la sesión anterior (Group, 2026-07-27): 491 unit / 203 IT. Final tras esta sesión: **506 unit / 239 IT**, 0 failures/errors (`./mvnw verify`, `JAVA_HOME=C:\Users\JoseNarvaez\.jdks\corretto-21.0.10`).
