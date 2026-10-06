# Plan: Listado de Candidatos — `GET /candidates` (Pantalla 3)

> Recurso: `Candidate` — aspirante del proceso de admisión.
> Bounded context: `admission`. Endpoint raíz: `/candidates`.
> Fuente de verdad del contrato: `118-SISA-CLAUDE/docs/design/dominio/03-admision.md` (Pantalla 3) + `118-SISA-CLAUDE/docs/design/figma/prompts/03-admision.md`.

## 1. Contexto (docs-first)

- **Código fuente:** `118-SISA-BACK`, paquete `mx.edu.utez.sisa.admission`.
- **Frontend consumidor:** `118-SISA-FRONT/src/app/modules/admision/pages/CandidatosList.tsx`
  (hoy 100% mock con `mockCandidates`).
- **Precedente de scope por rol:** Director de División solo ve los candidatos de su
  división, resuelto **server-side** (`UserRole.divisionId`), no por un `divisionId`
  que mande el cliente. Es el mismo criterio de `docs/plans/2026-07-28-director-division-role-filter.md`.
- **Estado del dominio:** `Candidate` ya existía (registro de ficha) pero **no** tenía
  endpoint de listado. Los 4 estados terminales (`EXAM_TAKEN`, `ACCEPTED`, `REJECTED`,
  `ENROLLED`) son alcanzables por casos de uso aún no desarrollados; solo `REGISTERED`
  y `PAID` se producen hoy.

## 2. Alcance

1. `CandidateRepository.search(CandidateSearchCriteria)` con JPQL + paginación.
2. Filtro de división vía `EXISTS` (`Candidate → ProgramAdmissionConfig → AcademicProgram`).
3. `CallerDivisionScopePort` + adapter sobre `user_role`/`role` de `identity`.
4. `ListCandidatesUseCase` + impl (default 20, cap 100, fail-closed).
5. DTOs `CandidateListItemResponse` / `CandidateListResponse`.
6. `GET /candidates` en `CandidateController`.
7. `PermissionRegistry` (`GET /candidates → CANDIDATES_READ`) + matcher de
   `SecurityFilterConfig` + seed del permiso en `IdentityAuthorizationCatalogSeedRunner`.
8. Seed de la división `DATID` y del scope del Director (`TestDivisionsSeedRunner` /
   `TestDirectorScopeSeedRunner`).
9. Tests: unit (caso de uso + controller + registry) e IT JPA del search.

## 3. Contrato de API

```
GET /candidates?status=&programId=&periodId=&search=&page=0&size=20
```

- `status`: `CandidateStatus` exacto (`REGISTERED|PAID|EXAM_TAKEN|ACCEPTED|REJECTED|ENROLLED`).
- `programId`: id del `AcademicProgram` (NO el `admissionConfigId`).
- `periodId`: periodo destino del `ProgramAdmissionConfig`.
- `search`: coincidencia libre sobre folio, CURP o nombre.
- **No** se acepta `divisionId`: se resuelve server-side según el rol del llamador.

Envelope (mismo que el resto de listados paginados):

```jsonc
{
  "items": [
    { "id": "uuid", "folio": "ADM-2026-000001", "fullName": "Ana García López",
      "curp": "…18…", "programId": "uuid", "programName": "TSU …",
      "status": "REGISTERED", "registeredAt": "2026-09-30T18:00:00Z" }
  ],
  "totalElements": 1, "totalPages": 1, "page": 0, "size": 20
}
```

Orden: `registeredAt DESC, id DESC`. Default `size=20`, máximo 100.

### Scope por rol (fail-closed)

`CallerDivisionScopePort` devuelve un tri-state:

- `GLOBAL` (Servicios Escolares / Admin): sin filtro.
- `DIVISION(uuid)` (Director de División): filtra por `divisionId`.
- `NONE` (Director con rol pero `divisionId = null`): **0 filas**.

## 4. Decisión de contrato — `programId` es el del `AcademicProgram`

La fila expone `programId` = id del `AcademicProgram`, no el `admissionConfigId`.
Así el valor que el usuario elige en el filtro es el mismo que viaja de vuelta:
el JPQL filtra `cfg.programId = :programId`. Se resolvió con un puerto de solo
lectura que devuelve `Map<UUID, ProgramRef>` por `configId`:

- `ProgramAdmissionConfigQueryPort.findProgramRefsByConfigIds(Set<UUID>)`
  → `Map<UUID, ProgramRef>`, con `record ProgramRef(UUID programId, String programName)`.

Renombrado desde el borrador `findProgramNamesByConfigIds` (que devolvía
`Map<UUID, String>`), verificado sin referencias residuales.

## 5. Fuera de alcance

- Endpoint de detalle `GET /candidates/{id}` (ya existe, queda como está).
- `GET /candidates/*` sigue `permitAll` por el portal público; documentar el
  pendiente de acceso por propiedad, sin cambiarlo ahora.
- Persistencia del cambio de programa (la UI lo tenía mock).

## 6. Execution Log (2026-09-30)

