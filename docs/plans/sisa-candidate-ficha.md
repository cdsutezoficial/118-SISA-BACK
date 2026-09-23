# Plan: Ficha de Admisión — `POST /candidates` (bounded context `admission`, Fase C)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/03-admision.md` (`Candidate` aggregate) + `00-shared-kernel.md` (`Person` + VOs `Address`/`HealthProfile`/`DiversityProfile`/`EmploymentInfo`/`HighSchoolBackground`) + `docs/requirements/02-ADMISION.md` (RF-ADM-011).

Fases previas: Fase A (2026-07-28) sembró los catálogos `State`/`Municipality`/`HighSchoolType` y `OutreachChannel` (plan `2026-07-28-inegi-catalogs-and-highschooltype.md`); Fase B (2026-07-29) extendió `Person` con los 5 VOs (plan `2026-07-29-person-extension.md`). Esta Fase C estrena el aggregate `Candidate` y el flujo que el estudiante llena desde el portal público: **un POST único** que persiste `Person` + perfiles + `Candidate` en una transacción y devuelve folio.

**Decisión de alcance del PO (sesión)**: el registro es **manual primero** — `llaveMxVerified` se acepta y se guarda como viene (`false`), la invariante "debe ser true" se difiere hasta integrar LlaveMX (`AuthenticateCandidatePortalUseCase`).

## 2. Comparación docs vs. implementación actual

Antes de este plan, en `admission` solo existían los catálogos (`OutreachChannel`, `HighSchoolType`) y sus 5 casos de uso. `Candidate` no existía; el frontend (17 pantallas) usaba `mockCandidates`. El backend del registro era 100% inexistente.

## 3. Modelo

`candidate` (tabla):

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| person_id | UUID (FK plana a `person`) | **No nulo** — sin relación JPA (convención cross-aggregate del repo) |
| admission_config_id | UUID (FK plana a `program_admission_config`, cross-boundary a `academic_config`) | **No nulo** |
| folio | String | **No nulo** — `ADM-{año}-{seq:06d}`, único por año-calendario (v1) |
| status | Enum<String> | **No nulo** — `REGISTERED, PAID, EXAM_TAKEN, ACCEPTED, REJECTED, ENROLLED` |
| llave_mx_verified | Boolean | **No nulo** — `false` en registro manual (decisión de esta fase) |
| registered_at | Instant | **No nulo** — `Instant.now()` en construcción |
| selected_by | UUID (FK plana a `user`) | nullable — lo setea Dirección en selección |
| selected_at | Instant | nullable |
| is_first_choice | Boolean | **No nulo** |
| outreach_channel_id | UUID (FK plana a `admission.OutreachChannel`) | nullable |
| is_enabled_for_induction | Boolean | **No nulo** — nace `false` |

**Réplica de `Person` en la misma transacción**: `Person(curp, firstName, lastName1, lastName2, null)` + los 5 VOs vía setters (`setAddress`…), guardados por cascade (`CascadeType.ALL`). `institutionalEmail` = `null` (es solo para staff); el email del candidato va a `personalEmail`.

## 4. Contrato del POST

**POST único `/candidates`** (sin sesión → `permitAll`, único POST anónimo de la app):

- Request `RegisterCandidateRequest` espejo de `FichaAdmisionCompleta` por secciones (`datosGenerales`, `domicilio`, `contacto`, `informacionComplementaria`, `ingresos`, `seleccionCarrera`, `antecedentesEscolares`, `llaveMxVerified`).
- **Catálogos por UUID, no por nombre**: `programAdmissionConfigId`, `outreachChannelId`, `stateId`/`municipalityId` (domicilio), `birthStateId`/`birthMunicipalityId` (nacimiento), `schoolStateId`/`schoolMunicipalityId` + `schoolTypeId` (preparatoria). El portal resuelve nombre→id con los endpoints de options existentes antes de postear.
- Fechas/horas como strings del front y **NO se configuran via Jackson** (el repo no tiene configuración de fechas ni `@JsonFormat`): `fechaNacimiento` `dd/MM/yyyy` → `LocalDate`; `horaInicio`/`horaFin` `HH:mm` → `LocalTime`. El parseo vive en `CandidateController` (capa web).
- Mapeos string→enum en `CandidateController` (capa web, no en el puerto): `sexo` {"Femenino","Masculino","Hombre","Mujer"} → `Gender` (M/F/NB); `estadoCivil` {"Soltero/a", "Casado/a", "Unión libre", "Divorciado/a", "Viudo/a"} → `MaritalStatus`; `tipoTrabajo` {"Tiempo completo","Medio tiempo"} → `EmploymentType`.
- `cctConfirmacion` se valida (debe ser igual a `cct`) y se colapsa en `cctConfirmed` para `HighSchoolBackground`.
- `modalidad` **NO va en el DTO** — la modalidad la define el programa del `ProgramAdmissionConfig` elegido, no se guarda en `Candidate` (03-admision.md).
- Response 201 `CandidateRegistrationResponse` = resultado del use case (id, personId, folio, status, llaveMxVerified, registeredAt, isFirstChoice, outreachChannelId, isEnabledForInduction).

