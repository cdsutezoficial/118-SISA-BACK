package mx.edu.utez.sisa.admission.infrastructure.web;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ChangeCandidateProgramUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateEditFormUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateAccountUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase.CandidateListItem;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase.ListCandidatesQuery;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase.ListCandidatesResult;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.AntecedentesEscolares;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Contacto;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.DatosGenerales;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Domicilio;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.InformacionComplementaria;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Ingresos;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.SeleccionCarrera;
import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;
import mx.edu.utez.sisa.admission.infrastructure.notification.CandidateFichaMailService;
import mx.edu.utez.sisa.admission.infrastructure.pdf.CandidateFichaPdfService;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CandidateEditFormResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CandidateFichaResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CandidateListItemResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CandidateListResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CandidateRegistrationResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CandidateAccountResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CandidateStaffDetailResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CheckoutInitiationRequest;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CheckoutInitiationResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.FichaPaymentAccessRequest;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.FichaPaymentAccessResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.PaymentConfirmationResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.RegisterCandidateRequest;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.ReleaseFichaPaymentRequest;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.ReleaseFichaPaymentResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.VerifyFichaPaymentRequest;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidCandidateFichaDataException;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;
import mx.edu.utez.sisa.identity.infrastructure.security.LlaveMxVerificationTicketService;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxVerificationRequiredException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Public portal endpoints for the admission ticket. Deliberately the ONLY
 * controller in the repo registered for anonymous access:
 * {@code identity.SecurityFilterConfig} grants {@code POST /candidates},
 * the ficha read/PDF and the payment endpoints {@code permitAll} (the
 * applicant has no session); every admin-facing endpoint stays on the
 * {@code ADMIN}/{@code SERVICIOS_ESCOLARES} matchers.
 *
 * <p>This controller is the WEB layer's translation boundary: it maps the
 * frontend ficha payload ({@code RegisterCandidateRequest}, string-typed:
 * {@code fechaNacimiento} {@code dd/MM/yyyy}, {@code sexo}
 * {"Femenino"/"Masculino"/"Hombre"/"Mujer"}, {@code estadoCivil}
 * {"Soltero/a", …}, {@code tipoTrabajo} free text, {@code horaInicio}/{@code
 * horaFin} {@code HH:mm}, {@code cctConfirmacion}) into the typed domain
 * command ({@code Gender}, {@code MaritalStatus}, {@code LocalDate}/{@code
 * LocalTime}, {@code cctConfirmed}), keeping the domain port purist.
 * String→enum and text→date/time mappings live HERE, not in
 * {@code RegisterCandidateUseCaseImpl}.
 *
 * <p>The {@code promo} field is deliberately not included: {@code modalidad}
 * is defined by the chosen program, not stored on {@code Candidate}
 * ({@code 03-admision.md}).
 */
@RestController
@RequestMapping("/candidates")
public class CandidateController {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	private final RegisterCandidateUseCase registerCandidateUseCase;

	private final AccessFichaPaymentUseCase accessFichaPaymentUseCase;

	private final ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase;

	private final InitiateFichaPaymentUseCase initiateFichaPaymentUseCase;

	private final ReleaseFichaPaymentSlotUseCase releaseFichaPaymentSlotUseCase;

	private final GetCandidateFichaUseCase getCandidateFichaUseCase;

	private final GetCandidateAccountUseCase getCandidateAccountUseCase;

	private final ListCandidatesUseCase listCandidatesUseCase;

	private final GetCandidateEditFormUseCase getCandidateEditFormUseCase;

	private final UpdateCandidateUseCase updateCandidateUseCase;

	private final ChangeCandidateProgramUseCase changeCandidateProgramUseCase;

	private final CandidateFichaMailService fichaMailService;

	private final CandidateFichaPdfService fichaPdfService;

	private final PaymentAccessRateLimiter paymentAccessRateLimiter;
	private final LlaveMxVerificationTicketService verificationTicketService;
	private final boolean llaveMxRequired;

