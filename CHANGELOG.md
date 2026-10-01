# Changelog — 118-SISA-BACK

Todos los cambios relevantes del backend se documentan aquí en orden cronológico inverso.

---

## [2026-09-30] La cotización de la ficha usa el reloj, no la fecha de arranque

Commit: pendiente.

### Por qué

`GetFichaAmountUseCaseImpl` recibía un `quoteDate` fijo inyectado al construir el
bean (`LocalDate.now()` en `UseCaseConfig`). Si el proceso vive semanas — o si se
cambia la vigencia de un concepto — la fecha se queda congelada y la pantalla
puede decir "vigente" o "no vigente" mal (§2.6).

### Qué cambió

- `GetFichaAmountUseCaseImpl`: recibe un `Clock` y cotiza con
  `LocalDate.now(clock)` en cada llamada, igual que el resto del flujo de
  admisión.
- `UseCaseConfig`: el bean `getFichaAmountUseCase` recibe el `Clock` de admisión
  en vez de `LocalDate.now()`.

### Tests

`GetFichaAmountUseCaseImplTest`: el quote sigue resolviendo el concepto del
programa, ahora contra un reloj fijo. Suite completa en verde, 0 fallos.

---

## [2026-09-30] Los tres candados de fecha al iniciar el pago de la ficha

Commit: pendiente.

### Por qué

`InitiateFichaPaymentUseCaseImpl` solo miraba la ventana del **concepto** de
pago. Con el plazo de 10 días reinstalado (§1.9) aparecen dos cortes más, y el
orden importa: la **ficha** tiene su propio reloj (día 0 = registro + 10 días),
el **proceso** tiene su fecha de cierre (`closesAt`), y el **concepto** su
ventana de catálogo. Además, comparar estas fechas con `Instant` mataba pagos
válidos: una ficha pagable el día de cierre lo dejaba de ser en cuanto pasaba la
hora exacta de cierre, aunque el calendario del aspirante todavía dijera "hoy"
(§10.2). Los tres gates se comparan ahora como **fechas de calendario** en la
zona de admisión.

### Qué cambió

- `InitiateFichaPaymentUseCaseImpl`: `requirePaymentWindowOpen` evalúa los tres
  gates en orden, **antes** de tocar EVO (un cierre local es reversible; un
  `INITIATE_CHECKOUT` quemado no):
  1. **Ficha** — `today > candidate.paymentDeadline(zone, deadline-days)` →
     nueva `FichaPaymentExpiredException`
     (`409 ADMISSION_FICHA_EXPIRED`).
  2. **Proceso** — `today > closesAt` (fecha local) → reusa
     `ProgramAdmissionConfigSalesClosedException`
     (`409 ADMISSION_SALES_WINDOW_CLOSED`), mismo mensaje que el registro.
  3. **Concepto** — `fichaAmountResolver.requirePayableOn` conserva su tipo y
     código (concepto vencido / inexistente) pero con el mensaje de admisión
     "No se encontró pago vigente configurado para este proceso. Comunícate con
     Servicios escolares."
- Nuevo parámetro `deadline-days` en el constructor del caso de uso, cableado en
  `UseCaseConfig` desde `sisa.admission.payment.deadline-days`.
- Nueva `FichaPaymentExpiredException` (`admission/shared/exception`), mapeada
  en `GlobalExceptionHandler` al código estable `ADMISSION_FICHA_EXPIRED`.

### Tests

`InitiateFichaPaymentUseCaseImplTest`: gate 0 (ficha vencida se rechaza antes de
consultar concepto/Evo), el día límite sigue pagable, gate 1 (tras el cierre del
proceso se rechaza con el mensaje del registro), regresión §10.2 (el día de
cierre sigue pagable sin importar la hora) y gate 2 (concepto vencido o
inexistente toman el mensaje de admisión sin filtrar la fecha del catálogo).

---

## [2026-09-30] El candado del CURP pasa a ser la ficha viva

