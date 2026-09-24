package mx.edu.utez.sisa.admission.infrastructure.notification;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import mx.edu.utez.sisa.admission.shared.exception.FichaEmailSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CandidateFichaMailService}: the "send instructions"
 * resend must be synchronous and THROW on SMTP failure (so the portal 502s
 * with the real cause instead of the old fake 204), while the paid-ficha
 * confirmation email stays best-effort (never fails the HTTP response).
 */
@ExtendWith(MockitoExtension.class)
class CandidateFichaMailServiceTest {

	private static final String TO = "aspirante@example.mx";

	@Mock
	private JavaMailSender mailSender;

	private CandidateFichaMailService service;

	@BeforeEach
	void setUp() {
		service = new CandidateFichaMailService(mailSender, false);
	}

	@Test
	void sendPaymentInstructionsSyncDeliversWhenSmtpSucceeds() {
		when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

		service.sendPaymentInstructionsSync(TO, "Ana Torres", "ADM-2026-000001",
				"Ing. en Tecnologías de la Información", new BigDecimal("500.00"), "REF-2026-000001",
				LocalDate.of(2026, 10, 15));

		verify(mailSender).send(any(MimeMessage.class));
	}

	@Test
	void sendPaymentInstructionsSyncThrowsWithRealCauseWhenSmtpFails() {
		when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
		doThrow(new MailSendException("MessagingException: 535 5.7.8 Username and Password not accepted"))
				.when(mailSender).send(any(MimeMessage.class));

		assertThatThrownBy(() -> service.sendPaymentInstructionsSync(TO, "Ana Torres", "ADM-2026-000001",
				"Programa", new BigDecimal("500.00"), "REF", LocalDate.now()))
				.isInstanceOf(FichaEmailSendException.class)
				.hasMessageContaining("No se pudo enviar el correo de instrucciones a " + TO)
				.hasMessageContaining("Password not accepted");
	}

	@Test
	void sendPaymentConfirmationSwallowsFailureInBestEffortMode() {
		// fail-fast makes the dispatch synchronous so the swallow path is
		// exercised deterministically — without it the async send races the assert.
		CandidateFichaMailService failFastService = new CandidateFichaMailService(mailSender, true);
		when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
		doThrow(new MailSendException("down")).when(mailSender).send(any(MimeMessage.class));

		assertThatCode(() -> failFastService.sendPaymentConfirmation(TO, "Ana", "ADM-2026-000001", "Programa",
				new BigDecimal("500.00"), "REF", LocalDate.now(), "REC-2026-000001")).doesNotThrowAnyException();

		verify(mailSender).send(any(MimeMessage.class));
	}
}