## 5. Casos de uso

`RegisterCandidateUseCase` (port-in con command/result de tipo record) + `RegisterCandidateUseCaseImpl`. Validaciones en orden:

1. CURP ya existe en `person` → **409** `CandidateAlreadyExistsException` (candidato duplicado / fila staff en conflicto).
2. `ProgramAdmissionConfig` elegida no existe → **404** `ProgramAdmissionConfigNotFoundException`.
3. Config existe pero `status != OPEN` → **409** `ProgramAdmissionConfigNotOpenException`.
4. `outreachChannelId` no nulo y no resuelve → **404** `OutreachChannelNotFoundException` (ya existía).
5. `schoolTypeId` no nulo y no resuelve → **404** `HighSchoolTypeNotFoundException` (ya existía).

Cap-resistant (fichas pagadas vs `maxCandidates`): **diferido** hasta que exista `AdmissionPayment` (no se rechaza config llena en esta fase).

### Puertos out

- `CandidateRepository`: `save`, `findById`, `countByFolioStartingWith(prefix)` (base del folio Año→seq).
- `CandidatePersonRepository`: `save(Person)` (cascade 5 perfiles), `findByCurp` (duplicado cross-context sobre tabla compartida — misma decisión que `PersonLookupJpaRepository`, no se importa el port de `identity`).
- `ProgramAdmissionConfigQueryPort`: proyección mínima `findById → AdmissionConfigInfo(id, status)` sobre `program_admission_config` (repos propio read-only, no se importa el de `academic_config`).

### Folio

`ADM-{año}-{seq:06d}`, `seq = countByFolioStartingWith("ADM-{año}-") + 1` (único por año-calendario; aproximación v1 del "único por periodo" del dominio — el periodo destino de la config está disponible para un futuro exacto por periodo).

## 6. Endpoints y seguridad

`POST /candidates` (201/404/409/400).

**Seguridad**: único `permitAll` de escritura en `SecurityFilterConfig` (junto a `auth/login`/`auth/refresh`) — el aspirante no tiene sesión. Todo otro verbo de `/candidates/**` futura (listado/detalle admin) irá bajo `ADMIN`/`SERVICIOS_ESCOLARES`.

## 7. Excepciones nuevas

- `CandidateAlreadyExistsException` (409) — homologa `identity.DuplicateCurpException`.
- `ProgramAdmissionConfigNotFoundException` (404).
- `ProgramAdmissionConfigNotOpenException` (409).
- `InvalidCandidateFichaDataException` (400) — parseo de fecha/hora fallido en la capa web.

Todas mapeadas en `admission.GlobalExceptionHandler` (aditivo, `@Component("admissionGlobalExceptionHandler")`).

## 8. Patrón a clonar

`CreateAcademicPlanUseCase` (command/result records + impl con `@Transactional`) + `PersonProfileRoundTripIT` (test JPA de `Person`+perfiles, base para el IT de la ficha). Adapters infra: patrón `OutreachChannelRepositoryAdapter`.

## 9. Estructura de paquete

`admission/domain/{model/Candidate,CandidateStatus, port/in/RegisterCandidateUseCase, port/out/CandidateRepository,CandidatePersonRepository,ProgramAdmissionConfigQueryPort, service/RegisterCandidateUseCaseImpl}`, `admission/infrastructure/{persistence/CandidateJpaRepository,CandidatePersonJpaRepository,ProgramAdmissionConfigLookupJpaRepository + adapters, web/CandidateController, web/dto/RegisterCandidateRequest,CandidateRegistrationResponse}`, `admission/shared/exception/*`.

## 10. Fuera de alcance

- Integración LlaveMX y enforcement de `llaveMxVerified = true` (`AuthenticateCandidatePortalUseCase`).
- `AdmissionPayment` (Pago de ficha) y la validación cap-resistant / transición a `PAID`.
- Listado/detalle/transiciones admin de /candidates (Screens 3, 5-15) — sigue mockfront.
- Selección/resultados/examen (pantallas 7-12) y matrícula.
- Reescribir el wizard del frontend al contrato por UUID (fase posterior).