Commit: pendiente.

### Por qué

`RegisterCandidateUseCaseImpl` bloqueaba el registro con solo que existiera un
`Person` con ese CURP (`findByCurp`). Eso le colgaba el CURP para siempre a
quien registró una ficha y nunca la pagó: la ficha murió, pero la persona no
podía volver a intentarlo. §1.10: **el CURP no bloquea a la persona, bloquea la
ficha**. Una persona puede registrar su CURP varias veces, pero solo puede tener
una ficha viva a la vez.

### Qué cambió

- `CandidateRepository.findAllByPersonId` (+ adaptador y JPA).
- `RegisterCandidateUseCaseImpl`: el pre-chequeo pasa a
  `validateCurpHasNoLiveFicha`, que lee las fichas de la persona y bloquea solo
  si alguna está **viva**. Viva = `PAID` en adelante, o `REGISTERED` dentro de su
  plazo. `PAYMENT_EXPIRED` y `REGISTERED` ya vencida (en el hueco entre el
  vencimiento y el barrido de las 00:10) **liberan** el CURP.
- El cálculo de "vencida" reusa `Candidate.paymentDeadline` con el **mismo**
  `deadline-days` que el barrido (nuevo parámetro del constructor), para que el
  candado y el barrido no puedan separarse por configuración.
- Dos mensajes según el caso (ficha vigente / ficha ya en el proceso), ambos bajo
  el mismo `409 ADMISSION_CANDIDATE_ALREADY_EXISTS`.
- Cambio de comportamiento buscado: un `Person` sin ninguna ficha (fila de
  personal, o creada por otro módulo) ya no bloquea el registro.
- Wiring en `admission/infrastructure/config/UseCaseConfig`.

### Tests

`RegisterCandidateUseCaseImplTest`: rechaza con ficha viva (registrada dentro de
plazo) y con ficha pagada; permite con ficha `PAYMENT_EXPIRED` y con
`REGISTERED` ya fuera de plazo. Suite completa en verde, 0 fallos.

---

## [2026-09-30] El plazo de 10 días de la ficha: estado, cálculo y barrido

Commit: pendiente.

### Por qué

