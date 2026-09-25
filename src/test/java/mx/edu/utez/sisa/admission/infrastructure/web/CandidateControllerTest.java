package mx.edu.utez.sisa.admission.infrastructure.web;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
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
 */
@WebMvcTest(CandidateController.class)
@AutoConfigureMockMvc(addFilters = false)
class CandidateControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegisterCandidateUseCase registerCandidateUseCase;

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

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private PermissionCache permissionCache;

	private static final UUID ID = UUID.randomUUID();

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001";

	// ── checkout (Fase 4) ──

	@Test
	void checkoutReturnsSessionAndSdkUrl() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID)).thenReturn(new InitiateCheckoutResult(ID, ORDER_ID,
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
	void checkoutReturns404WhenCandidateDoesNotExist() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID))
				.thenThrow(new CandidateNotFoundException("No existe el candidato: " + ID));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe el candidato: " + ID));
	}

	@Test
	void checkoutReturns409WhenAlreadyPaid() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID))
				.thenThrow(new CandidateAlreadyPaidException("La ficha del candidato " + ORDER_ID + " ya estaba pagada."));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("La ficha del candidato " + ORDER_ID + " ya estaba pagada."));
	}

	@Test
	void checkoutReturns502WhenGatewayIsDown() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID)).thenThrow(
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
	void confirmWithoutOrderIdFallsBackToWindowConfirm() throws Exception {
		when(confirmFichaPaymentVerifiedUseCase.confirm(ID, null)).thenReturn(paid());

		mockMvc.perform(post("/candidates/{id}/payments/confirm", ID)).andExpect(status().isOk())
				.andExpect(jsonPath("$.receiptNumber").value("REC-20260924-000001"));
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
		when(confirmFichaPaymentVerifiedUseCase.confirm(ID, null))
				.thenThrow(new CandidateNotFoundException("No existe el candidato: " + ID));

		mockMvc.perform(post("/candidates/{id}/payments/confirm", ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe el candidato: " + ID));
	}
}