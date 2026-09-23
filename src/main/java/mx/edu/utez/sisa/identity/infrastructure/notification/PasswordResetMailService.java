package mx.edu.utez.sisa.identity.infrastructure.notification;

import jakarta.mail.internet.MimeMessage;
import mx.edu.utez.sisa.identity.domain.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * {@link NotificationPort} adapter sending the password-reset link
 * (01-identidad.md — NotificationPort) via the same SMTP configuration and
 * best-effort async pattern as {@code CandidateRegistrationMailService}: a
 * failed SMTP send is logged and swallowed — it must never fail the
 * forgot-password response nor delay it. The reset link is built by the use
 * case; this class only transports the email.
 */
@Component
public class PasswordResetMailService implements NotificationPort {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

	private static final String SUBJECT = "Restablecimiento de contraseña — SISA UTEZ";

	private final JavaMailSender mailSender;

	public PasswordResetMailService(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	@Override
	public void sendPasswordReset(String to, String resetLink) {
		CompletableFuture.runAsync(() -> {
			try {
				doSend(to, resetLink);
			} catch (Exception ex) {
				log.warn("No se pudo enviar el correo de restablecimiento a {}: {}", to, ex.getMessage());
			}
		});
	}

	private void doSend(String to, String resetLink) throws Exception {
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
		helper.setTo(to);
		helper.setSubject(SUBJECT);
		helper.setText(body(resetLink));
		mailSender.send(message);
	}

	private static String body(String resetLink) {
		return String.format("""
				Hola:

				Recibimos una solicitud para restablecer tu contraseña de acceso a SISA.

				Para continuar, abre el siguiente enlace (válido por 30 minutos):
				%s

				Si tú no hiciste esta solicitud, ignora este correo.

				Saludos,
				Universidad Tecnológica de la zona de UTEZ
				SISA — Sistema Integral de Servicios Académicos""", resetLink);
	}
}