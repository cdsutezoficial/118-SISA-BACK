# Changelog — 118-SISA-BACK

Todos los cambios relevantes del backend se documentan aquí en orden cronológico inverso.

---

## [2026-09-30] El barrido nocturno: lo que el navegador no resolvió, lo responde el banco

Commit: `e9f0725`.

### Qué se agrega

- `ReconcileFichaPaymentsUseCase` + `ReconcileFichaPaymentsUseCaseImpl`.
- `ReconcileFichaPaymentsJob` — `RECONCILIAR_PAGOS`, cron `0 5 0 * * *`.
- `OrderSettlementDecider` — la tabla §6 como función pura.
- `ConfirmFichaPaymentVerifiedUseCaseImpl` reescrito (ver abajo).

### Las 00:05, y por qué antes de las 00:10

`VENCEN_FICHAS` corre a las 00:10 y decide qué fichas no se pagaron a tiempo. Si la
conciliación corriera después, un pago capturado a las 23:59 se encontraría con la ficha
ya expirada, y el sistema estaría eligiendo creer el plazo sobre el dinero efectivamente
cobrado. Conciliar primero significa que toda captura ocurrida ya está registrada antes
de que el barrido de vencimientos mire la ficha.

### El bug que esto destapó: nadie escribía `CAPTURED`

`CheckoutAttempt.close()` tiene un guard escrito explícitamente para que un timeout
tardío no pise un `CAPTURED`, y ese `CAPTURED` no lo ponía nadie. Tres llamadas a
`closeAttempt` en todo el código: `ORDER_NOT_CREATED` al iniciar, `REJECTED` en el
liberar. Consecuencias:

1. Toda ficha pagada dejaba su intento abierto para siempre, así que el barrido
   re-consultaría al banco cada noche, para siempre.
2. Cada una de esas noches, el barrido pediría además `release()` sobre un pago `PAID`,
   que es un `IllegalStateException` en `requirePendingPayment`.

Ahora `confirm` cierra su intento como `CAPTURED`, y el invariante es "ficha pagada ⇒
ningún intento abierto", que es justo lo que hace que el barrido no vuelva a mirar esa
ficha.

### `confirm` cambió, y no es un afloje

Tres cambios, todos forzados por el barrido:

- **El `orderId` se valida contra `CheckoutAttempt`, no contra `payment.orderId`.** Esa
  columna se sobreescribe en cada reintento, así que había dos situaciones reales sin
  respuesta: la persona paga, el navegador vuelve a entrar al checkout antes de que
  llegue la confirmación, y la columna ya guarda el *segundo* pedido mientras el primero
  es el que capturó; y el barrido, cuyo trabajo es liquidar intentos que ya no son el
  pedido vivo. No es una pérdida de seguridad: nadie puede nombrar un pedido que no sea
  suyo, porque tiene que ser una fila de `checkout_attempt` con su `candidate_id`, y
  exigirse que siga **abierto** es lo que impide re-confirmar.
- **El monto se compara contra el del intento**, no el de la ficha. El javadoc de
  `CheckoutAttempt.amount` ya decía que ese es el número que se puso enfrente del
  Aspirante; la tariff se re-cotiza en cada checkout, así que la ficha pudo cambiar en
  medio.
- **La puerta es `capturedAny()`, no `result == SUCCESS`.** Este era el ítem 5 pendiente.
  `SUCCESS` es una etiqueta que el gateway imprime; `totalCapturedAmount > 0` es dinero
  que se movió. Una orden `SUCCESS` que no capturó nada no es un pago, y aceptarla era
  exactamente la forma de marcar pagada una ficha sin pago.

### Captura por debajo de la tarifa: se paga igual, se loguea

Si el intento capturado se alicuotó a un precio viejo y la tarifa viva es mayor, la ficha
se marca pagada y se loguea la diferencia.

No es una decisión de justicia sino una consecuencia de que las órdenes de EVO son
inmutables: el dinero está en el banco y no se puede recuperar. Dejarla `PENDING` con
captura es una ficha cuyo dueño va a volver a pulsar "Pagar" y a pagar dos veces, que es
justo lo que toda esta reconciliación existe para evitar. La tarifa se re-cotiza por
checkout, así que el hueco solo es alcanzable si el catálogo se movió entre dos intentos:
un hecho operativo para que Servicios Escolares lo concilie, nunca una razón para
retener una admisión ya cobrada.

