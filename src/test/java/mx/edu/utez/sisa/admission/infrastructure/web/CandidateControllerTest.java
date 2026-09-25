package mx.edu.utez.sisa.admission.infrastructure.web;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;
import mx.edu.utez.sisa.admission.infrastructure.notification.CandidateFichaMailService;
import mx.edu.utez.sisa.admission.infrastructure.pdf.CandidateFichaPdfService;
import mx.edu.utez.sisa.admission.shared.exception.FichaEmailSendException;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for {@link CandidateController}, focused on the
 * "send instructions" endpoint: {@code 204} only when the instructions email
 * was actually sent, {@code 502} with the SMTP cause when delivery fails, and
 * {@code 404}/{@code 502} for missing candidate / no registered email.
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
	private GetCandidateFichaUseCase getCandidateFichaUseCase;

	@MockitoBean
	private CandidateFichaMailService fichaMailService;

	@MockitoBean
	private CandidateFichaPdfService fichaPdfService;

	@MockitoBean
	private JwtService jwtService;

	@MockitoBean
	private PermissionCache permissionCache;

	private static FichaData ficha(UUID id, String email) {
		return new FichaData(id, "ADM-2026-000001", CandidateStatus.REGISTERED, Instant.now(), UUID.randomUUID(),
				"Ing. en Tecnologías de la Información", "CURP123456789", "Ana", "Torres", "Ruiz", email,
				"REF-2026-000001", new BigDecimal("500.00"), LocalDate.now().plusDays(10),
				AdmissionPaymentStatus.PENDING, null, null);
	}

	@Test
	void sendInstructionsReturns204WhenEmailIsSent() throws Exception {
		UUID id = UUID.randomUUID();
		when(getCandidateFichaUseCase.get(id)).thenReturn(ficha(id, "ana@example.mx"));

		mockMvc.perform(post("/candidates/{id}/send-instructions", id)).andExpect(status().isNoContent());

		verify(fichaMailService).sendPaymentInstructionsSync(eq("ana@example.mx"), any(String.class), any(String.class),
				any(String.class), any(BigDecimal.class), any(String.class), any(LocalDate.class));
	}

	@Test
	void sendInstructionsReturns502WithRealCauseWhenSmtpFails() throws Exception {
		UUID id = UUID.randomUUID();
		when(getCandidateFichaUseCase.get(id)).thenReturn(ficha(id, "ana@example.mx"));
		doThrow(new FichaEmailSendException(
				"No se pudo enviar el correo de instrucciones a ana@example.mx: Password not accepted"))
				.when(fichaMailService).sendPaymentInstructionsSync(any(String.class), any(String.class),
						any(String.class), any(String.class), any(BigDecimal.class), any(String.class),
						any(LocalDate.class));

		mockMvc.perform(post("/candidates/{id}/send-instructions", id)).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value("No se pudo enviar el correo de instrucciones a ana@example.mx: Password not accepted"));
	}

	@Test
	void sendInstructionsReturns502WhenCandidateHasNoEmail() throws Exception {
		UUID id = UUID.randomUUID();
		when(getCandidateFichaUseCase.get(id)).thenReturn(ficha(id, null));

		mockMvc.perform(post("/candidates/{id}/send-instructions", id)).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value("El candidato no tiene un correo electrónico registrado."));
	}

	@Test
	void sendInstructionsReturns404WhenCandidateDoesNotExist() throws Exception {
		UUID id = UUID.randomUUID();
		when(getCandidateFichaUseCase.get(id)).thenReturn(null);

		mockMvc.perform(post("/candidates/{id}/send-instructions", id)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe el candidato: " + id));
	}
}