	public CandidateController(RegisterCandidateUseCase registerCandidateUseCase,
			AccessFichaPaymentUseCase accessFichaPaymentUseCase,
			ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase,
			InitiateFichaPaymentUseCase initiateFichaPaymentUseCase,
			ReleaseFichaPaymentSlotUseCase releaseFichaPaymentSlotUseCase,
			GetCandidateFichaUseCase getCandidateFichaUseCase, ListCandidatesUseCase listCandidatesUseCase,
			GetCandidateEditFormUseCase getCandidateEditFormUseCase, UpdateCandidateUseCase updateCandidateUseCase,
			ChangeCandidateProgramUseCase changeCandidateProgramUseCase,
			GetCandidateAccountUseCase getCandidateAccountUseCase,
			CandidateFichaMailService fichaMailService,
			CandidateFichaPdfService fichaPdfService, PaymentAccessRateLimiter paymentAccessRateLimiter,
			LlaveMxVerificationTicketService verificationTicketService,
			@Value("${sisa.admission.candidate.llave-mx-required:false}") boolean llaveMxRequired) {
		this.registerCandidateUseCase = registerCandidateUseCase;
		this.accessFichaPaymentUseCase = accessFichaPaymentUseCase;
		this.confirmFichaPaymentVerifiedUseCase = confirmFichaPaymentVerifiedUseCase;
		this.initiateFichaPaymentUseCase = initiateFichaPaymentUseCase;
		this.releaseFichaPaymentSlotUseCase = releaseFichaPaymentSlotUseCase;
		this.getCandidateFichaUseCase = getCandidateFichaUseCase;
		this.getCandidateAccountUseCase = getCandidateAccountUseCase;
		this.listCandidatesUseCase = listCandidatesUseCase;
		this.getCandidateEditFormUseCase = getCandidateEditFormUseCase;
		this.updateCandidateUseCase = updateCandidateUseCase;
		this.changeCandidateProgramUseCase = changeCandidateProgramUseCase;
		this.fichaMailService = fichaMailService;
		this.fichaPdfService = fichaPdfService;
		this.paymentAccessRateLimiter = paymentAccessRateLimiter;
		this.verificationTicketService = verificationTicketService;
		this.llaveMxRequired = llaveMxRequired;
	}

	/**
	 * Staff-facing candidate list ("Candidatos" screen). Every filter is optional
	 * and the whole query is scoped server-side from the JWT: a
	 * {@code DIRECTOR_DIVISION} caller is restricted to their own division's
	 * programs (RN-ADM-004), so {@code divisionId} is deliberately NOT a
	 * request parameter. Unlike the public ficha endpoints, this one requires a
	 * session ({@code SecurityFilterConfig}) and its permission
	 * ({@code CANDIDATES_READ}, {@code PermissionRegistry}).
	 *
	 * @param status    optional — exact {@link CandidateStatus}
	 * @param programId optional — filter by chosen program
	 * @param periodId  optional — filter by the config's destination period
	 * @param search    optional — matches folio, CURP or name
	 */
	@GetMapping
	public ResponseEntity<CandidateListResponse> listCandidates(@RequestParam(required = false) CandidateStatus status,
			@RequestParam(required = false) UUID programId, @RequestParam(required = false) UUID periodId,
			@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Authentication authentication) {
		UUID callerId = UUID.fromString(authentication.getName());
		ListCandidatesResult result = listCandidatesUseCase
				.listCandidates(new ListCandidatesQuery(callerId, status, programId, periodId, search, page, size));
		return ResponseEntity.ok(new CandidateListResponse(
				result.items().stream().map(CandidateController::toItem).toList(), result.totalElements(),
				result.totalPages(), result.page(), result.size()));
	}