### Se pregunta al banco incluso en fichas ya pagadas

Podría seem más simple saltarlas, y estaría mal dos veces: dejaría esas filas abiertas
para siempre —no existe un valor "superseded" para una orden que el banco nunca
rechazó— y decidaría el destino de un intento a partir de nuestra propia contabilidad en
vez de la única parte que sabe. Lo que una ficha pagada **sí** omite es el `release`: su
lugar es suyo para siempre. Y cuando el banco dice que esa orden se rechazó, el motivo
que se registra es `REJECTED`, porque eso es lo que él dijo, aunque otra orden sí haya
funcionado.

### Aislamiento por intento

`EvoPaymentGatewayException` se captura y se sigue. Es la falla que el barrido jamás debe
convertir en liberación: "no pudimos preguntar" y "el banco rechazó" tienen que quedar
distinguibles, o una caída del banco reparte lugares cuyos pagos siguen en vuelo. Cualquier
otra excepción también se cuenta y se sobrevive — un intento envenenado no puede abandonar
los otros treinta y nueve.

Tampoco es `@Transactional`: llama a un tercero una vez por intento, así que una
transacción alrededor del bucle dejaría una abierta mientras el banco contesta y
reventaría cuarenta pagos ya liquidados porque el cuarenta y uno tardó.

35 tests nuevos/reescritos (14 del barrido, 15 de `confirm`, 8 del decider). 1131 en total.

---

## [2026-09-30] La descripción del pedido llega sin acentos: se iba a ver "AdmisiÃ³n" en el estado de cuenta

Commit: `501060a`.

### Qué estaba roto

Un pago 3DS2 real en el sandbox (3DS `MANDATORY`, ACS emulator, resultado `(Y)`) llegó
a EVO con la descripción correcta y el gateway nos la devolvió así:

```json
"description": "Ficha de AdmisiÃ³n ADM-2026-000003"
```

La "ó" salió como UTF-8 y se decodificó como Latin-1 en algún punto. Esa cadena es la
que el titular de la tarjeta ve en su estado de cuenta: no es un dato interno, es
texto que se lleva a otro banco.

### Por qué la respuesta es quitar el acento y no arreglar el charset

Enviamos `Content-Type: application/json` sin charset
(`EvoPaymentsGatewayAdapter.java:123`), así que **esta parte no declara la
codificación**: no somos nosotros quienes elegimos cómo se lee, y no hay nada que
corregir del lado del request para que el gateway lo lea bien. Perseguir el
`charset` contra el procesador es pelear por algo que él controla.

La salida barata es no depender de él: enviar ASCII puro.

### Por qué no cuesta nada

El folio ya identifica la ficha de forma única (`ADM-2026-000003`). La descripción
nunca fue un identificador, solo texto para el humano que revisa su banco. Perder el
tilde no quita información y elimina la clase de bug entera, no este caso.

`orderDescriptionIsAsciiSoTheBankStatementCannotShowMojibake` fija que la cadena sea
ASCII puro, sin fijar la redacción exacta — esa es la propiedad que importa, y así un
cambio de copy futuro no rompe el test.

34 tests verdes en `InitiateFichaPaymentUseCaseImplTest`.

---

## [2026-09-30] Endpoint de liberación: el navegador avisa cuando el Aspirante abandona el pago

Commit: `37c4c7a`.

### Qué cambia

`POST /candidates/{id}/payments/release` (`permitAll`, como los demás endpoints del
portal). Lo llama el front en `onEvoError` y `onEvoTimeout`, que hasta ahora solo
limpiaban estado local.

- `ReleaseFichaPaymentSlotUseCase` + `ReleaseFichaPaymentSlotUseCaseImpl`.
- `ReleaseFichaPaymentRequest` / `ReleaseFichaPaymentResponse`.
- `ReleaseOutcome`: `SLOT_RELEASED`, `PAYMENT_IN_PROGRESS`, `PAYMENT_CAPTURED`,
  `RETAINED_UNEXPLAINED`.

### Qué estaba roto

