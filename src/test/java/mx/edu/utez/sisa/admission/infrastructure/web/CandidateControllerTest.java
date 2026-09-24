package mx.edu.utez.sisa.admission.infrastructure.web;

import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase.InitiateCheckoutResult;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;
import mx.edu.utez.sisa.admission.infrastructure.notification.CandidateFichaMailService;
import mx.edu.utez.sisa.admission.infrastructure.pdf.CandidateFichaPdfService;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for the Fase 4 checkout endpoint of
 * {@link CandidateController}: {@code 200} with the gateway session /
 * {@code checkoutUrl}, and the business failures surfaced as {@code 404}
 * (candidate missing), {@code 409} (already paid) and {@code 502} (gateway
 * down, handled by {@code GlobalExceptionHandler}).
 */
@WebMvcTest(CandidateController.class)
@AutoConfigureMockMvc(addFilters = false)
class CandidateControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegisterCandidateUseCase registerCandidateUseCase;

	@MockitoBean
	private ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

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

	@Test
	void checkoutReturnsSessionAndUrl() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID)).thenReturn(new InitiateCheckoutResult(ID,
				"TESTUTEZ-ADM-2026-000001", "SESSION0001BR", "1", "TESTUTEZ", "AAAA/BRAVO/SUCCESS0001",
				"https://evopaymentsmexico.gateway.mastercard.com/checkout/payment/SESSION0001BR?version=1"));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID)).andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId").value("TESTUTEZ-ADM-2026-000001"))
				.andExpect(jsonPath("$.sessionId").value("SESSION0001BR"))
				.andExpect(jsonPath("$.version").value("1"))
				.andExpect(jsonPath("$.merchant").value("TESTUTEZ"))
				.andExpect(jsonPath("$.successIndicator").value("AAAA/BRAVO/SUCCESS0001"))
				.andExpect(jsonPath("$.checkoutUrl").value(
						"https://evopaymentsmexico.gateway.mastercard.com/checkout/payment/SESSION0001BR?version=1"));
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
				.thenThrow(new CandidateAlreadyPaidException("La ficha del candidato ADM-2026-000001 ya estaba pagada."));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("La ficha del candidato ADM-2026-000001 ya estaba pagada."));
	}

	@Test
	void checkoutReturns502WhenGatewayIsDown() throws Exception {
		when(initiateFichaPaymentUseCase.initiateCheckout(ID)).thenThrow(
				new EvoPaymentGatewayException("El proveedor de pagos (EVO) no pudo iniciar el pago en línea: SERVER_BUSY"));

		mockMvc.perform(post("/candidates/{id}/payments/checkout", ID)).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value(
						"El proveedor de pagos (EVO) no pudo iniciar el pago en línea: SERVER_BUSY"));
	}
}