	/**
	 * Public, unauthenticated ficha registration (RF-AUTH-003).
	 *
	 * <p>{@code llaveMxVerified} is no longer taken on faith. It is derived from
	 * the signed, short-lived ticket the Llave Morelos callback leaves in an
	 * HttpOnly cookie: a client cannot mint one, so nobody can post
	 * {@code true} and claim a verification that never happened.
	 *
	 * <p>{@code @Value} flag {@code sisa.admission.candidate.llave-mx-required}
	 * gates how strict that is. While OFF — the default, preserving the interim
	 * manual registration that shipped before the integration — a missing ticket
	 * still falls back to the flag in the body, so existing deployments and
	 * environments without LlaveMX credentials keep working. Once the hub
	 * credentials are in place, turn it ON and the request boolean is ignored
	 * entirely: no ticket, no ficha.
	 */
	@PostMapping
	public ResponseEntity<CandidateRegistrationResponse> register(
			@Valid @RequestBody RegisterCandidateRequest request, HttpServletRequest httpRequest) {
		boolean verified = resolveLlaveMxVerification(request, httpRequest);
		RegisterCandidateCommand command = toCommand(request, verified);
		RegisterCandidateUseCase.CandidateRegistrationResult result = registerCandidateUseCase.register(command);
		return ResponseEntity.status(HttpStatus.CREATED).body(CandidateRegistrationResponse.from(result));
	}

	/**
	 * @return whether the ficha may be recorded as LlaveMX-verified
	 * @throws LlaveMxVerificationRequiredException when this environment demands
	 *                                             a ticket and the browser has
	 *                                             none (or a stale one)
	 */
	private boolean resolveLlaveMxVerification(RegisterCandidateRequest request, HttpServletRequest httpRequest) {
		String ticket = readVerificationTicket(httpRequest);
		if (ticket != null) {
			// Throws on an invalid or expired ticket. Not swallowed: an expired
			// one must not quietly downgrade to "unverified" and let a
			// half-verified applicant through.
			Claims claims = verificationTicketService.verify(ticket);
			assertTicketMatchesTheFicha(claims, request);
			return true;
		}
		if (llaveMxRequired) {
			throw new LlaveMxVerificationRequiredException(
					"Debes verificar tu identidad con Llave Morelos antes de registrar tu ficha.");
		}
		return request.llaveMxVerified();
	}

	/**
	 * Binds the ticket to the ficha it is being used for.
	 *
	 * <p>A valid ticket only proves that <em>someone</em> proved their identity to
	 * Morelos in this browser. Without this check the obvious abuse is trivial:
	 * verify your own account once, then submit any other CURP in the body and
	 * the ficha is stored as {@code llaveMxVerified} while describing a different
	 * person entirely. Comparing the CURP the provider attested with the CURP in
	 * the request closes that gap, and it is the one claim worth enforcing
	 * because CURP is what identifies a ficha in this system.
	 *
	 * <p>A ticket carrying no CURP (the provider did not return one) is NOT
	 * allowed to pass here: it cannot be bound to anything, so it is treated as
	 * unproven rather than as good-enough.
	 */
	private static void assertTicketMatchesTheFicha(Claims claims, RegisterCandidateRequest request) {
		String attested = claims.get("curp", String.class);
		if (attested == null || attested.isBlank()) {
			throw new LlaveMxVerificationRequiredException(
					"La verificacion con Llave Morelos no incluye una CURP, asi que no confirma tu identidad. Intenta de nuevo.");
		}
		if (!normalizeCurp(attested).equals(normalizeCurp(request.datosGenerales().curp()))) {
			throw new LlaveMxVerificationRequiredException(
					"La CURP que registraste no coincide con la verificada con Llave Morelos.");
		}
	}

	/**
	 * CURPs are compared case- and whitespace-insensitively: the provider may
	 * return lower-case or spaced, the applicant may type either, and none of
	 * that is a different person. Whitespace inside the string is stripped
	 * rather than trimmed, because a CURP is fixed-width — a value that needs
	 * interior characters removed was mistyped, not reformatted.
	 */
	private static String normalizeCurp(String curp) {
		return curp == null ? "" : curp.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
	}