El navegador le avisaba al Aspirante de que su sesión había expirado, pero el backend
no se enteraba. El lugar del cupo seguía apartado: la siguiente persona que pulsaba
"Pagar" en esa carrera recibía "El cupo de esta carrera se agotó" por un lugar que el
primer Aspirante ya había soltado. Y el primero tampoco podía pagar aunque quedara
sitio, porque el suyo seguía tomado.

### Lo importante: el endpoint no le cree al navegador

El nombre y el llamador invitan a soltar el lugar porque el navegador dijo que se
rindió. **Eso sería un oversell.** Un timeout es el fallo ambiguo por definición: la
petición pudo haber llegado a EVO y ser capturada un segundo después, aunque la
respuesta se perdiera. Liberar sobre la palabra del navegador entregaría el último
lugar de una carrera a otra persona mientras el dinero de la primera está en camino.

Así que el cuerpo es una **pregunta**, y `Retrieve Order` la responde. Se aplica la
misma tabla de §6 que usará el barrido, y solo una fila escribe:

| Retrieve Order | Lugar | `outcome` |
|---|---|---|
| `FAILURE` o nodo `error`, sin captura | **se suelta** | `SLOT_RELEASED` |
| capturó dinero | se queda | `PAYMENT_CAPTURED` |
| `SUCCESS` sin captura | se queda | `RETAINED_UNEXPLAINED` |
| `PENDING` / desconocido | se queda | `PAYMENT_IN_PROGRESS` |

Tres consecuencias de esa tabla:

- **Solo `SLOT_RELEASED` cierra el intento, y con `REJECTED`.** Los otros tres lo
  dejan **abierto** a propósito: cerrarlo sobre un "todavía no sabemos" es
  justamente cómo se pierde una captura que llegó después de la llamada. `REJECTED` y
  no `SESSION_TIMEOUT`/`ERROR` porque esos registran lo que vio el navegador, y esta
  fila se cierra con lo que dijo el banco.
- **La captura gana al veredicto.** Un `FAILURE` que capturó algo no es un rechazo: el
  dinero entró, el lugar se queda y el barrido marca la ficha pagada.
- **Un corte de EVO (502) no cambia nada.** Si liberara al salir, un banco
  inalcanzable sería indistinguible de un rechazo.

Rechazos, todos antes de llamar al gateway: sin ficha 404, ya pagada 409 (una ficha
pagada ocupa su lugar para siempre), y `orderId` ausente o de otra ficha 400 — esa
última es la única forma de abuso posible en un endpoint público, y es la que
obliga a que el orden sea el de la propia ficha en vez de "el último intento".

`slotReleased` viaja aparte de `outcome` porque el caso común es `false` y son
situaciones opuestas: o el lugar es tuyo y reintentas, o tu dinero ya entró.

1103 tests verdes.

---

## [2026-09-30] Tres pruebas para el re-precio de la ficha y el reintento de cobro

Commit: `0bb1222`.

### Qué cambia

Solo pruebas. La producción de la Fase 3 ya estaba y estos tres tests son lo que la
protege.

### Por qué faltaban

El re-precio vive en `CheckoutSlotClaimer#claim`, no en
`InitiateFichaPaymentUseCaseImpl`. Eso dejó la regla partida en dos mitades que nunca
se juntaron: el test del use case comprobaba que al *claimer mock* le llegaba el monto
vivo, y el test del claimer comprobaba que `save` escribía el monto vivo. Si alguien
borraba la llamada a `reprice()`, las dos mitades seguían en verde.

### Qué fijan

- **`theFichaItselfIsRepricedToTheLiveTariffBeforeEvoIsAsked`** — un checkout con el
  claimer real y la fila registrada en 500.00 termina guardando 550.00 en **esa misma
  fila**, y es el mismo 550.00 que ve el banco. El hilo completo, no las mitades.
  Importa porque la confirmación después compara la captura del banco contra
  `admission_payment.amount`: un 500.00 viejo ahí rechazaría todos los pagos legítimos
  de una carrera que cambió de precio.
- **`aRetryReusesThePaymentRowAndAppendsASecondAttempt`** — dos checkouts de la misma
  ficha dejan **una sola fila de pago** (las cuatro escrituras son la misma instancia)
  y **dos `CheckoutAttempt` abiertos** con `orderId` distinto. La fila de pago queda
  con la orden más reciente —la que la confirmation va a verificar—, pero el historial
  conserva las dos, que es lo que permite reconciliar contra el banco la orden que la
  applicant sí abandonó. La unicidad de `order_id` es una restricción de base y no se
  puede probar aquí; lo que este test fija es la conducta que la hace satisfacible.
