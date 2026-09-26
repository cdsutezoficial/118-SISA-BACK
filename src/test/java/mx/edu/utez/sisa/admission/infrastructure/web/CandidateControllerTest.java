package mx.edu.utez.sisa.admission.infrastructure.web;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.AccessFichaPaymentUseCase.PaymentAccess;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmFichaPaymentVerifiedUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase.InitiateCheckoutResult;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
	private GetCandidateFichaUseCase getCandidateFichaUseCase;

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

	// ── payment-access ("vuelve a pagar mi ficha") ──
	private static final String FOLIO = "ADM-2026-000101";

	private static final String SUFFIX = "N08";

	private static PaymentAccess paymentAccess(boolean alreadyPaid) {
		return new PaymentAccess(ID, FOLIO, "Ana Torres Ramos", "Ing. en Tecnologías de la Información",
				new BigDecimal("500.00"), "REF-20260924-000101", java.time.LocalDate.now().plusDays(10),
				alreadyPaid ? mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PAID
						: mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PENDING,
				alreadyPaid ? "REC-20260924-000001" : null,
				alreadyPaid ? java.time.Instant.parse("2026-09-24T15:30:00Z") : null, alreadyPaid);
	}

	@Test
	void paymentAccessReturnsThePaymentView() throws Exception {
		when(accessFichaPaymentUseCase.access(FOLIO, SUFFIX)).thenReturn(paymentAccess(false));

		mockMvc.perform(post("/candidates/payment-access").contentType(MediaType.APPLICATION_JSON)
				.content("{\"folio\":\"" + FOLIO + "\",\"curpSuffix\":\"" + SUFFIX + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.folio").value(FOLIO))
				.andExpect(jsonPath("$.amount").value(500.00)).andExpect(jsonPath("$.alreadyPaid").value(false));
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
}