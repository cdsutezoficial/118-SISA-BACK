# Changelog — 118-SISA-BACK

Todos los cambios relevantes del backend se documentan aquí en orden cronológico inverso.

---

## [2026-09-28] El tipo ADMISSION: la ficha deja de pedir prestado ENROLLMENT

Commit: `19ead19`.

### La cuota de admisión se prestaba el tipo de la cuota de inscripción

El precio de la ficha, el conteo de lugares ocupados y el desplegable de carreras buscaban
un concepto `ENROLLMENT` con `is_tuition` encendido. Ese tipo nominaba dos cosas
distintas: la cuota de admisión y la cuota cuatrimestral de inscripción. Además hacía que
el vocabulario de admisión fuera inalcanzable desde el catálogo: la UI no ofrecía ningún
tipo "Admisión" que registrar, así que quien pagaba la ficha terminaba creando —o
renombrando— un concepto de inscripción.

`PaymentConceptType.ADMISSION` entra al enum y los cinco lookups pasan a apuntar a él.
`is_tuition` sigue siendo la bandera que acota a uno por programa, así que no cambian ni
la cardinalidad ni los dos 409 que ya existían (no hay ninguno / hay varios).

`findActiveEnrollmentForProgram` conserva el nombre a propósito: el concepto de dominio
sigue siendo la inscripción del programa; lo que cambió es el tipo con el que se busca.

| Capa | Archivo | Cambio |
|---|---|---|
| Domain | `academic_config/domain/model/PaymentConceptType.java` | **Nuevo valor `ADMISSION`** + Javadoc que explica por qué existe |
| Infra | `admission/infrastructure/persistence/PaymentConceptQueryAdapter.java` | Las 2 consultas del precio piden `ADMISSION` |
| Infra | `admission/infrastructure/persistence/AdmissionPaymentRepositoryAdapter.java` | Las 2 consultas de ocupación piden `ADMISSION` |
| Infra | `academic_config/infrastructure/persistence/ProgramAdmissionConfigJpaRepository.java` | El JPQL del desplegable filtra por `ADMISSION` |
| Puerto | `admission/domain/port/out/PaymentConceptQueryPort.java` | Javadoc alineado al tipo consultado |
| Puerto | `admission/domain/port/out/ProgramAdmissionConfigQueryPort.java` | Ídem |
| Puerto | `admission/domain/port/in/GetFichaAmountUseCase.java` | Ídem |
| Domain service | `admission/domain/service/FichaAmountResolver.java` | Ídem |
| Domain service | `admission/domain/service/GetFichaAmountUseCaseImpl.java` | Ídem |
| Domain service | `admission/domain/service/RegisterCandidateUseCaseImpl.java` | Ídem |
| Infra | `admission/infrastructure/persistence/PaymentConceptLookupJpaRepository.java` | Javadoc de las consultas |
| Infra | `admission/infrastructure/web/FichaAmountController.java` | Javadoc del 409 |
| Shared | `admission/shared/exception/FichaPaymentConceptNotFoundException.java` | Javadoc |
| Shared | `admission/shared/exception/AmbiguousFichaPaymentConceptException.java` | Javadoc |

**Sin migración de esquema.** El enum se persiste como `EnumType.STRING` sobre `VARCHAR`, así
que el valor nuevo entra solo.

### ⚠️ Migración de datos requerida

En ambientes que ya tengan conceptos de admisión hay que correr esto **antes de desplegar**:

```sql
UPDATE payment_concept SET type = 'ADMISSION' WHERE type = 'ENROLLMENT' AND is_tuition = true;
```

Sin ella, un programa cuya ficha ya estaba precioada devuelve **409 por falta de concepto
`ADMISSION`**, y la UI no tiene forma de arreglarlo: el listado ya no presenta `ENROLLMENT`
como admisión. El caso de las fichas ya vendidas no se rompe (el pago ya ocurrió), pero sí
la cotización de las que aún no se cobran.

### Los mensajes de error no se tocaron

`FichaAmountResolver` sigue diciendo "concepto de inscripción" al Aspirante. Es copy orientado
a la persona y el término sigue siendo correcto; lo que estaba mal era el tipo con el que
el backend buscaba, no la palabra que se le muestra.

### Pruebas

Los cuatro IT afectados se actualizan al tipo nuevo.
`aTuitionOfTheEnrollmentTypeIsNotTheAdmissionFee` queda como guarda de regresión: crea un
concepto `ENROLLMENT` con `is_tuition` activo y vigente, y afirma que **no** aparece en el
precio de la ficha. Es exactamente el arreglo que se revirtió por error, y sin esa prueba
pasaría inadvertido porque el flujo seguiría funcionando —con el tipo equivocado—.

1016 unitarias + 30 de integración (`ProgramAdmissionConfigOptionsQueryIT` 16,
`CheckoutSlotClaimerConcurrencyIT` 2, `PaymentConceptWindowQueryIT` 12) en verde contra
MySQL real.

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