### Archivos creados / modificados

**Dominio:**
- `domain/model/CandidateSearchCriteria.java`, `CandidateSearchPage.java`.
- `domain/port/in/ListCandidatesUseCase.java` (+ `ListCandidatesQuery`).
- `domain/service/ListCandidatesUseCaseImpl.java` — default 20, cap 100, fail-closed.
- `domain/port/out/CandidateRepository.java` — `search(...)`.
- `domain/port/out/CallerDivisionScopePort.java` — tri-state.

**Persistencia (admission):**
- `infrastructure/persistence/CandidateJpaRepository.java`,
  `CandidateRepositoryAdapter.java` — JPQL con `EXISTS` + free-text, sort.
- `infrastructure/persistence/CandidatePersonRepository.java` — `findByIds`.
- `infrastructure/persistence/CallerUserRoleJpaRepository.java`,
  `CallerDivisionScopeAdapter.java`.
- `infrastructure/persistence/ProgramAdmissionConfigQueryAdapter.java`.

**Web / config:**
- `infrastructure/web/dto/CandidateListItemResponse.java`, `CandidateListResponse.java`.
- `infrastructure/web/CandidateController.java` — `GET /candidates`.
- `infrastructure/config/UseCaseConfig.java` — bean `listCandidatesUseCase`.

**Identity:**
- `infrastructure/security/PermissionRegistry.java` — `GET /candidates → CANDIDATES_READ`.
- `infrastructure/security/SecurityFilterConfig.java` — matcher
  `hasAnyRole("ADMIN","SERVICIOS_ESCOLARES","DIRECTOR_DIVISION")`.
- `infrastructure/bootstrap/IdentityAuthorizationCatalogSeedRunner.java` — permiso
  `CANDIDATES_READ`.
- `domain/port/out/UserRoleScopePort.java`, `infrastructure/persistence/UserRoleScopeAdapter.java`,
  `domain/model/UserRole.java` (`scopeDivision(UUID)`) — seed del scope.

**academic_config (seeds):**
- `infrastructure/bootstrap/TestDivisionsSeedRunner.java` (`@Order(5)`) — DATID.
- `infrastructure/bootstrap/TestDirectorScopeSeedRunner.java` (`@Order(15)`).
- Javadoc de `TestAccountsSeedRunner` actualizado.

**Tests:**
- `ListCandidatesUseCaseImplTest` — 9 tests.
- `CandidateControllerTest` — 33 tests.
- `PermissionRegistryTest` — 5 tests.
- `CandidateRepositoryAdapterSearchIT` — 8 tests (`@DataJpaTest`).
- `TestDirectorScopeSeedRunnerIT` — 3 tests.

### Decisiones técnicas

1. **`programId` del `AcademicProgram`** (§4) para round-trip con el filtro JPQL.
2. **Scope server-side**: el cliente nunca manda `divisionId`; el puerto decide
   `GLOBAL`/`DIVISION`/`NONE` (fail-closed).
3. **Sin N+1**: los nombres de programa se resuelven por lote
   (`findProgramRefsByConfigIds`).
4. **Contrato lean**: la fila no incluye `paidAt`/`paymentStatus`/`isEnabledForInduction`;
   eso vive en `GET /candidates/{id}`.
5. **`GET /candidates` declarado explícitamente** en el matcher: el patrón
   `/candidates/*` no coincide con `/candidates`.

### Resultado de tests

- Backend (con `DB_URL` a `sisa_it` y `SPRING_MAIL_HOST` para contextos full):
  - `ListCandidatesUseCaseImplTest` 9/9; `CandidateControllerTest` 33/33.
  - `PermissionRegistryTest` 5/5.
  - `CandidateRepositoryAdapterSearchIT` 8/8.
  - `TestDirectorScopeSeedRunnerIT` 3/3.
  - Suite unitaria completa: **1062 tests, 0 fallos**.

### Infra de pruebas — aprendizajes

- **Los `-D` del CLI de Maven no llegan al JVM forkeado** de surefire/failsafe.
  Usar variables de entorno (`$env:DB_URL=...`), que sí hereda el fork.
- Los IT llevan `ddl-auto=create-drop`: **nunca** apuntar `DB_URL` a `sisa`.
- Los tests full-context necesitan `SPRING_MAIL_HOST` (el `.env` define
  `SPRING_MAIL_*` en SCREAMING_SNAKE, que no enlaza como property importado);
  sin él, `JavaMailSender` no existe y el contexto falla.
- En PowerShell hay que entrecomillar los `-D`: `"-Dit.test=…"`.
- Surefire incluye `**/Test*.java`: las clases `Test*IT` corren también bajo `mvn test`.

## 7. Desviaciones

- El borrador del puerto (`findProgramNamesByConfigIds`) se renombró a
  `findProgramRefsByConfigIds` al corregir el contrato de `programId` (§4).
- `CandidateRepositoryAdapterSearchIT` requería `targetGenerationId` no nulo
  (columna NOT NULL en BD); se usa `UUID.randomUUID()` en el helper `newConfig`.