- **`omitsTheEvoOrderRowWhenNoCheckoutWasEverStarted`** — el PDF de quien todavía no
  ha empezado a pagar no lleva la fila "Orden de pago (EVO)", ni como texto vacío ni
  como `null`. `orderId` es `null` entre que alguien se registra y pulsa "Pagar", que
  es justo para quien se imprime esta ficha. El positivo ya lo cubría el test de la
  ficha completa, así que la fila no puede haberse eliminado de plano.

1086 tests verdes.

---

## [2026-09-30] La ficha vence cuando cierra el proceso, no solo a los 10 días

Commit: `42427c1`.

### Qué cambia

La regla de vigencia de una ficha queda en un solo lugar (`FichaPaymentWindow`) y pasa
a ser el **menor** de dos límites: `min(registeredAt + N días, closesAt del proceso)`.

Antes, el barrido `VENCEN_FICHAS` solo conocía el plazo propio de la ficha. Eso dejaba
un hueco visible: un proceso que cerraba el día 20 seguía con sus fichas sin pagar en
estado "Registrado" hasta el día 30, el portal seguía ofreciendo pagar una venta que ya
había acabado, y al pulsar la applicant recibía un 409 que le decía que su plazo de 10
días había terminado — que no era la razón.

- `ExpireStaleFichaPaymentsUseCaseImpl` resuelve el `closesAt` de cada proceso y expira
  también por cierre. El cierre se lee **una vez por proceso**, memoizado por
  `admissionConfigId`, porque el barrido recorre todas las fichas `REGISTERED` del
  sistema. Un config borrado no aborta la noche: esa ficha cae a su plazo propio, la
  dirección conservadora (vence después, nunca antes).
- El candado por CURP de `RegisterCandidateUseCaseImpl` también pasa a respetar
  `closesAt`. Antes retenía una persona hasta 10 días después de que su proceso cerrara,
  con un mensaje pidiéndole esperar una ventana que ya no existía.
- El checkout tenía **dos** gates con **dos** errores distintos: "tu ficha venció" y
  "la venta cerró". Ahora es uno solo —`FichaPaymentExpiredException`— porque para la
  applicant los dos hechos son el mismo y cuál de los dos límites llegó primero no es
  algo sobre lo que pueda actuar.
- `PaymentAccess` y `FichaPaymentAccessResponse` agregan `candidateStatus` y
  `paymentExpired`. Son dos preguntas distintas: el estado es lo que el barrido dejó
  escrito, el flag es lo cierto hoy. En la ventana entre que un plazo vence y las 00:10
  se diferencian, y al portal le obedece el flag — es lo que el checkout exige.
- `paymentDeadline` en `payment-access` y en la ficha PDF pasa a leer el `closesAt`
  **vivo** en vez del snapshot `registrationDeadline` congelado en el ticket. Cerrar un
  cohorte anticipadamente tiene que acortar la promesa de la pantalla, y la reimpresión
  del PDF es donde una promesa vieja haría más daño.

### Por qué el barrido sigue siendo el único que escribe `PAYMENT_EXPIRED`

El candado por CURP trata `REGISTERED` fuera de ventana como ya libre, y las consultas
de cupo liberan una reserva vencida por fechas, no por un barrido. Los dos toleran el
hueco entre el vencimiento y las 00:10, y ambos dependen de que nadie más escriba ese
estado.

### Por qué `ProgramAdmissionConfigSalesClosedException` sigue existiendo

La usa el registro, que sí tiene dos límites distintos y dos mensajes distintos que
sí significan algo ("la venta abre el 01/09" ≠ "la venta cerró el 30/09").

## [2026-09-30] El barrido le pregunta al banco, y la orden del pago se conoce desde el inicio

Commits: `de0a1f4`, `dcb98e6`.

### Qué cambia

`CheckoutAttempt` nace en el dominio y el `orderId` se genera **antes** de llamar al
gateway, no después:

- `CheckoutAttempt` + `CheckoutAttemptCloseReason` (`STARTED`, `SESSION_TIMEOUT`,
  `ERROR`, `ORDER_NOT_CREATED`, `REJECTED`, `CAPTURED`, `ORDER_EXPIRED`), con
  `order_id` único y escritura append-only: cada reintento agrega su fila, ninguna se
  pisa.
- `CheckoutSlotClaimer.openAttempt()` / `closeAttempt()` en `REQUIRES_NEW`, para que el
  intento sobreviva a un rollback posterior.
- `InitiateFichaPaymentUseCaseImpl` genera el `orderId` y abre el intento antes de
 gateway. Ante un rechazo definitivo cierra el intento con `ORDER_NOT_CREATED` y suelta
  el lugar; ante un fallo ambiguo (`orderMayHaveBeenCreated`) lo deja abierto, porque el
  banco puede tener una orden que se capture después.

Esto elimina de raíz el caso "lugar apartado sin número de pedido" que obligaba a
soltar a ciegas por antigüedad: el `orderId` se conoce desde el principio, así que
siempre hay con qué preguntar al banco.

### Por qué el adapter mira más campos

`EvoOrderStatus` crece con lo que el barrido necesita para decidir: los cuatro
`total*Amount`, `creationTime`, `lastUpdatedTime` y `error`. Los campos están
documentados como ALWAYS PROVIDED en `Referencias de API.txt` (sección plana de
`Retrieve Order`), así que son datos reales y no inferencias.

