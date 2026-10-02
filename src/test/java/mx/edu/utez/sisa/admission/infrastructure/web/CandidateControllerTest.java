package mx.edu.utez.sisa.admission.infrastructure.web;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase.PaymentAccess;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase.InitiateCheckoutResult;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase.CandidateListItem;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase.ListCandidatesQuery;
import mx.edu.utez.sisa.admission.domain.port.in.ListCandidatesUseCase.ListCandidatesResult;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase.ReleaseOutcome;
import mx.edu.utez.sisa.admission.domain.port.in.ReleaseFichaPaymentSlotUseCase.ReleaseResult;
import mx.edu.utez.sisa.admission.infrastructure.notification.CandidateFichaMailService;
import mx.edu.utez.sisa.admission.infrastructure.pdf.CandidateFichaPdfService;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidPaymentVerificationException;
import mx.edu.utez.sisa.admission.shared.exception.TooManyPaymentAccessAttemptsException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link CandidateController}, focused on the Fase 4
 * checkout and Fase 5 verified-confirm endpoints: session/SDK URL on
 * success, EVO-verified confirm returning the receipt, and the business
 * failures surfaced as {@code 404}, {@code 409}, {@code 502} (gateway down)
 * and {@code 400} (verification failed) with the real message.
 *
 * <p>The confirm body is now REQUIRED with a non-blank {@code orderId}: the
 * window-payment endpoint was removed, so a missing/blank one is a {@code 400}
 * that must never reach the use case.
 */
