# Changelog — 118-SISA-BACK

Todos los cambios relevantes del backend se documentan aquí en orden cronológico inverso.

---

## [2026-09-27] El cupo se reserva en el checkout + códigos de error estables

Dos commits: `1e4a824` y `5c1ce31`.

### El cupo se reservaba en el registro

`RegisterCandidateUseCase` validaba `maxCandidates` al registrarse, días antes de lo
que protegía. Quien se registró el lunes y pagó el viernes fue comprobado el lunes,
contra un conteo de fichas pagadas que no podía incluir un pago que aún no había
hecho: todos los que leyeron el contador igual pasaron y el exceso solo apareció en el
banco. `validateQuota` desaparece.

La regla se mueve al checkout (`5c1ce31`), que es donde se pide el dinero y el
último punto en que decir que no todavía no cuesta un reembolso.

| Capa | Archivo | Cambio |
|---|---|---|
| Domain | `domain/model/AdmissionPayment.java` | `checkoutClaimedAt: Instant` nullable + `claimCheckoutSlot()` / `releaseCheckoutSlot()` |
| Domain service | `domain/service/CheckoutSlotClaimer.java` | **Nuevo.** `claim` / `release` / `persistCheckoutSession`, cada uno en su transacción |
| Port (out) | `domain/port/out/AdmissionQuotaPort.java` | **Nuevo.** `lockQuota(configId)` → `QuotaState` |
| Port (out) | `domain/port/out/AdmissionPaymentRepository.java` | `countOccupiedByProgramId` y `…ExcludingCandidate` |
| Domain service | `domain/service/InitiateFichaPaymentUseCaseImpl.java` | Reclaza y devuelve el lugar alrededor de la llamada a Evo; **quita** `@Transactional` (no se mantiene una transacción abierta a través del gateway) |
| Domain service | `domain/service/RegisterCandidateUseCaseImpl.java` | Elimina `validateQuota` y su import |
| Infra | `infrastructure/persistence/AdmissionPaymentOccupancyQueries.java` | **Nuevo.** El JPQL único que responde el desplegable y el claim |
| Infra | `infrastructure/persistence/AdmissionQuotaAdapter.java` | **Nuevo.** Traduce el lock a `PESSIMISTIC_WRITE` |
| Infra | `infrastructure/persistence/ProgramAdmissionConfigLookupJpaRepository.java` | `findByIdForUpdate` con `@Lock(PESSIMISTIC_WRITE)` |
| Infra | `infrastructure/gateway/EvoPaymentsGatewayAdapter.java` | Marca los fallos ambiguos con `EvoPaymentGatewayException.possiblyCreated` |
| Shared | `admission/shared/exception/EvoPaymentGatewayException.java` | `orderMayHaveBeenCreated()` — decide si el lugar se devuelve |
| Infra | `academic_config/infrastructure/web/ProgramAdmissionConfigController.java` | Recibe el `Clock` inyectado (decía la ventana en la zona del servidor, el checkout en la de admisión) |

**Concurrencia.** El lock de fila sin más no alcanza: bajo `REPEATABLE READ` de MySQL
el `SELECT` del conteo lee el snapshot que se fijó antes del lock, así que el segundo
candidato contaba cero ocupados y los dos se llevaban el lugar. `READ_COMMITTED` lo
convierte en lectura actual. `CheckoutSlotClaimerConcurrencyIT` reproducía el fallo
antes del arreglo: dos candidatos, un lugar, cero rechazos.

**Un lugar ocupado** es una ficha pagada **o** una pendiente con `checkoutClaimedAt`
mientras la ventana del concepto siga abierta. El `EXISTS` sobre `availableUntil`
evita el job de limpieza: un checkout abandonado sale del conteo solo.

### Códigos de error estables (`1e4a824`)

El frontend tenía que ramificar por `message`, que es copy y se reescribe. Tres
fallos distintos que llegan los tres como 409 mandaban a tres lugares distintos y no
se podían expresar sin adivinar la palabra.

- `ErrorResponse` suma `code: String` nullable; el constructor de 5 argumentos se
  conserva para que los módulos que no adoptan uno no pasen `null` en cada llamada.
- El handler de Admisión publica 14 códigos `ADMISSION_*`, que **a partir de ahora
  son parte del contrato HTTP**: renombrarlos es incompatible, los mensajes no.

### Verificación

- `mvn test`: 1016/1016.
- `CheckoutSlotClaimerConcurrencyIT`: 2/2 (requiere `DB_URL` a `sisa_it`, nunca `sisa`
  — las IT llevan `ddl-auto=create-drop`).

### Pendiente de decisión de producto

El conteo es por `programId`, no por `admissionConfigId`, porque la ventana de
expiración vive en el concepto y se busca por programa. La regla acordada es que el
cupo es **por proceso de admisión** (por periodo), así que el conteo por programa
mezcla ciclos y una carrera que llenó su cupo en 2026-1 sigue apareciendo llena en
2027-1. Decidido el diseño, pendiente la aprobación para ejecutarlo: ver
`decision-cupo-proceso-admision.md` y `historial-admision-evo-pagos.md` (hallazgo 2)
en la raíz de `/sisa`.

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