**No se agrega `gatewayCode`:** no viene en `Retrieve Order` —vive en las respuestas de
transacción y en `Retrieve Transaction, que son endpoints distintos—, y no se llega a
ellos desde un `orderId`. Esa ausencia es la razón por la que un `SUCCESS` sin captura
se **retiene y se registra** en vez de investigarse: no hay campo que diga por qué, y
soltar ese lugar le robaría el cupo a alguien que quizá sí pagó.

El `error` solo se arma con un nodo `error` real. Se quitó el fallback que inventaba una
línea de error a partir de `status`: ese campo es de la capa EVO y no está entre los
documentados, así que usarlo convertía un estado normal en curso en un rechazo.

`EvoOrderStatus` conserva constructores de 3 y 4 campos para el camino de confirmación,
que solo lee `result` y `amount`. Con `SUCCESS` el monto capturado se toma igual al
amount, de modo que ese camino conserva el significado que tenía antes del barrido.

### Tests

`CheckoutSlotClaimerTest` cubre apertura y cierre de intentos; `InitiateFichaPaymentUseCaseImplTest`
cubre el orden de las escrituras y el caso ambiguo. 1072 en verde.

---

## [2026-09-30] Un lugar lo suelta su propia ficha, no el catálogo

Commit: pendiente.

### Qué cambia

Las tres consultas de ocupación dejan de decidir la vigencia de una ficha preguntando
al catálogo de pagos. Ahora la decide la ficha: su `registeredAt` y el `closesAt` de su
proceso. El `EXISTS` sobre `PaymentConcept`/`PaymentRate` desapareció de las tres.

El `EXISTS` era la forma en que una reserva se liberaba, y estaba mal por dos motivos
que no tienen nada que ver entre sí:

- **Decidía con la fecha equivocada.** La reserva moría cuando `available_until` del
  concepto de admisión pasaba, no cuando la ficha se cumplía su propio plazo. El
  calendario que la ventanilla edita pasó a ser la razón por la que alguien no podía
  pagar, y el checkout y el catálogo applicaban fechas distintas para la misma regla.
- **Recorría la escalera de precios completa** —programa, nivel, general— en cada
  intento de pago, que es la escritura más disputada del sistema, dentro de una
  transacción con candado sobre la fila del config.

La regla que queda es la que el negocio ya tenía escrita: una ficha registrada tiene 10
días naturales desde el registro y nunca sobrevive al cierre del proceso. Los dos
cortes son **fechas**, no instantes: `cand.registeredAt >= :registeredNoLaterThan` y
`cfg.closesAt >= :midnightToday`. Por eso una ficha emitida el 28 con ventas hasta el 30
se puede pagar todo el 30, y por eso una carrera que cierra a las 23:00 no se apaga a
esa hora.

Nada de esto necesita un job para funcionar. Una reserva se suelta porque ya no cuenta,
no porque alguien pasó a limpiar.

### `FichaPaymentWindow`

Nuevo, y es la parte que evita el problema de fondo. La aritmética de "cuánto vive una
ficha" está en un solo lugar y las tres consultas la llaman: el adapter de pagos, el
controller del selector y los propios tests. Antes esa cuenta estaba implícita en cada
copia de la consulta, y dos copias de una regla de fechas es exactamente cómo empiezan
a discrepar.

`latestPayableRegistration` es `today - N`, el espejo exacto de
`Candidate.paymentDeadline`, que suma `N` al día de registro. Escribirló como
`today - (N-1)` regalaría un día a todos y contradiría al vencimiento que calcula la
misma clase: el único bug que este método no puede tener es dos respuestas distintas a
"¿cuándo vence una ficha?" en el mismo lugar.

### La zona se lee del reloj, no del servidor

El adapter toma `clock.getZone()` y el controlador ya tenía el `Clock`. Nada usa
`ZoneId.systemDefault()`: `NOW` en los tests es 18:00Z, que en México es el 25 y en
UTC ya es el 26, así que un test que resolviera "hoy" en la zona equivocada se
sentaría a un día del límite que toda la clase existe para medir.

### Tests

`ProgramAdmissionConfigOptionsQueryIT` gana los dos bordes que antes no existían, porque
la regla nueva los hace distinguibles: una ficha en su **último** día todavía ocupa
lugar, y una de un proceso cerrado ayer lo suelta todo. La segunda se afirma por el
conteo y no por el selector, porque un config cerrado ya cae por la cláusula de
ventana y el selector no podría separar las dos razones.

El IT ya no siembra concepto de admisión ni tarifa. Su ausencia es el punto: lo que
sostiene una reserva son las fechas de la ficha, así que una carrera cuyo catálogo ni
siquiera está configurado conserva bien sus lugares en vez de reportarse vacía y
sobrevender. Y `CheckoutSlotClaimerConcurrencyIT` necesitaba el
`CheckoutAttemptRepositoryAdapter` en su `@Import`, que faltaba desde `de0a1f4`.

`Candidate.registeredAt` no tiene setter y no se le dio uno: el día de registro se fija
una vez y no es algo que el código de producción deba poder reescribir. Los tests que
lo necesitan reescriben la columna con SQL nativo después del insert.

39 en verde en los dos IT y `ProgramAdmissionConfigControllerTest`; el controlador
además afirma que el corte cae exactamente 10 días atrás, para que un día de
colchón se note como lo que es.

---

## [2026-09-30] El estado de pago se escribe, no se imprime

Commit: pendiente.

### Por qué

El PDF imprimía `Estado de pago: PAID`. No es un detalle de estilo: la persona lleva
este archivo a ventanilla en la mano y lo lee en casa, y `PAID` no es un estado, es el
nombre de una columna.

### Qué cambió

- `CandidateFichaPdfService` ya no llama `paymentStatus().name()`. El enum entra por
  `text()`, que resuelve cada enum a su texto en español: `PENDING` → "Pendiente",
  `PAID` → "Pagado". Si mañana aparece un enum nuevo en una fila, el compilador obliga
  a decidir qué se escribe en el documento.
- El enum **no se tocó**. `AdmissionPaymentStatus` sigue siendo `PENDING, PAID` y
  sigue persistiéndose: es estado real, y las consultas de cupo lo filtran
  (`countPaidByAdmissionConfigId`, `AdmissionPaymentOccupancyQueries`). Un enum se
  queda cuando el motor lo usa para decidir, y se cae cuando solo clasifica un texto —
  que es lo que pasó con `EmploymentType`, en la entrada de abajo.
- **El layout del PDF no se modificó.** Se intentó una pasada visual (encabezado con
  banda verde, tarjeta de pago, filas alternas, barra lateral en las secciones) y se
  revirtió a petición del negocio: el documento ya era conocido por quien lo recibe y
  se prefiere no cambiarlo. Este commit es solo el texto del enum.

### Tests

`printsThePaymentStatusAsSpanishTextAndNeverAsTheEnumName` afirma "Pagado" y
"Pendiente", y **nega** "PAID" y "PENDING": una aserción positiva sola pasaría aunque el
nombre del enum quedara en otra parte del texto. El resto del archivo no necesitó
cambios, porque el layout y los valores se siguen extrayendo igual.

---

## [2026-09-30] "Tipo de trabajo" es texto libre

Commit: pendiente.

### Por qué

El campo no se llenaba, y no era cosa del PDF. `CandidateController.toEmploymentType`
traducía "Tiempo completo" → `PERMANENT` y "Medio tiempo" → `TEMPORARY`, y **todo lo
demás caía en `default -> null` sin error**. Dos formularios escriben ese campo y
ninguno se limita a esos dos valores: el de admisión es un `TextField` libre, y el del
inscripción ofrece un catálogo de cuatro del que "Freelance" y "Negocio propio"
tampoco sobrevivían. El dato nunca llegaba a la base, así que el PDF imprimía `-` con
total honestidad.

### Qué cambió

- `EmploymentInfo.employmentType` es `String`. Se borró el enum `EmploymentType`.
- `tipoTrabajo` viaja tal cual llega, con `trimToNull` para que el `''` que manda el
  wizard por campos que nunca renderizó no se guarde como un valor lleno (imprimiría
  una respuesta donde la persona no contestó nada).
- Los puertos (`RegisterCandidateUseCase.Ingresos`, `FichaData.Ingresos`) y el PDF
  siguen el mismo tipo.
- **No hace falta migración de esquema**: `@Enumerated(STRING)` ya guardaba el texto en
  un `varchar(255)`, y el DDL generado lo confirma. Lo único pendiente son dos
  `UPDATE` para los datos que el enum dejó en inglés:

```sql
UPDATE person_employment_info SET employment_type = 'Tiempo completo' WHERE employment_type = 'PERMANENT';
UPDATE person_employment_info SET employment_type = 'Medio tiempo'   WHERE employment_type = 'TEMPORARY';
```

### Tests

`keepsTheEmploymentTypeExactlyAsTheApplicantWroteIt` usa "Negocio propio" en el
fixture —justo el valor que el switch descartaba— y exige que salga literal en el PDF.

---

## [2026-09-30] La referencia de pago es el folio, sin fecha dentro

Commit: pendiente.

### Por qué

`generateReference` armaba `REF-{yyyyMMdd}-{folioSeq}` con un `LocalDate.now()` pelado
—la zona por defecto del servidor— dentro de una cadena que el pagador lee en
ventanilla y que se imprime en el PDF. Eso hace que la referencia dependa de *cuándo*
se emitió la ficha en vez de *cuál* ficha es: no se puede recalcular en ningún otro
lado, y dos fichas del mismo día en servidores distintos pueden discrepar. Decisión
11.1.

### Qué cambió

- `RegisterCandidateUseCaseImpl.generateReference(folio)` pasa a `REF-{folio}` puro,
  con el prefijo en una constante. Sin reloj, sin sufijo. Sigue siendo única porque el
  folio lo es. `OrderIdBuilder` conserva su sufijo aleatorio a propósito: un pedido del
  banco tiene que ser único por **intento**, y dos intentos de una misma ficha
  comparten referencia.
- `ConfirmAdmissionPaymentUseCaseImpl`: el javadoc decía "misma derivación que la
  referencia de pago", que con lo anterior ya era falso. Ahora explica que el recibo
  (`REC-{yyyyMMdd}-{seq}`) **sí** lleva fecha y a propósito, porque se emite una vez al
  pagar y ya está estampado en correos enviados. Su formato no se toca —cambiarlo no
  compra nada—, pero se deja escrito que esa lectura de `LocalDate.now()` no es un
  patrón que haya que copiar.
- `CandidateFichaResponse` gana `orderId` nullable, que era la mitad pendiente del ítem
  5 de la Fase 3. `FichaData` ya lo traía y el PDF ya lo imprimía solo cuando existe;
  el DTO lo expone para que la pantalla pueda hacer la misma distinción entre "nunca
  intentó pagar" y "el pago es el 12345".
- Se corrigió el javadoc de `GetCandidateFichaUseCase`, que decía que el DTO mapeaba
  `FichaData` uno a uno cuando hace tiempo que no es así.

### Tests

`RegisterCandidateUseCaseImplTest` ahora fija la referencia contra el folio
(`"REF-" + result.folio()`) en vez de con `startsWith("REF-")`: así la prueba sigue
valiendo el año que sea y demuestra que no quedó ninguna fecha dentro. Suite completa
en verde.

---

## [2026-09-30] El checkout cobra la tarifa viva, no la cotizada al registrar

Commit: pendiente.

### Por qué

El monto de la ficha se congelaba en el registro
(`RegisterCandidateUseCaseImpl`) y el checkout cobraba ese valor congelado. Una
edición de tarifa entre emitir la ficha y pagarla no llegaba al Aspirante, aunque
§1.3 pide que gane el precio del clic. La fila de pago **sigue naciendo en el
registro** (no se mueve al cobro, que era el plan original de Fase 3); lo que
cambia es el monto.

### Qué cambió

- `AdmissionPayment.reprice(amount)`: sobrescribe `amount` mientras la ficha está
  `PENDING`; el javadoc de `amount` pasa a decir que el valor del registro es una
  cotización y el del cobro es lo que se cobró.
- `InitiateFichaPaymentUseCaseImpl`: tras los tres candados, cotiza en vivo con
  `FichaAmountResolver.resolve(programId, hoy)` y usa ese monto en la orden de
  EVO. `requirePaymentWindowOpen` ahora devuelve la config, para no resolver el
  programa dos veces.
- `CheckoutSlotClaimer.claim(candidateId, amount)`: persiste el reprecio en la
  misma transacción que toma el cupo —antes de llamar a EVO— para que el monto
  que la confirmación compara contra el banco sea el del clic. Un rechazo por
  cupo no repricia, porque el chequeo de capacidad corre antes.
- `FichaAmountResolver`: javadocs actualizados; antes afirmaban que el monto
  quedaba congelado en el registro.

### Tests

`InitiateFichaPaymentUseCaseImplTest` (la orden lleva el monto vivo y el claim lo
recibe), `CheckoutSlotClaimerTest` (el claim guarda el reprecio) y el IT de
concurrencia actualizado. Suite completa en verde, 0 fallos.

---

## [2026-09-30] La ficha muestra sus fechas reales y su precio vivo

Commit: pendiente.

### Por qué

La ficha imprimía como "Fecha límite de pago" el `available_until` del concepto de
pago (una fecha que el catálogo mueve y sobre la que el Aspirante no puede actuar),
no el plazo real de la ficha. Y el monto viajaba congelado desde el registro, así
que una corrección de tarifa no llegaba a una ficha ya emitida (§1.13, §3.2).

### Qué cambió

- `GetCandidateFichaUseCase.FichaData` gana `paymentDeadline`: el más corto entre el
  cierre de la venta y `registeredAt + N` días de la ficha. Es la fecha que se
  imprime bajo "Fecha límite de pago"; `paymentClosesOn` queda como frontera de
  motor y deja de mostrarse. `GetCandidateFichaUseCaseImpl` recibe `Clock` y el
  `deadline-days`, y cotiza el monto en vivo desde el catálogo (se omite si el
  programa no tiene precio hoy).
- `AccessFichaPaymentUseCase.PaymentAccess` gana `paymentDeadline`, calculado con
  la misma regla, para que la pantalla de "vuelve a pagar" no mienta la fecha.
- `RegisterCandidateUseCase.FichaPayment` gana `paymentDeadline`, para que la
  pantalla de registro confirme la misma fecha que luego se ve en el portal.
- `CandidateFichaResponse`, `FichaPaymentAccessResponse` y
  `CandidateRegistrationResponse.PaymentResponse` exponen `paymentDeadline`.
- `CandidateFichaPdfService`: "Monto a pagar" usa el monto vivo (se omite si es
  nulo) y "Fecha límite de pago" usa `paymentDeadline`, mostrada solo cuando difiere
  de "Fecha límite de inscripción".

### Tests

`GetCandidateFichaUseCaseImplTest`, `AccessFichaPaymentUseCaseImplTest`,
`CandidateFichaPdfServiceTest` y `CandidateControllerTest` cubren la fecha visible
(mínimo de los dos relojes) y el monto vivo, y fijan que el `available_until` del
concepto ya no se imprime. Suite completa en verde, 1063 tests, 0 fallos.

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
