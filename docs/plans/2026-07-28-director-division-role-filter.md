# Plan: Director de División acoplado al rol `DIRECTOR_DIVISION`

## 1. Contexto (docs-first)

`docs/design/dominio/02-config-academica.md` (línea 38): `AcademicDivision.directorPersonId` es FK → `Person`, nullable — "la creación debe poder completarse sin director asignado". `CreateAcademicDivisionUseCaseImpl.java:38` confirma que la única validación hoy es `personLookupPort.existsById(...)` — no exige que esa Persona tenga ningún rol.

**Decisión de José (2026-07-28)**: el buscador de personas en el formulario de División debe mostrar **solo** personas que ya tienen asignado el rol `DIRECTOR_DIVISION` (`docs/design/dominio/01-identidad.md`, `UserRole`) con scope a esa división específica.

**Implicancia de secuencia (confirmada con José)**: como `UserRole.divisionId` requiere una división ya existente, no se le puede asignar el rol a nadie antes de crear la división → el campo Director **desaparece de "Registrar División"** y solo existe en **"Editar División"** (flujo: crear división sin director → registrar/buscar la persona en Usuarios → asignarle el rol `DIRECTOR_DIVISION` con scope a esa división → recién ahí editar la división y elegirla).

## 2. Alcance

Extender `GET /users` (identity, ya existente) con un filtro opcional `divisionId`, combinable con el filtro `role` ya existente, para que el frontend pueda resolver "personas con rol DIRECTOR_DIVISION en esta división" sin ningún endpoint nuevo.

**Fuera de alcance**: no se toca `academic_config`/`AcademicDivision` — `directorPersonId` sigue validando solo `existsById`. La restricción "debe tener el rol" es una decisión de qué le ofrece el picker del frontend, no una regla de negocio nueva del backend de Divisiones.

## 3. Diseño técnico

- `UserRepository.UserSearchCriteria` (`identity/domain/port/out/UserRepository.java`): agregar `divisionId` (UUID, nullable) al record, junto a `roleType`/`status`/`search`/`page`/`size`.
- Adapter JPA: el filtro existente por `roleType` ya usa un predicado EXISTS contra `UserRole` (documentado en el Javadoc del propio `UserRepository`: "nunca un JOIN, para no duplicar filas") — agregar `AND (divisionId param is null OR ur.divisionId = :divisionId)` al mismo EXISTS.
- `ListUsersUseCaseImpl`: pasar el nuevo parámetro sin transformarlo.
- `UserController.listUsers`: nuevo `@RequestParam(required = false) UUID divisionId`.
- Sin cambios de seguridad — mismo endpoint, mismos matchers ya existentes (`GET /users` → ADMIN/SERVICIOS_ESCOLARES).
- Sin excepciones nuevas — `divisionId` sin `role` simplemente no filtra nada por división (combinación permisiva, igual que el resto de filtros opcionales de este endpoint).

## 4. Patrón a clonar

Extensión aditiva de un query existente, mismo criterio que cuando se agregó `programId` a `GET /generations` o `search` a `GET /persons` — no es un aggregate nuevo, es un filtro más sobre un endpoint que ya existe.

## 5. Execution Log (2026-07-28)

**Archivos modificados:**

- `identity/domain/port/out/UserRepository.java` — agregado `UUID divisionId` (último campo) a `UserSearchCriteria`, con Javadoc aclarando que solo tiene efecto combinado con `roleType`.
- `identity/infrastructure/persistence/UserJpaRepository.java` — el `AND (:divisionId IS NULL OR ur.divisionId = :divisionId)` se agregó DENTRO del mismo EXISTS que ya gatea `roleType` (en `value` y `countQuery`), no como una condición externa simétrica. Firma `search(...)` ahora recibe `divisionId` antes de `Pageable`.
- `identity/infrastructure/persistence/UserRepositoryAdapter.java` — pasa `criteria.divisionId()` al JPA repository.
- `identity/domain/port/in/ListUsersUseCase.java` — agregado `UUID divisionId` (último campo) a `ListUsersQuery`.
- `identity/domain/service/ListUsersUseCaseImpl.java` — pasa `query.divisionId()` sin transformar a `UserSearchCriteria`.
- `identity/infrastructure/web/UserController.java` — nuevo `@RequestParam(required = false) UUID divisionId` en `GET /users`.

**Decisión de diseño confirmada durante la implementación:** `divisionId` vive DENTRO del EXISTS gateado por `:roleType IS NULL OR ...`, no en una condición externa independiente. Consecuencia verificada con test: `divisionId` pasado SIN `roleType` es un no-op total (el EXISTS nunca se evalúa, matchea todos los usuarios) — no "filtra por usuarios con algún rol en esa división". Esto es lo que dice literalmente el diseño original (agregar el AND al MISMO EXISTS de `roleType`) y lo que el alcance aclara ("divisionId sin role no filtra nada por división").

**Tests agregados:**

- `UserRepositoryAdapterSearchIT`: `divisionIdCombinedWithRoleTypeExcludesRightRoleWrongDivision`, `divisionIdCombinedWithRoleTypeMatchesRightRoleAndRightDivision`, `divisionIdCombinedWithRoleTypeCountsUserOnceWhenOnlyOneOfSeveralRolesMatchesBoth` (rol correcto en una de tres UserRole rows, contado una sola vez), `divisionIdAloneWithoutRoleTypeHasNoEffect`.
- `UserControllerTest`: `listUsersPassesDivisionIdQueryParamThroughWithoutRole` + assertion de `divisionId` agregada al test existente de filtros combinados.
- `ListUsersUseCaseImplTest`: assertion de `criteria.divisionId()` agregada al test `listUsers_passesFiltersThroughToRepository`.
- Todos los positional-constructor call sites existentes de `UserSearchCriteria`/`ListUsersQuery` (en los 3 archivos de test) actualizados con el nuevo campo al final (`null` donde no aplica).

**Resultado `./mvnw verify` (JDK 21, Corretto):** BUILD SUCCESS. Unit: 506 → 507 (+1). Integration: 239 → 243 (+4). 0 failures, 0 errors.

**Desviaciones del plan:** ninguna funcional. Se agregó `divisionId` como último campo de ambos records (no junto a `roleType`) para no romper por posición todos los call sites existentes que usan constructores posicionales.
