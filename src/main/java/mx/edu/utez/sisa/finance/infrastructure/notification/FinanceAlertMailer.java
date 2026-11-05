package mx.edu.utez.sisa.finance.infrastructure.notification;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;
import mx.edu.utez.sisa.finance.domain.port.out.FinanceAlertNotifier;

/**
 * Mails Finanzas when an alert is opened (E4), after the transaction that opened it
 * commits.
 *
 * <p>The recipients are {@code sisa.finance.alerts.recipients}, comma separated. Left
 * blank, nothing is mailed and the alert is still on the review screen: the screen is
 * the record, the mail only points at it. A failed send is logged and never undoes the
 * alert.
 */
@Component
public class FinanceAlertMailer implements FinanceAlertNotifier {

	private static final Logger log = LoggerFactory.getLogger(FinanceAlertMailer.class);

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
			.withZone(ZoneId.of("America/Mexico_City"));

	private final ApplicationEventPublisher publisher;

	private final JavaMailSender mailSender;

	private final List<String> recipients;

	private final String screenUrl;

	private final boolean failFast;

	public FinanceAlertMailer(ApplicationEventPublisher publisher, JavaMailSender mailSender,
			@Value("${sisa.finance.alerts.recipients:}") String recipients,
			@Value("${sisa.security.password-reset.frontend-base-url:}") String frontendBaseUrl,
			@Value("${sisa.mail.fail-fast:false}") boolean failFast) {
		this.publisher = publisher;
		this.mailSender = mailSender;
		this.recipients = Arrays.stream(recipients.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
		this.screenUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank() ? null
				: frontendBaseUrl.trim().replaceAll("/+$", "") + "/admision/alertas-pago";
		this.failFast = failFast;
	}

	@Override
	public void alertRaised(FinanceAlert alert) {
		publisher.publishEvent(new AlertRaised(alert));
	}

	@TransactionalEventListener(fallbackExecution = true)
	public void onAlertRaised(AlertRaised event) {
		if (recipients.isEmpty()) {
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(recipients.toArray(String[]::new));
		message.setSubject("Alerta de pago para revisión — " + title(event.alert()) + folioSuffix(event.alert()));
		message.setText(body(event.alert()));
		Runnable send = () -> {
			try {
				mailSender.send(message);
			} catch (Exception ex) {
				log.warn("No se pudo enviar la alerta de Finanzas de la orden {}: ", event.alert().getOrderId(), ex);
			}
		};
		if (failFast) {
			send.run();
		} else {
			CompletableFuture.runAsync(send);
		}
	}

	String body(FinanceAlert alert) {
		StringBuilder text = new StringBuilder();
		text.append(title(alert)).append("\n\n");
		text.append("Folio: ").append(alert.getFolio() == null ? "-" : alert.getFolio()).append('\n');
		text.append("Orden: ").append(alert.getOrderId()).append('\n');
		text.append("Monto: $").append(alert.getAmount() == null ? "-" : alert.getAmount().toPlainString())
				.append(" MXN\n");
		text.append("Detectada: ").append(DATE_TIME.format(alert.getCreatedAt())).append("\n\n");
		if (alert.getDetail() != null) {
			text.append(alert.getDetail()).append("\n\n");
		}
		text.append(screenUrl != null ? "Revísala en " + screenUrl
				: "Revísala en SISA, en Admisión > Alertas de pago.").append('\n');
		return text.toString();
	}

	static String title(FinanceAlert alert) {
		return switch (alert.getType()) {
			case AUTHORIZED_NOT_CAPTURED -> "Monto autorizado sin cobrar";
			case PAID_WITHOUT_QUOTA -> "Pago recibido sin cupo";
			case OVERPAYMENT -> "Pago que no liquidó ningún cargo";
		};
	}

	private static String folioSuffix(FinanceAlert alert) {
		return alert.getFolio() == null ? "" : " — " + alert.getFolio();
	}

	/** Published inside the raising transaction, handled after it commits. */
	public record AlertRaised(FinanceAlert alert) {
	}
}
