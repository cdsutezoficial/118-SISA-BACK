# Changelog — 118-SISA-BACK

Todos los cambios relevantes del backend se documentan aquí en orden cronológico inverso.

---

## [2026-07-08] Enmienda: campo `dgpCode` en `AcademicProgram`

### Contexto

Tras la entrega completa del módulo `academic-config/programs` (ver
`openspec/changes/archive/2026-07-08-academic-config-programs/`), se identificó
que el frontend requería un campo adicional no contemplado en el spec original:
`dgpCode` (clave de registro ante la Dirección General de Profesiones de la SEP).
El campo es opcional (`nullable`) — no todos los programas tienen registro DGP.

### Archivos modificados

| Capa | Archivo | Cambio |
|---|---|---|
| Domain model | `domain/model/AcademicProgram.java` | Campo `dgpCode: String` (nullable) + getter + `updateDetails()` |
| Port (in) | `domain/port/in/CreateAcademicProgramUseCase.java` | `dgpCode` en `CreateAcademicProgramCommand` y `AcademicProgramResult` |
| Port (in) | `domain/port/in/UpdateAcademicProgramUseCase.java` | `dgpCode` en `UpdateAcademicProgramCommand` |
| Port (in) | `domain/port/in/ListAcademicProgramsUseCase.java` | `dgpCode` en `ProgramSummary` |
| Service | `domain/service/CreateAcademicProgramUseCaseImpl.java` | `toResult()` incluye `dgpCode` |
| Service | `domain/service/UpdateAcademicProgramUseCaseImpl.java` | `updateDetails()` recibe `dgpCode` |
| Service | `domain/service/ListAcademicProgramsUseCaseImpl.java` | `toSummary()` incluye `dgpCode` |
| Web | `infrastructure/web/AcademicProgramController.java` | `toResponse()`, `toItem()`, ambas construcciones de comando |
| DTO | `infrastructure/web/dto/AcademicProgramResponse.java` | Campo `dgpCode` |
| DTO | `infrastructure/web/dto/AcademicProgramListItemResponse.java` | Campo `dgpCode` |
| DTO | `infrastructure/web/dto/CreateAcademicProgramRequest.java` | Campo `dgpCode` |
| DTO | `infrastructure/web/dto/UpdateAcademicProgramRequest.java` | Campo `dgpCode` |

### Impacto en API

| Endpoint | Cambio |
|---|---|
| `POST /programs` | Body acepta campo opcional `dgpCode: string \| null` |
| `PUT /programs/{id}` | Body acepta campo opcional `dgpCode: string \| null` |
| `GET /programs` | Cada item de la lista incluye `dgpCode: string \| null` |
| `GET /programs/{id}` | Respuesta incluye `dgpCode: string \| null` |

### Reglas de negocio

- `dgpCode` es **opcional** — puede ser `null` o estar ausente en el request.
- No hay validación de unicidad ni formato para `dgpCode` (la SEP no garantiza unicidad entre instituciones).
- Un programa sin `dgpCode` es completamente válido.
