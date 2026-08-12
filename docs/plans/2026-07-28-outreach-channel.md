# Plan: OutreachChannel (bounded context `admission` — primera pieza)

## 1. Contexto (docs-first)

Fuente: `118-SISA-CLAUDE/docs/design/dominio/03-admision.md` (sección "Catálogos", `OutreachChannel`) + `docs/requirements/02-ADMISION.md` (RF-ADM-011, sección "Selección de carrera": "Medio de difusión por el que se enteró").

`OutreachChannel` es el catálogo de medios de difusión por los que un candidato se enteró de la universidad (Facebook, recomendación de un familiar, feria educativa, etc.) — configurable sin tocar código. `Candidate` lo referenciará vía `outreachChannelId` cuando se construya (fuera de alcance de este plan).

**Primera pieza real del bounded context `admission`** (paquete `mx.edu.utez.sisa.admission`, distinto de `academic_config` donde vive `ProgramAdmissionConfig`). Es el catálogo más simple del dominio — sin relaciones, sin campos complejos.

## 2. Comparación docs vs. implementación actual

Cero código — no existe el paquete `admission` todavía en el backend.

## 3. Modelo

| Atributo | Tipo | Restricciones |
|---|---|---|
| id | UUID | PK |
| name | String | No nulo. Sin unicidad documentada — mismo criterio ya aplicado a `SubjectClassification`/`PaymentConcept` (no inventar una restricción que el doc no pide) |
| status | Enum | ACTIVE, INACTIVE |

## 4. Casos de uso

`CreateOutreachChannelUseCase`, `UpdateOutreachChannelUseCase`, `ListOutreachChannelsUseCase`, `GetOutreachChannelUseCase`, `ChangeOutreachChannelStatusUseCase` — mismo shape de 5 casos de uso que `SubjectClassification`/`AcademicDivision` (aggregate más simple del proyecto: sin FKs, sin entidades hijas, sin validaciones de rango).

## 5. Endpoints y seguridad

`POST/GET /outreach-channels`, `GET/PUT /outreach-channels/{id}`, `PATCH /outreach-channels/{id}/status`.

**Seguridad**: `ADMIN` + `SERVICIOS_ESCOLARES` — mismo criterio que `ProgramAdmissionConfig` (no existe un rol dedicado a "admisión" en el catálogo de 11 roles; Servicios Escolares es quien gestiona el proceso completo según `03-admision.md`, "Roles que acceden").

## 6. Excepciones nuevas

- `OutreachChannelNotFoundException` (404) — único caso de uso real; no hace falta ninguna excepción 400/409 (sin FKs que validar, sin unicidad que proteger).

## 7. Patrón a clonar

`SubjectClassification` o `AcademicDivision` — el aggregate más simple ya existente en el proyecto (sin entidades hijas, sin FKs de otros aggregates, un solo campo de texto + estado).

## 8. Estructura de paquete

Nuevo paquete `mx.edu.utez.sisa.admission` (primera vez que se usa), mismas subcarpetas que `academic_config`/`identity`: `domain/{model,port/in,port/out,service}`, `infrastructure/{persistence,web,web/dto}`, `shared/exception`.

## 9. Fuera de alcance

- `Candidate` y el resto del bounded context `admission`.
- Cualquier referencia desde `Candidate.outreachChannelId` (no existe `Candidate` todavía).
- Frontend (fase posterior).
