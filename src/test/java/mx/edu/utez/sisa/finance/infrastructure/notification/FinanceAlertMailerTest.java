package mx.edu.utez.sisa.finance.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertType;

@ExtendWith(MockitoExtension.class)
class FinanceAlertMailerTest {

	@Mock
	private ApplicationEventPublisher publisher;

	@Mock
	private JavaMailSender mailSender;

	private static FinanceAlert alert() {
		return new FinanceAlert(FinanceAlertType.PAID_WITHOUT_QUOTA, null, null, "TESTUTEZ-ADM-2026-000001",
				"ADM-2026-000001", new BigDecimal("1750.00"), "La carrera ya estaba llena.",
				Instant.parse("2026-11-05T06:05:00Z"));
	}

	@Test
	void mailsEveryRecipientWithTheFolioOrderAmountAndLink() {
		FinanceAlertMailer mailer = new FinanceAlertMailer(publisher, mailSender, "a@utez.edu.mx, b@utez.edu.mx",
				"https://sisa.utez.edu.mx/SGA/", true);

		mailer.onAlertRaised(new FinanceAlertMailer.AlertRaised(alert()));

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(sent.capture());
		assertThat(sent.getValue().getTo()).containsExactly("a@utez.edu.mx", "b@utez.edu.mx");
		assertThat(sent.getValue().getSubject()).contains("Pago recibido sin cupo").contains("ADM-2026-000001");
		assertThat(sent.getValue().getText()).contains("TESTUTEZ-ADM-2026-000001").contains("1750.00")
				.contains("La carrera ya estaba llena.")
				.contains("https://sisa.utez.edu.mx/SGA/admision/alertas-pago");
	}

	@Test
	void withoutRecipientsNothingIsMailed() {
		FinanceAlertMailer mailer = new FinanceAlertMailer(publisher, mailSender, " ", "", true);

		mailer.onAlertRaised(new FinanceAlertMailer.AlertRaised(alert()));

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void raisingPublishesTheEvent() {
		FinanceAlertMailer mailer = new FinanceAlertMailer(publisher, mailSender, "", "", true);
		FinanceAlert alert = alert();

		mailer.alertRaised(alert);

		verify(publisher).publishEvent(new FinanceAlertMailer.AlertRaised(alert));
	}
}