@WebMvcTest(CandidateController.class)
@AutoConfigureMockMvc(addFilters = false)
class CandidateControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegisterCandidateUseCase registerCandidateUseCase;

	@MockitoBean
	private AccessFichaPaymentUseCase accessFichaPaymentUseCase;

	@MockitoBean
	private ConfirmFichaPaymentVerifiedUseCase confirmFichaPaymentVerifiedUseCase;

	@MockitoBean
	private InitiateFichaPaymentUseCase initiateFichaPaymentUseCase;

	@MockitoBean
	private ReleaseFichaPaymentSlotUseCase releaseFichaPaymentSlotUseCase;

	@MockitoBean
	private GetCandidateFichaUseCase getCandidateFichaUseCase;

	@MockitoBean
	private ListCandidatesUseCase listCandidatesUseCase;

	@MockitoBean
	private CandidateFichaMailService fichaMailService;

	@MockitoBean
	private CandidateFichaPdfService fichaPdfService;

	/**
	 * The real limiter is a plain {@code @Component}, which {@code @WebMvcTest}
	 * does not scan. Mocked here so the throttle's own behaviour stays in
	 * {@link PaymentAccessRateLimiterTest} and these tests only assert the
	 * controller's status mapping.
	 */
	@MockitoBean
	private PaymentAccessRateLimiter paymentAccessRateLimiter;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private PermissionCache permissionCache;

	private static final UUID ID = UUID.randomUUID();

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001";

	// ── checkout (Fase 4) ──

	@Test
	void checkoutReturnsSessionAndSdkUrl() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID, null)).thenReturn(new InitiateCheckoutResult(ID, ORDER_ID,
				"SESSION0001BR", "TESTUTEZ", "AAAA/BRAVO/SUCCESS0001",
				"https://evopaymentsmexico.gateway.mastercard.com/static/checkout/checkout.min.js"));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID)).andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId").value(ORDER_ID))
				.andExpect(jsonPath("$.sessionId").value("SESSION0001BR"))
				.andExpect(jsonPath("$.merchant").value("TESTUTEZ"))
				.andExpect(jsonPath("$.successIndicator").value("AAAA/BRAVO/SUCCESS0001"))
				.andExpect(jsonPath("$.checkoutJsUrl").value(
						"https://evopaymentsmexico.gateway.mastercard.com/static/checkout/checkout.min.js"))
				.andExpect(jsonPath("$.checkoutUrl").doesNotExist())
				.andExpect(jsonPath("$.version").doesNotExist());
	}

	@Test
	void checkoutPassesTheReturnPathThroughToTheUseCase() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID, "/portal/ficha/pago"))
				.thenReturn(new InitiateCheckoutResult(ID, ORDER_ID, "SESSION0001BR", "TESTUTEZ", "OK", "https://x/y.js"));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"returnPath\":\"/portal/ficha/pago\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(ORDER_ID));

		verify(initiateFichaPaymentUseCase).initiateCheckout(ID, "/portal/ficha/pago");
	}

	/**
	 * The open-redirect guard at the edge: structurally hostile paths are
	 * rejected with 400 before the use case ever sees them.
	 */
	@Test
	void checkoutRejectsAnOffSiteReturnPath() throws Exception {
		for (String hostile : new String[] { "https://evil.example/steal", "//evil.example", "/portal/../admin" }) {
			mockMvc.perform(post("/candidates/{id}/payments/checkout", ID).contentType(MediaType.APPLICATION_JSON)
					.content("{\"returnPath\":\"" + hostile + "\"}"))
					.andExpect(status().isBadRequest());
		}
		verify(initiateFichaPaymentUseCase, never()).initiateCheckout(any(), any());
	}

	@Test
	void checkoutReturns404WhenCandidateDoesNotExist() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID, null))
				.thenThrow(new CandidateNotFoundException("No existe el candidato: " + ID));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe el candidato: " + ID));
	}

	@Test
	void checkoutReturns409WhenAlreadyPaid() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID, null))
				.thenThrow(new CandidateAlreadyPaidException("La ficha del candidato " + ORDER_ID + " ya estaba pagada."));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("La ficha del candidato " + ORDER_ID + " ya estaba pagada."));
	}

	@Test
	void checkoutReturns502WhenGatewayIsDown() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID, null)).thenThrow(
				new EvoPaymentGatewayException("El proveedor de pagos (EVO) no pudo iniciar el pago en línea: SERVER_BUSY"));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID)).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value(
						"El proveedor de pagos (EVO) no pudo iniciar el pago en línea: SERVER_BUSY"));
	}

	// ── verified confirm (Fase 5) ──

	private static ConfirmPaymentResult paid() {
		return new ConfirmPaymentResult(ID, "ADM-2026-000001", CandidateStatus.PAID, "REF-2026-000001",
				new BigDecimal("500.00"), Instant.parse("2026-09-24T12:00:00Z"), "REC-20260924-000001");
	}

	@Test
	void confirmWithOrderIdReturnsReceipt() throws Exception {
		when(confirmFichaPaymentVerifiedUseCase.confirm(ID, ORDER_ID)).thenReturn(paid());

		mockMvc.perform(post("/candidates/{id}/payments/confirm", ID)
				.contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + ORDER_ID + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.candidateStatus").value("PAID"))
				.andExpect(jsonPath("$.receiptNumber").value("REC-20260924-000001"));
	}

	@Test
	void confirmWithoutOrderIdIs400() throws Exception {
		// The window-payment fallback is gone: the body is required and
		// {@code orderId} is @NotBlank, so the request never reaches the use case.
		mockMvc.perform(post("/candidates/{id}/payments/confirm", ID))
				.andExpect(status().isBadRequest());
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), any());
	}

	@Test
	void confirmWithBlankOrderIdIs400() throws Exception {
		mockMvc.perform(post("/candidates/{id}/payments/confirm", ID)
				.contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"  \"}"))
				.andExpect(status().isBadRequest());
		verify(confirmFichaPaymentVerifiedUseCase, never()).confirm(any(), any());
	}

	@Test
	void confirmReturns400WhenVerificationFails() throws Exception {
		when(confirmFichaPaymentVerifiedUseCase.confirm(ID, ORDER_ID)).thenThrow(new InvalidPaymentVerificationException(
				"El pago no fue confirmado por el procesador, inténtalo de nuevo."));

		mockMvc.perform(post("/candidates/{id}/payments/confirm", ID)
				.contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + ORDER_ID + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El pago no fue confirmado por el procesador, inténtalo de nuevo."));
	}

	@Test
	void confirmReturns404WhenCandidateDoesNotExist() throws Exception {
		when(confirmFichaPaymentVerifiedUseCase.confirm(ID, ORDER_ID))
				.thenThrow(new CandidateNotFoundException("No existe el candidato: " + ID));

		mockMvc.perform(post("/candidates/{id}/payments/confirm", ID)
				.contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + ORDER_ID + "\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe el candidato: " + ID));
	}

	// ── release (Fase 4.3: the browser gave up on the session) ──

	/**
	 * The body carries the outcome and the boolean separately because the portal
	 * says something different for each: "el lugar es tuyo, reintenta" versus "tu
	 * dinero ya entró". Collapsing them into one field would force the front to
	 * guess, which is the failure this endpoint was built to end.
	 */
	@Test
	void releaseReturnsTheGatewayOutcomeAndWhetherTheSlotCameBack() throws Exception {
		when(releaseFichaPaymentSlotUseCase.release(ID, ORDER_ID))
				.thenReturn(new ReleaseResult(ID, ORDER_ID, ReleaseOutcome.SLOT_RELEASED, true));

		mockMvc.perform(post("/candidates/{id}/payments/release", ID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orderId\":\"" + ORDER_ID + "\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId").value(ORDER_ID))
				.andExpect(jsonPath("$.outcome").value("SLOT_RELEASED"))
				.andExpect(jsonPath("$.slotReleased").value(true));
	}

	/** A still-pending payment is a 200 with {@code slotReleased=false}, not an error. */
	@Test
	void releaseReportsAStillPendingOrderWithoutFailing() throws Exception {
		when(releaseFichaPaymentSlotUseCase.release(ID, ORDER_ID))
				.thenReturn(new ReleaseResult(ID, ORDER_ID, ReleaseOutcome.PAYMENT_IN_PROGRESS, false));

		mockMvc.perform(post("/candidates/{id}/payments/release", ID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orderId\":\"" + ORDER_ID + "\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("PAYMENT_IN_PROGRESS"))
				.andExpect(jsonPath("$.slotReleased").value(false));
	}

	/** Without an {@code orderId} there is no attempt to settle, so nothing is called. */
	@Test
	void releaseWithoutOrderIdIs400() throws Exception {
		mockMvc.perform(post("/candidates/{id}/payments/release", ID)).andExpect(status().isBadRequest());
		verify(releaseFichaPaymentSlotUseCase, never()).release(any(), any());
	}

	/** An order id that is not this ficha's is the one abuse the endpoint could suffer. */
	@Test
	void releaseWithAnOrderIdFromAnotherFichaIs400() throws Exception {
		when(releaseFichaPaymentSlotUseCase.release(ID, ORDER_ID)).thenThrow(
				new InvalidPaymentVerificationException("El identificador del pedido no corresponde a esta ficha, inténtalo de nuevo."));

		mockMvc.perform(post("/candidates/{id}/payments/release", ID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orderId\":\"" + ORDER_ID + "\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"El identificador del pedido no corresponde a esta ficha, inténtalo de nuevo."));
	}

	/** A paid ficha holds its place permanently: 409, never a silent no-op. */
	@Test
	void releaseOnAPaidFichaIs409() throws Exception {
		when(releaseFichaPaymentSlotUseCase.release(ID, ORDER_ID))
				.thenThrow(new CandidateAlreadyPaidException("La ficha del candidato " + ID + " ya estaba pagada."));

		mockMvc.perform(post("/candidates/{id}/payments/release", ID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orderId\":\"" + ORDER_ID + "\"}")).andExpect(status().isConflict());
	}

	/**
	 * The gateway being unreachable is a 502 and changes nothing, so the applicant can
	 * simply try again — and the daily sweep covers whatever nobody retries.
	 */
	@Test
	void releaseReturns502WhenTheGatewayCannotBeReached() throws Exception {
		when(releaseFichaPaymentSlotUseCase.release(ID, ORDER_ID)).thenThrow(
				new EvoPaymentGatewayException("No se pudo contactar al proveedor de pagos (EVO): timeout"));

		mockMvc.perform(post("/candidates/{id}/payments/release", ID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"orderId\":\"" + ORDER_ID + "\"}")).andExpect(status().isBadGateway());
	}

	// ── payment-access ("vuelve a pagar mi ficha") ──
	private static final String FOLIO = "ADM-2026-000101";

	private static final String SUFFIX = "N08";

	/** The registration window's closing day, as stored on the ticket. */
	private static final LocalDate REGISTRATION_DEADLINE = LocalDate.of(2026, 9, 30);

	/** The tuition concept's {@code available_until}: an engine boundary, not the shown date. */
	private static final LocalDate PAYMENT_CLOSES_ON = LocalDate.of(2026, 10, 5);

	/** The date the screen promises: the earlier of the sales window and the ficha plazo. */
	private static final LocalDate PAYMENT_DEADLINE = LocalDate.of(2026, 9, 28);

	private static PaymentAccess paymentAccess(boolean alreadyPaid) {
		return new PaymentAccess(ID, FOLIO, "Ana Torres Ramos", "Ing. en Tecnologías de la Información",
				new BigDecimal("500.00"), "REF-20260924-000101", REGISTRATION_DEADLINE,
				alreadyPaid ? mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PAID
						: mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PENDING,
				alreadyPaid ? "REC-20260924-000001" : null,
				alreadyPaid ? java.time.Instant.parse("2026-09-24T15:30:00Z") : null, alreadyPaid,
				PAYMENT_CLOSES_ON, PAYMENT_DEADLINE, mx.edu.utez.sisa.admission.domain.model.CandidateStatus.REGISTERED,
				false);
	}

	@Test
	void paymentAccessReturnsThePaymentView() throws Exception {
		when(accessFichaPaymentUseCase.access(FOLIO, SUFFIX)).thenReturn(paymentAccess(false));

		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"" + FOLIO + "\",\"curpSuffix\":\"" + SUFFIX + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.folio").value(FOLIO))
				.andExpect(jsonPath("$.amount").value(500.00)).andExpect(jsonPath("$.alreadyPaid").value(false));
	}

	/**
	 * The two dates reach the client under their own names, as {@code yyyy-MM-dd}.
	 *
	 * <p>Asserted on the wire because the whole defect was a projection problem: the
	 * value was always there, it was just labelled "deadline" and rendered as
	 * "Fecha límite de pago". A rename that silently dropped one of them would
	 * leave the screen worse than before, so both are pinned here.
	 */
	@Test
	void paymentAccessExposesBothWindowDatesUnderDistinctNames() throws Exception {
		when(accessFichaPaymentUseCase.access(FOLIO, SUFFIX)).thenReturn(paymentAccess(false));

		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"" + FOLIO + "\",\"curpSuffix\":\"" + SUFFIX + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registrationDeadline").value("2026-09-30"))
				.andExpect(jsonPath("$.paymentClosesOn").value("2026-10-05"))
				.andExpect(jsonPath("$.paymentDeadline").value("2026-09-28"))
				.andExpect(jsonPath("$.deadline").doesNotExist());
	}

	/**
	 * A concept with no closing date must serialise as an explicit null, not be
	 * dropped: the screen decides whether to render the row from the key being
	 * present, and a missing key is indistinguishable from a contract change.
	 */
	@Test
	void paymentAccessSerialisesAnAbsentPaymentWindowAsNull() throws Exception {
		when(accessFichaPaymentUseCase.access(FOLIO, SUFFIX)).thenReturn(new PaymentAccess(ID, FOLIO,
				"Ana Torres Ramos", "Ing. en Tecnologías de la Información", new BigDecimal("500.00"),
				"REF-20260924-000101", REGISTRATION_DEADLINE,
				mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PENDING, null, null, false, null,
				null, mx.edu.utez.sisa.admission.domain.model.CandidateStatus.REGISTERED, false));

		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"" + FOLIO + "\",\"curpSuffix\":\"" + SUFFIX + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.paymentClosesOn").isEmpty())
				.andExpect(jsonPath("$.paymentDeadline").isEmpty())
				.andExpect(jsonPath("$.registrationDeadline").value("2026-09-30"));
	}

	/**
	 * The flag is what hides the "Pagar" button, so it has to reach the wire. A
	 * projection that dropped it would leave the screen showing a button the
	 * checkout refuses with a 409 — the exact defect this field was added for.
	 */
	@Test
	void paymentAccessExposesTheExpiredFlagAndCandidateStatus() throws Exception {
		when(accessFichaPaymentUseCase.access(FOLIO, SUFFIX))
				.thenReturn(new PaymentAccess(ID, FOLIO, "Ana Torres Ramos", "Ing. en TIC", new BigDecimal("500.00"),
						"REF-20260924-000101", REGISTRATION_DEADLINE,
						mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PENDING, null, null, false,
						PAYMENT_CLOSES_ON, PAYMENT_DEADLINE,
						mx.edu.utez.sisa.admission.domain.model.CandidateStatus.PAYMENT_EXPIRED, true));

		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"" + FOLIO + "\",\"curpSuffix\":\"" + SUFFIX + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.paymentExpired").value(true))
				.andExpect(jsonPath("$.candidateStatus").value("PAYMENT_EXPIRED"));
	}

	@Test
	void paymentAccessMarksAnAlreadyPaidFicha() throws Exception {
		when(accessFichaPaymentUseCase.access(FOLIO, SUFFIX)).thenReturn(paymentAccess(true));

		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"" + FOLIO + "\",\"curpSuffix\":\"" + SUFFIX + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.alreadyPaid").value(true))
				.andExpect(jsonPath("$.receiptNumber").value("REC-20260924-000001"))
				.andExpect(jsonPath("$.paidAt").value("2026-09-24T15:30:00Z"));
	}

	@Test
	void paymentAccessReturns404WithTheGenericMessageOnMismatch() throws Exception {
		when(accessFichaPaymentUseCase.access(FOLIO, SUFFIX)).thenThrow(new CandidateNotFoundException(
				"No encontramos una ficha de admisión con ese folio y CURP. Verifica tus datos."));

		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"" + FOLIO + "\",\"curpSuffix\":\"" + SUFFIX + "\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message")
						.value("No encontramos una ficha de admisión con ese folio y CURP. Verifica tus datos."));
	}

	@Test
	void paymentAccessReturns429WhenThrottled() throws Exception {
		doThrow(new TooManyPaymentAccessAttemptsException(
				"Demasiados intentos de acceso. Espera unos minutos antes de volver a intentarlo."))
						.when(paymentAccessRateLimiter).checkAllowed(any());

		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"" + FOLIO + "\",\"curpSuffix\":\"" + SUFFIX + "\"}"))
				.andExpect(status().isTooManyRequests());
		verify(accessFichaPaymentUseCase, never()).access(any(), any());
	}

	@Test
	void paymentAccessRejectsAMalformedFolio() throws Exception {
		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"no-es-un-folio\",\"curpSuffix\":\"" + SUFFIX + "\"}"))
				.andExpect(status().isBadRequest());
		verify(accessFichaPaymentUseCase, never()).access(any(), any());
	}

	@Test
	void paymentAccessRejectsASuffixThatIsNotThreeCharacters() throws Exception {
		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"" + FOLIO + "\",\"curpSuffix\":\"08\"}"))
				.andExpect(status().isBadRequest());
		verify(accessFichaPaymentUseCase, never()).access(any(), any());
	}

	// ── candidatos (listado) ──

	/**
	 * The caller id travels in the principal, never in the query string: the
	 * server-side division scope is derived from the JWT identity, so a Director
	 * cannot widen their view by adding/removing a parameter.
	 */
	@Test
	void listCandidatesUsesThePrincipalAsCallerId() throws Exception {
		UUID callerId = UUID.randomUUID();
		when(listCandidatesUseCase.listCandidates(any())).thenReturn(
				new ListCandidatesResult(List.of(new CandidateListItem(ID, "ADM-2026-000001", "Ana Torres Ramos",
						"GOCD050101HDFRNS04", UUID.fromString(CFG_ID), "Ing. en TI", CandidateStatus.REGISTERED,
						Instant.parse("2026-09-24T12:00:00Z"))), 1, 1, 0, 20));

		mockMvc.perform(get("/candidates").principal(authentication(callerId))).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].folio").value("ADM-2026-000001"))
				.andExpect(jsonPath("$.items[0].status").value("REGISTERED"))
				.andExpect(jsonPath("$.totalElements").value(1));

		ArgumentCaptor<ListCandidatesQuery> captor = ArgumentCaptor.forClass(ListCandidatesQuery.class);
		verify(listCandidatesUseCase).listCandidates(captor.capture());
		assertThat(captor.getValue().callerId()).isEqualTo(callerId);
	}

	@Test
	void listCandidatesDefaultsToFirstPageOfTwenty() throws Exception {
		when(listCandidatesUseCase.listCandidates(any()))
				.thenReturn(new ListCandidatesResult(List.of(), 0, 0, 0, 20));

		mockMvc.perform(get("/candidates").principal(authentication(UUID.randomUUID())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

		ArgumentCaptor<ListCandidatesQuery> captor = ArgumentCaptor.forClass(ListCandidatesQuery.class);
		verify(listCandidatesUseCase).listCandidates(captor.capture());
		assertThat(captor.getValue().page()).isZero();
		assertThat(captor.getValue().size()).isEqualTo(20);
	}

	@Test
	void listCandidatesForwardsEveryFilterToTheUseCase() throws Exception {
		when(listCandidatesUseCase.listCandidates(any()))
				.thenReturn(new ListCandidatesResult(List.of(), 0, 0, 1, 5));

		mockMvc.perform(get("/candidates").param("status", "PAID").param("programId", CFG_ID)
				.param("periodId", GEO_ID).param("search", "ana").param("page", "1").param("size", "5")
				.principal(authentication(UUID.randomUUID()))).andExpect(status().isOk());

		ArgumentCaptor<ListCandidatesQuery> captor = ArgumentCaptor.forClass(ListCandidatesQuery.class);
		verify(listCandidatesUseCase).listCandidates(captor.capture());
		assertThat(captor.getValue().status()).isEqualTo(CandidateStatus.PAID);
		assertThat(captor.getValue().programId()).isEqualTo(UUID.fromString(CFG_ID));
		assertThat(captor.getValue().periodId()).isEqualTo(UUID.fromString(GEO_ID));
		assertThat(captor.getValue().search()).isEqualTo("ana");
		assertThat(captor.getValue().page()).isEqualTo(1);
		assertThat(captor.getValue().size()).isEqualTo(5);
	}

	private static Authentication authentication(UUID callerId) {
		return new UsernamePasswordAuthenticationToken(callerId.toString(), null, List.of());
	}

	// ── registro (validación anidada del DTO) ──

	/**
	 * The root record of {@code RegisterCandidateRequest} annotated its 7 nested
	 * records with {@code @NotNull} but not {@code @Valid}, so Bean Validation
	 * never descended into them (JSR-380 §5.7.1) and all 16 nested
	 * {@code @NotBlank}/{@code @NotNull} were dead code. Six of those fields feed
	 * NOT NULL columns, so omitting one reached the DB and came back as a 500 at
	 * flush time instead of a 400.
	 *
	 * <p>These tests pin the cascade: each case blanks exactly one nested field and
	 * asserts the 400 plus the field's own Spanish message. They must never reach
	 * the use case.
	 */
	@Test
	void registerRejectsABlankCurp() throws Exception {
		assertNestedFieldRejected("curp", "GOCD050101HDFRNS04", "La CURP es obligatoria.");
	}

	@Test
	void registerRejectsBlankNames() throws Exception {
		assertNestedFieldRejected("nombres", "Juan", "Los nombres son obligatorios.");
	}

	@Test
	void registerRejectsABlankStreet() throws Exception {
		assertNestedFieldRejected("calle", "Avenida Juarez", "La calle es obligatoria.");
	}

	@Test
	void registerRejectsABlankExteriorNumber() throws Exception {
		assertNestedFieldRejected("numeroExterior", "1", "El número exterior es obligatorio.");
	}

	@Test
	void registerRejectsABlankPostalCode() throws Exception {
		assertNestedFieldRejected("codigoPostal", "68000", "El código postal es obligatorio.");
	}

	@Test
	void registerRejectsABlankEmail() throws Exception {
		assertNestedFieldRejected("personalEmail", "juan.perez@example.com",
				"El correo electrónico es obligatorio.");
	}

	@Test
	void registerRejectsABlankSchoolName() throws Exception {
		assertNestedFieldRejected("nombrePreparatoria", "Colegio Nacional",
				"El nombre de la preparatoria es obligatorio.");
	}

	@Test
	void registerRejectsAMissingCareer() throws Exception {
		mockMvc.perform(post("/candidates").contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REGISTRATION.replace("\"" + CFG_ID + "\"", "null")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La carrera es obligatoria."));
		verify(registerCandidateUseCase, never()).register(any());
	}

	@Test
	void registerRejectsAMissingHouseholdIncome() throws Exception {
		mockMvc.perform(post("/candidates").contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REGISTRATION.replaceAll("\"ingresoMensualFamiliar\"\\s*:\\s*12000",
						"\"ingresoMensualFamiliar\": null")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El ingreso mensual familiar es obligatorio."));
		verify(registerCandidateUseCase, never()).register(any());
	}

	/**
	 * {@code ciudadPreparatoria} is the one nested field with no annotation at
	 * all, and {@code school_city} is NOT NULL. It is NOT rejected here on
	 * purpose: a Mexican high school legitimately has no capturable city (the
	 * wizard only renders that input when the school was abroad), so
	 * {@code HighSchoolBackground} normalizes {@code null} to {@code ""} rather
	 * than turning a valid registration into a 400.
	 */
	@Test
	void registerAcceptsAMissingSchoolCity() throws Exception {
		// Reaching the use case proves validation let it through. The mock returns
		// null so the controller NPEs, and that 500 is irrelevant — what matters is
		// that this is NOT a 400 and NOT a validation failure.
		mockMvc.perform(post("/candidates").contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REGISTRATION.replace("\"ciudadPreparatoria\":\"Puebla\"",
						"\"ciudadPreparatoria\":null")))
				.andExpect(status().is5xxServerError());
		verify(registerCandidateUseCase).register(any());
	}

	/**
	 * Only the status is asserted here: with six sections absent the handler
	 * reports {@code getFieldErrors().findFirst()} and Hibernate Validator does
	 * not order field errors, so pinning which message wins would be flaky.
	 * The per-field messages are pinned by the tests above, one violation each.
	 */
	@Test
	void registerRejectsAMissingSection() throws Exception {
		mockMvc.perform(post("/candidates").contentType(MediaType.APPLICATION_JSON)
				.content("{\"datosGenerales\":{},\"llaveMxVerified\":false}"))
				.andExpect(status().isBadRequest());
		verify(registerCandidateUseCase, never()).register(any());
	}

	/**
	 * Blanks exactly one field of the valid payload and asserts the 400 plus that
	 * field's own message — which only happens if the {@code @Valid} cascade is
	 * intact.
	 *
	 * <p>Matched with a regex rather than a literal so the spacing in the payload
	 * block does not matter; {@code assertNotEquals} is the guard against a
	 * pattern that silently matches nothing and turns this into a false pass.
	 */
	private void assertNestedFieldRejected(String key, String value, String expectedMessage) throws Exception {
		String body = VALID_REGISTRATION.replaceAll("\"" + key + "\"\\s*:\\s*\"" + Pattern.quote(value) + "\"",
				"\"" + key + "\": \"\"");
		org.junit.jupiter.api.Assertions.assertNotEquals(VALID_REGISTRATION, body,
				"el payload base no contenia el campo a reemplazar: " + key);
		mockMvc.perform(post("/candidates").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(expectedMessage));
		verify(registerCandidateUseCase, never()).register(any());
	}

	private static final String CFG_ID = "11111111-1111-1111-1111-111111111111";

	private static final String GEO_ID = "22222222-2222-2222-2222-222222222222";

	private static final String VALID_REGISTRATION = """
			{
			  "datosGenerales": {
			    "curp": "GOCD050101HDFRNS04", "nombres": "Juan", "apellidoPaterno": "Perez",
			    "apellidoMaterno": "Gomez", "fechaNacimiento": "01/01/2005", "sexo": "Femenino",
			    "nacionalidad": "Mexicana", "estadoCivil": "Soltero/a", "tieneHijos": false
			  },
			  "domicilio": {
			    "calle": "Avenida Juarez", "numeroExterior": "1", "codigoPostal": "68000",
			    "stateId": "%s", "municipalityId": "%s"
			  },
			  "contacto": { "personalEmail": "juan.perez@example.com" },
			  "informacionComplementaria": {},
			  "ingresos": { "ingresoMensualFamiliar": 12000, "trabaja": false },
			  "seleccionCarrera": { "admissionConfigId": "%s", "isFirstChoice": true },
			  "antecedentesEscolares": {
			    "nombrePreparatoria": "Colegio Nacional", "estudioBachilleratoEnMexico": true,
			    "promedio": 9.0, "cct": "17DCT0001A", "cctConfirmacion": "17DCT0001A",
			    "ciudadPreparatoria": "Puebla"
			  },
			  "llaveMxVerified": true
			}
			""".formatted(GEO_ID, GEO_ID, CFG_ID);
}