	private static String readVerificationTicket(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (LlaveMxVerificationTicketService.COOKIE_NAME.equals(cookie.getName())
					&& cookie.getValue() != null && !cookie.getValue().isBlank()) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/**
	 * "Vuelve a pagar mi ficha": the applicant's way back into an unpaid ficha
	 * when the post-registration screen is gone (reload loses its
	 * navigate-state UUID). Identifies her by {@code folio} + last 3 CURP
	 * characters and returns ONLY the payment data.
	 *
	 * <p>Throttled per client IP by {@link PaymentAccessRateLimiter}
	 * ({@code 429} past the budget) because that identity pair is deliberately
	 * weak, and answered with a single generic {@code 404} for every rejection
	 * so it cannot be used to discover which folios exist.
	 */
	@PostMapping("/payment-access")
	public ResponseEntity<FichaPaymentAccessResponse> accessPayment(
			@Valid @RequestBody FichaPaymentAccessRequest request, HttpServletRequest httpRequest) {
		paymentAccessRateLimiter.checkAllowed(PaymentAccessRateLimiter.clientKeyOf(httpRequest));
		return ResponseEntity
				.ok(FichaPaymentAccessResponse.from(accessFichaPaymentUseCase.access(request.folio(),
						request.curpSuffix())));
	}

	/**
	 * The payment-only view by candidate id, priced live: the portal re-reads
	 * it on every load of {@code /portal/ficha/pago}, so a tariff or discount
	 * changed after the applicant looked her ficha up shows on reload.
	 */
	@GetMapping("/{id}/payment-access")
	public ResponseEntity<FichaPaymentAccessResponse> refreshPaymentAccess(@PathVariable UUID id) {
		return ResponseEntity.ok(FichaPaymentAccessResponse.from(accessFichaPaymentUseCase.accessById(id)));
	}

	/**
	 * Confirms the ticket payment ({@code PENDING → PAID}) and transitions the
	 * candidate to {@code PAID}, then sends the confirmation email (best-effort,
	 * never blocks the response). The body carries the EVO {@code order.id} and
	 * is REQUIRED: EVO must report {@code SUCCESS} for that exact order before
	 * any local "pagado". There is no window-payment path anymore, so a request
	 * without {@code orderId} is a {@code 400} rather than a trust-me confirm.
	 * {@code 400} when verification fails, {@code 409} if already paid,
	 * {@code 404} if candidate/payment missing.
	 */
	@PostMapping("/{id}/payments/confirm")
	public ResponseEntity<PaymentConfirmationResponse> confirmPayment(@PathVariable UUID id,
			@Valid @RequestBody VerifyFichaPaymentRequest request) {
		ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult result = confirmFichaPaymentVerifiedUseCase
				.confirm(id, request.orderId());
		FichaData ficha = getCandidateFichaUseCase.get(id);
		if (ficha != null && ficha.email() != null && !ficha.email().isBlank()) {
			// `paidAt` del resultado, no el reloj: el comprobante tiene que llevar
			// la fecha en que el gateway confirmó, no la de hoy. Antes se pasaba
			// `deadline`, que el correo nunca usó.
			fichaMailService.sendPaymentConfirmation(ficha.email(), fullName(ficha), ficha.folio(),
					ficha.programName(), result.amount(), result.referenceNumber(), result.paidAt(),
					result.receiptNumber());
		}
		return ResponseEntity.ok(PaymentConfirmationResponse.from(result));
	}

	/**
	 * Public "Pagar en línea" trigger (Fase 4): initiates the EVO Hosted
	 * Checkout session for the ficha — {@code order.id} (prefix + folio),
	 * amount/currency from the payment concept, return/cancelUrl from config —
	 * and persists {@code order.id} + {@code session.id} on the payment so the
	 * return step (Fase 5) can verify the result against the gateway. {@code 404}
	 * if candidate/payment missing, {@code 409} if already paid, {@code 502} if
	 * the gateway cannot start the session.
	 *
	 * <p>The body is optional and carries only an allowlisted {@code returnPath}
	 * — see {@link CheckoutInitiationRequest}. Existing callers that POST
	 * nothing keep working.
	 */
	@PostMapping("/{id}/payments/checkout")
	public ResponseEntity<CheckoutInitiationResponse> initiateCheckout(@PathVariable UUID id,
			@Valid @RequestBody(required = false) CheckoutInitiationRequest request) {
		String returnPath = request == null ? null : request.returnPath();
		return ResponseEntity
				.ok(CheckoutInitiationResponse.from(initiateFichaPaymentUseCase.initiateCheckout(id, returnPath)));
	}

	/**
	 * Gives the quota slot back when the applicant's browser gave up on the hosted
	 * checkout — the {@code onEvoTimeout} / {@code onEvoError} callbacks of
	 * {@code useFichaPayment.ts}. Before this existed the browser cleaned up locally
	 * and told the backend nothing, so the slot stayed held and the next "Pagar" on
	 * that career was refused for a place the applicant had already released.
	 *
	 * <p>Despite the name and the caller, <b>this endpoint does not believe the
	 * caller</b>. A browser timeout and a browser error are both compatible with an
	 * order that exists and was captured moments later, so the body is treated as a
	 * question and {@code Retrieve Order} answers it; the slot comes back only when
	 * the bank proves nothing was captured. Anything else — still pending, captured,
	 * or the undiagnosable {@code SUCCESS} with no capture — returns
	 * {@code slotReleased=false} and leaves the attempt open for the daily sweep.
	 *
	 * <p>{@code 404} if there is no ficha, {@code 409} if it is already paid,
	 * {@code 400} if the {@code orderId} is absent or is not this ficha's, and
	 * {@code 502} if the gateway could not be reached — in which case nothing changed
	 * and the applicant may simply try again.
	 */
	@PostMapping("/{id}/payments/release")
	public ResponseEntity<ReleaseFichaPaymentResponse> releasePaymentSlot(@PathVariable UUID id,
			@Valid @RequestBody ReleaseFichaPaymentRequest request) {
		return ResponseEntity.ok(ReleaseFichaPaymentResponse
				.from(releaseFichaPaymentSlotUseCase.release(id, request.orderId())));
	}

	/**
	 * Full ficha projection for the screen refresh / direct-open case (the
	 * ficha route is only reachable via {@code navigate} state today; a reload
	 * loses it, so the frontend re-fetches by {@code id}).
	 */
	@GetMapping("/{id}")
	public ResponseEntity<CandidateFichaResponse> getFicha(@PathVariable UUID id) {
		FichaData ficha = getCandidateFichaUseCase.get(id);
		if (ficha == null) {
			throw new CandidateNotFoundException("No existe el candidato: " + id);
		}
		return ResponseEntity.ok(CandidateFichaResponse.from(ficha));
	}

	@GetMapping("/{id}/detail")
	public ResponseEntity<CandidateStaffDetailResponse> getStaffDetail(@PathVariable UUID id) {
		FichaData ficha = getCandidateFichaUseCase.get(id);
		if (ficha == null) {
			throw new CandidateNotFoundException("No existe el candidato: " + id);
		}
		return ResponseEntity.ok(CandidateStaffDetailResponse.from(ficha));
	}

	/**
	 * Pagos del candidato para el detalle de Servicios Escolares: la ficha vigente,
	 * la diferencia pendiente de un cambio de carrera, los cobros que quedaron como
	 * historial, el saldo a favor y los cambios de carrera.
	 */
	@GetMapping("/{id}/account")
	public ResponseEntity<CandidateAccountResponse> getAccount(@PathVariable UUID id) {
		return ResponseEntity.ok(CandidateAccountResponse.from(getCandidateAccountUseCase.get(id)));
	}

	/**
	 * Ficha completa para el formulario de edición de Servicios Escolares
	 * ({@code CANDIDATES_UPDATE}, Fase 7 · T2). A diferencia de {@code /detail},
	 * trae los IDs de catálogo y los formatos del registro.
	 */
	@GetMapping("/{id}/edit")
	public ResponseEntity<CandidateEditFormResponse> getEditForm(@PathVariable UUID id) {
		return ResponseEntity.ok(CandidateEditFormResponse.from(getCandidateEditFormUseCase.get(id)));
	}

	/**
	 * Guarda la ficha editada por Servicios Escolares. Recibe el mismo cuerpo que
	 * el registro; {@code llaveMxVerified} se ignora (la verificación no se edita).
	 * Si cambia la carrera, recotiza la ficha en la misma transacción. Responde
	 * con la ficha ya guardada.
	 */
	@PutMapping("/{id}")
	public ResponseEntity<CandidateEditFormResponse> update(@PathVariable UUID id,
			@Valid @RequestBody RegisterCandidateRequest request, Authentication authentication) {
		updateCandidateUseCase.update(id, toCommand(request, false), UUID.fromString(authentication.getName()));
		return ResponseEntity.ok(CandidateEditFormResponse.from(getCandidateEditFormUseCase.get(id)));
	}

	/**
	 * Lo que pasaría con el cobro si el candidato cambia a {@code admissionConfigId}:
	 * sin pagar, el precio nuevo con su descuento; pagada, la diferencia a pagar o
	 * el saldo a favor, y si la carrera nueva tiene lugar. No guarda nada.
	 */
	@GetMapping("/{id}/program-change-preview")
	public ResponseEntity<ChangeCandidateProgramUseCase.ProgramChangePreview> previewProgramChange(
			@PathVariable UUID id, @RequestParam UUID admissionConfigId) {
		return ResponseEntity.ok(changeCandidateProgramUseCase.preview(id, admissionConfigId));
	}

	@GetMapping("/{id}/ficha.pdf")
	public ResponseEntity<byte[]> downloadFichaPdf(@PathVariable UUID id) {
		FichaData ficha = getCandidateFichaUseCase.get(id);
		if (ficha == null) {
			throw new CandidateNotFoundException("No existe el candidato: " + id);
		}
		byte[] pdf = fichaPdfService.render(ficha);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"ficha-" + ficha.folio() + ".pdf\"")
				.contentType(MediaType.APPLICATION_PDF).body(pdf);
	}