El negocio reinstala la regla que se había derogado: una ficha tiene **10 días
desde su registro** para pagarse, y si no se paga en esos 10 días **no cuenta
como válida** y libera el CURP. El estado "no pagó" es propio del **candidato**,
no de la ficha de pago (que ni siquiera existe hasta que hay un intento de
cobro), y no puede ser `REJECTED`: ese estado ya significa "no pasó la
evaluación académica" dentro del ciclo `REGISTERED → PAID → EXAM_TAKEN →
ACCEPTED|REJECTED → ENROLLED`.

### Qué cambió

- `application.properties`: vuelve `sisa.admission.payment.deadline-days=${SISA_PAGO_DIAS:10}`,
  y el comentario que afirmaba que la propiedad ya no existía se sustituye por
  uno que describe la ventana (día 0 = registro, cierre 23:59:59 del día N).
- `CandidateStatus.PAYMENT_EXPIRED`, alcanzable **solo** desde `REGISTERED`.
- `Candidate`: `registeredOn(zone)` (día 0, leído en la zona de admisión — un
  registro a las 23:00 locales no rueda al día siguiente),
  `paymentDeadline(zone, days)` (derivado, **sin columna nueva**) y
  `markPaymentExpired()` (idempotente; nunca toca una ficha `PAID`).
- `CandidateRepository.findAllByStatus` (+ adaptador y JPA).
- Nuevo `ExpireStaleFichaPaymentsUseCase` (+ `Impl`) y el job diario
  `VENCEN_FICHAS` (00:10), siguiendo el patrón de
  `AdvanceAcademicPeriodStatusJob`. Usa el `Clock` de admisión, así que compara
  **por fecha calendario**, no `Instant` contra `Instant`.
- Wiring en `admission/infrastructure/config/UseCaseConfig`.

### Tests

`CandidateTest` (día 0, zona de admisión vs conversión UTC, expiración
idempotente, una ficha `PAID` no expira) y
`ExpireStaleFichaPaymentsUseCaseImplTest` (vence pasado el plazo, el día límite
**sí** se puede pagar, solo se guarda lo que cambió). Suite completa en verde,
0 fallos.

---

## [2026-09-30] El cupo del checkout se contaba por carrera, no por proceso

Commit: pendiente.

### Por qué

Había dos definiciones de "lugar ocupado" y no coincidían: el catálogo que
ofrece las carreras contaba por `admissionConfigId`, y el checkout que aparta el
lugar contaba por `programId`. Como la admisión de nuevo ingreso es anual, una
carrera con cupo lleno en el ciclo 2026-1 y cupo libre en el 2027-1 se
**ofrecía** en el formulario y se **rechazaba** al pagar, porque las fichas del
ciclo viejo seguían sumando en el conteo del nuevo. El cupo es del proceso
(carrera + período), no de la carrera.

### Qué cambió

- `AdmissionPaymentOccupancyQueries`: los dos conteos (y su variante
  `ExcludingCandidate`) pasan a `WHERE cand.admissionConfigId = :admissionConfigId`.
  La escalera de tarifas que vive dentro del `EXISTS` **sigue leyendo
  `cfg.programId`**: el **precio** es por programa, el **cupo** es por proceso, y
  confundir los dos ejes era justamente el bug.
- Renombrados `countOccupiedByProgramId` / `…ExcludingCandidate` a
  `countOccupiedByConfigId` / `…ExcludingCandidate` en el puerto, el adaptador y
  los llamadores.
- `CheckoutSlotClaimer` usa `candidate.getAdmissionConfigId()` en vez del
  `programId` del cuota-estado.
- `AdmissionQuotaPort.QuotaState` pierde `programId` (era un vestigio sin uso).
- `ProgramAdmissionConfigJpaRepository`: su subconsulta ya contaba por config; el
  Javadoc ahora deja escrito que las dos definiciones son idénticas y por qué el
  `EXISTS` de precio se queda.

### Tests

`ProgramAdmissionConfigOptionsQueryIT`: nuevo `aFullOldCycleDoesNotBlockTheSame
ProgramsNewCycle` (ciclo viejo lleno + ciclo nuevo libre, **mismo programa**),
reescrito `countsOnlyTheFichasOfTheConfigBeingOffered` con dos ciclos del mismo
programa, y ampliado `dropdownAndClaimerAgreeOnWhatOccupiesASlot` para que la
equivalencia catálogo↔checkout se pruebe sobre datos que la pongan a prueba. Los
casos viejos usaban un programa por config, que es el único escenario donde
contar por config y por programa da lo mismo (la equivalencia era vacía).
`CheckoutSlotClaimerTest` / `CheckoutSlotClaimerConcurrencyIT` /
`RegisterCandidateUseCaseImplTest` actualizados a la nueva firma. Suite de
unidad: **1054**, 0 fallos; los 2 IT tocados, en verde.

---

## [2026-09-30] Una carrera por el folio se reportaba como "candidato duplicado"

Commit: pendiente.

### Por qué

Al agregar el índice único sobre `candidate.folio` (ver la entrada siguiente),
la colisión dejó de ser corrupción silenciosa y pasó a ser un `409`. Pero el
handler mapeaba **toda** violación de índice único a `ADMISSION_CANDIDATE_ALREADY_EXISTS`
con el mensaje:

> Ya existe un registro con esos datos. Revisa tu información e inténtalo de nuevo.

Eso es falso. `generateFolio()` es `count(prefix) + 1` (`RegisterCandidateUseCaseImpl:344-348`),
un read-then-write sin lock: dos registros simultáneos calculan el mismo número y
**el perdedor** choca con el índice. No hay datos duplicados, no se guardó nada —
la transacción entera se revierte, incluido el `INSERT person` de la línea 141 — y
la Aspirante no tiene nada que revisar.

### Qué cambió

- Nuevo código estable `ADMISSION_REGISTRATION_CONFLICT`, distinto de
  `ADMISSION_CANDIDATE_ALREADY_EXISTS`: el primero es transitorio y se resuelve
  siempre reintentando; el segundo es un hecho sobre la Aspirante y nunca se
  resuelve así.
- `DuplicateKeyException` ahora responde con un mensaje de reintento honesto y
  sin accusationar los datos.
- El front distingue el código nuevo explícitamente, en lugar de dejarse caer por
  el branch genérico de 409 por casualidad.

### Por qué el reintento manual siempre funciona

InnoDB solo reporta conflicto de índice único contra filas **commiteadas**. Si a
la Aspirante le llegó la colisión, el ganador ya commiteó, así que el `count` de
su segundo intento ya avanzó y sí obtiene el folio libre. El wizard nunca navegó
(`setFolio` no se alcanzó), así que los 4 pasos siguen intactos y el botón
"Finalizar registro" vuelve a estar habilitado.

La rareraza es la carrera por CURP: el pre-chequeo (`RegisterCandidateUseCaseImpl:185`)
también es read-then-write, así que su perdedor puede caer aquí en vez de recibir
`CandidateAlreadyExistsException`. Se autocorrige: para entonces la otra ya
commiteó, y el reintento sí lo detecta con el mensaje correcto de CURP.

### Tests

3 en `admission.infrastructure.web.GlobalExceptionHandlerTest`: el código nuevo
es reintentable y distinto del de duplicado, el mensaje no acusa los datos, y no
filtra el nombre del índice. Suite completa: **1042**, 0 fallos.

---

## [2026-09-30] La validación anidada de `POST /candidates` no se ejecutaba

Commit: pendiente.

### Por qué

`RegisterCandidateRequest` anotaba sus 7 records anidados con `@NotNull` pero
**sin `@Valid`**, y ni siquiera importaba `jakarta.validation.Valid`. Sin
`@Valid`, Hibernate Validator no desciende al objeto anidado (JSR-380 §5.7.1), así
que las **16 anotaciones** `@NotBlank`/`@NotNull` de los records hijos eran
**código muerto**.

Seis de esos campos caen en columnas `NOT NULL` reales (`person.curp`,
`person.first_name`, `person.last_name1`, `person_address.street`,
`person_address.exterior_number`, `person_address.postal_code`), así que omitirlos
dejaba que el `null` llegara hasta la base. El `INSERT` se difiere al commit, MySQL
lo rechaza, y el `DataIntegrityViolationException` caía en el catch-all de
`identity/GlobalExceptionHandler` — un **500 "intenta más tarde"** para lo que es un
problema de datos del cliente.

Lo que lo escondía: `school_city` (vía `ciudadPreparatoria`) era el único campo sin
anotación, y por eso fue el único que se notó. Los otros seis tienen el `@NotBlank`
puesto, así que leer el DTO da la impresión de que están validados.

### Qué cambió

- `@Valid` en los 7 componentes del record raíz, e `import jakarta.validation.Valid`.
- Las 16 anotaciones anidadas (y los 7 `@NotNull` de la raíz) llevan ahora
  `message` en español. **No hay bundle de locale en el repo**, así que sin
  `message` el 400 salía como `"must not be blank"` en inglés; se sigue la
  convención ya usada en `FichaPaymentAccessRequest` y `CreateRoleRequest`.
- `HighSchoolBackground` normaliza `schoolCity` a `""`. **No se rechaza con 400**:
  una escuela en México legítimamente no tiene ciudad capturable (el wizard solo
  pinta ese input cuando el bachillerato fue en el extranjero), y así lo confirman
  las 5 filas existentes.
- `identity.GlobalExceptionHandler`: `DataIntegrityViolationException` → `400`, como
  red para cualquier constraint no previsto. El mensaje es genérico a propósito: el
  texto de la excepción trae tabla y columna, y filtrarlas expone el esquema.
- `admission.GlobalExceptionHandler`: `DuplicateKeyException` → `409` con
  `ADMISSION_CANDIDATE_ALREADY_EXISTS`. Esto cierra una carrera real: el
  pre-chequeo `findByCurp` es read-then-write sin lock, así que dos registros
  simultáneos con el mismo CURP pasaban ambos el chequeo y el segundo terminaba en
  500 en vez del 409 que el código ya preveía.
- `Candidate.folio` pasa a `@Column(unique = true)`. `generateFolio()` usa
  `count(prefix) + 1`, también read-then-write, y sin el índice dos registros
  simultáneos sacaban el mismo folio **en silencio** — la peor falla posible,
  porque el folio es la llave con la que el Aspirante vuelve a pagar.

### Notas

- El índice se aplicó y verificó en `sisa` (cero folios duplicados en 5 candidatos).
  Para los demás ambientes está `sql/2026-09-30-candidate-folio-unique.sql`, porque
  `ddl-auto=update` solo lo crea en desarrollo.
- **Sin cambios en FRONT**: el wizard ya gatea con validación de cliente, así que
  los 16 campos siempre llegan poblados y ningún registro legítimo se rompe.
- Sin commit.

---

## [2026-09-30] El rechazo por cupo ya no publica el tamaño del cupo

Commit: pendiente.

### Por qué

El único mensaje que un Aspirante lee cuando su carrera se llenó era
`"Esta carrera alcanzó su cupo de N fichas."`. El número se quita: no hay
lugar, y es el Aspirante tiene que poder entender **por qué** su pago no se
puede iniciar.

Un número al lado de un tope invita la única pregunta que el sistema no puede
contestar — *"¿entonces cuándo se libera un lugar?"* — porque el cupo es un
tope y **no hay cola**: nadie programa una liberación, así que "intenta más
tan pronto" sería una promesa que el código no puede cumplir. Antes de este
cambio el texto ya prometía explícitamente que un lugar se liberaba si quien
lo tomó dejaba vencer su ventana, lo cual describía una lista de espera
inexistente.

La redacción anterior tampoco decía **qué** llenó el cupo, y eso se conserva:
el conteo incluye checkouts en vuelo, así que afirmar que le ganaste a quienes
ya pagaron sería falso cada vez que el último lugar sigue moviéndose.

### Nuevo texto

```
El cupo de esta carrera se agotó.
```

### Impacto en API

Ninguno en el contrato: `409` y el código `ADMISSION_QUOTA_REACHED` quedan
igual, y `message` sigue siendo la frase del backend en texto plano. Lo que
cambia es `message`. El front lo muestra textual y su propio componente
(`PagoNoDisponibleNotice`) está escrito para eso: *"Si el backend se equivoca
en el texto, el arreglo es en el backend, no aquí."*

Un integrador que comparara el mensaje contra un patrón con el número verá
cambiar la cadena. Es intencional.

### Archivos modificados

- `CheckoutSlotClaimer`: `quotaReachedMessage()` deja de recibir `maxCandidates`.
  El mensaje ya no es función del cupo, y con él se va la rama de
  singular/plural que solo existía para imprimir `"1 ficha"` contra
  `"N fichas"`. **`QuotaState.maxCandidates()` no cambia**: sigue siendo lo que
  la comparación de la línea anterior evalúa.
- `CheckoutSlotClaimerTest`: el mensaje se pinea completo, para que reintroducir
  el número rompa la compilación del test. Se elimina
  `theFullQuotaMessageIsSingularForASinglePlace`, que ya no tiene variante
  gramatical que verificar.
- `InitiateFichaPaymentUseCaseImplTest` y `GlobalExceptionHandlerTest`
  (admisión): fixtures que traían el texto viejo inventado.

### Pruebas

`mvn test`: **1026 en verde** (eran 1027; la diferencia es el test del
singular que se eliminó).

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