	private static RegisterCandidateCommand toCommand(RegisterCandidateRequest request, boolean llaveMxVerified) {
		return new RegisterCandidateCommand(
				toDatosGenerales(request.datosGenerales()),
				toDomicilio(request.domicilio()),
				toContacto(request.contacto()),
				toInformacionComplementaria(request.informacionComplementaria()),
				toIngresos(request.ingresos()),
				toAntecedentesEscolares(request.antecedentesEscolares()),
				toSeleccionCarrera(request.seleccionCarrera()),
				llaveMxVerified);
	}

	private static DatosGenerales toDatosGenerales(RegisterCandidateRequest.DatosGenerales d) {
		return new DatosGenerales(d.curp(), d.nombres(), d.apellidoPaterno(), d.apellidoMaterno(),
				parseDate(d.fechaNacimiento()), toGender(d.sexo()), d.nacionalidad(), d.birthStateId(),
				d.birthMunicipalityId(), trimToNull(d.paisNacimiento()), trimToNull(d.estadoNacimiento()),
				trimToNull(d.ciudadNacimiento()), toMaritalStatus(d.estadoCivil()),
				d.lenguaNatal(), d.tieneHijos());
	}

	private static Domicilio toDomicilio(RegisterCandidateRequest.Domicilio dom) {
		return new Domicilio(dom.calle(), dom.numeroExterior(), dom.numeroInterior(), dom.colonia(), dom.localidad(),
				dom.codigoPostal(), dom.stateId(), dom.municipalityId());
	}

	private static Contacto toContacto(RegisterCandidateRequest.Contacto c) {
		return new Contacto(c.personalEmail(), c.telefonoCasa(), c.celular());
	}

	private static InformacionComplementaria toInformacionComplementaria(
			RegisterCandidateRequest.InformacionComplementaria info) {
		return new InformacionComplementaria(info.tieneEnfermedadPreexistente(), info.descripcionEnfermedad(),
				info.tieneDiscapacidad(), info.descripcionDiscapacidad(), info.padresHablanLenguaIndigena(),
				info.lenguaIndigenaPadres(), info.hablaLenguaIndigena(), info.lenguaIndigenaPropia(),
				info.seIdentificaIndigena(), info.seIdentificaNoBinario(), info.perteneceComunidadLgbttiq(),
				info.esAfrodescendiente(), info.esAfrodescendiente() && Boolean.TRUE.equals(info.seIdentificaAfrodescendiente()));
	}

	private static Ingresos toIngresos(RegisterCandidateRequest.Ingresos i) {
		// tipoTrabajo travels as written. It used to go through toEmploymentType(),
		// which returned null for anything that was not "tiempo completo"/"medio
		// tiempo" — silently voiding "Freelance", "Negocio propio" and every word
		// the free-text field accepts. The field is free text on purpose now.
		return new Ingresos(i.ingresoMensualFamiliar(), i.trabaja(), trimToNull(i.tipoTrabajo()),
				i.telefonoTrabajo(), i.ingresoMensual(), i.nombreEmpresa(), i.puesto(), parseTime(i.horaInicio()),
				parseTime(i.horaFin()));
	}

	private static AntecedentesEscolares toAntecedentesEscolares(RegisterCandidateRequest.AntecedentesEscolares a) {
		boolean cctConfirmed = a.cct() != null && !a.cct().isBlank() && a.cct().equals(a.cctConfirmacion());
		return new AntecedentesEscolares(a.nombrePreparatoria(), a.schoolTypeId(), a.estudioBachilleratoEnMexico(),
				a.schoolStateId(), a.schoolMunicipalityId(), a.paisPreparatoria(), trimToNull(a.estadoPreparatoria()),
				a.ciudadPreparatoria(), a.promedio(), a.cct(), cctConfirmed);
	}

	private static SeleccionCarrera toSeleccionCarrera(RegisterCandidateRequest.SeleccionCarrera s) {
		return new SeleccionCarrera(s.admissionConfigId(), s.outreachChannelId(), s.isFirstChoice());
	}

	private static LocalDate parseDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.trim(), DATE_FORMATTER);
		} catch (DateTimeParseException ex) {
			throw new InvalidCandidateFichaDataException("fechaNacimiento debe tener formato dd/MM/yyyy: " + value);
		}
	}

	private static LocalTime parseTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalTime.parse(value.trim(), TIME_FORMATTER);
		} catch (DateTimeParseException ex) {
			throw new InvalidCandidateFichaDataException("hora (horario laboral) debe tener formato HH:mm: " + value);
		}
	}

	/**
	 * Maps the front's {@code sexo} labels — "Femenino"/"Mujer" → {@code F},
	 * "Masculino"/"Hombre" → {@code M}, anything else → {@code NB}. Case and
	 * surrounding whitespace-insensitive.
	 */
	private static Gender toGender(String value) {
		if (value == null) {
			return null;
		}
		String normalized = normalize(value);
		return switch (normalized) {
			case "femenino", "mujer" -> Gender.F;
			case "masculino", "hombre" -> Gender.M;
			default -> Gender.NB;
		};
	}

	/**
	 * Maps the front's {@code estadoCivil} labels — "Soltero/a" → {@code SOLTERO},
	 * "Casado/a" → {@code CASADO}, "Unión libre" → {@code UNION_LIBRE},
	 * "Divorciado/a" → {@code DIVORCIADO}, "Viudo/a" → {@code VIUDO}, anything
	 * else → {@code OTRO}.
	 */
	private static MaritalStatus toMaritalStatus(String value) {
		if (value == null) {
			return null;
		}
		String normalized = normalize(value).replace("/a", "");
		return switch (normalized) {
			case "soltero" -> MaritalStatus.SOLTERO;
			case "casado" -> MaritalStatus.CASADO;
			case "unión libre", "union libre" -> MaritalStatus.UNION_LIBRE;
			case "divorciado" -> MaritalStatus.DIVORCIADO;
			case "viudo" -> MaritalStatus.VIUDO;
			default -> MaritalStatus.OTRO;
		};
	}

	/**
	 * Trims and nulls out a blank free-text field. The enrollment wizard sends
	 * {@code ''} for the employment fields it never rendered, and storing ""
	 * would print as a filled-in value on the PDF instead of "-".
	 */
	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static CandidateListItemResponse toItem(CandidateListItem item) {
		return new CandidateListItemResponse(item.id(), item.personId(), item.folio(), item.fullName(), item.curp(),
				item.programId(), item.programName(), item.status(), item.registeredAt());
	}

	private static String fullName(FichaData ficha) {
		return (ficha.firstName() == null ? "" : ficha.firstName()) + " "
				+ (ficha.lastName1() == null ? "" : ficha.lastName1()) + " "
				+ (ficha.lastName2() == null ? "" : ficha.lastName2());
	}
}
