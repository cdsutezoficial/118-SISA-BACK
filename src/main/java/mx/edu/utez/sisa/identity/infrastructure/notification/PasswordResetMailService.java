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
 * {@link NotificationPort} adapter for both credential emails (plan
 * {@code 2026-10-02-admin-reset-password.md}): the self-service
 * {@link #sendPasswordReset} link, and the {@link #sendTemporaryPassword} an
 * ADMIN assigns. Both use the same SMTP configuration and best-effort async
 * pattern as {@code CandidateRegistrationMailService}: a failed SMTP send is
 * logged and swallowed — it must never fail the caller's response nor delay it.
 *
 * <p>The plaintext password is never logged. A delivery failure logs only the
 * recipient and the exception message, which is the whole reason the ADMIN also
 * receives the password in the HTTP response: if the mail bounces, the reset
 * still succeeded and the value is still shown once.
 */
@Component
public class PasswordResetMailService implements NotificationPort {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

	private static final String RESET_SUBJECT = "Restablecimiento de contraseña — SISA UTEZ";
	private static final String TEMPORARY_PASSWORD_SUBJECT = "Contraseña temporal — SISA UTEZ";

	private final JavaMailSender mailSender;

	public PasswordResetMailService(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	@Override
	public void sendPasswordReset(String to, String resetLink) {
		CompletableFuture.runAsync(() -> {
			try {
				doSend(to, RESET_SUBJECT, resetLinkBody(resetLink));
			} catch (Exception ex) {
				log.warn("No se pudo enviar el correo de restablecimiento a {}: {}", to, ex.getMessage());
			}
		});
	}

	@Override
	public void sendTemporaryPassword(String to, String temporaryPassword) {
		CompletableFuture.runAsync(() -> {
			try {
				doSend(to, TEMPORARY_PASSWORD_SUBJECT, temporaryPasswordBody(temporaryPassword));
			} catch (Exception ex) {
				log.warn("No se pudo enviar el correo de contraseña temporal a {}: {}", to, ex.getMessage());
			}
		});
	}

	private void doSend(String to, String subject, String body) throws Exception {
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
		helper.setTo(to);
		helper.setSubject(subject);
		helper.setText(body);
		mailSender.send(message);
	}

	private static String resetLinkBody(String resetLink) {
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

	private static String temporaryPasswordBody(String temporaryPassword) {
		return String.format("""
				Hola:

				El administrador del sistema restableció tu contraseña de acceso a SISA.

				Tu contraseña temporal es:
				%s

				Por seguridad, el sistema te pedirá cambiarla en tu primer inicio de sesión.
				Las sesiones que tenías abiertas se cerraron con este restablecimiento.

				Si no esperabas este aviso, comunicate con el administrador del sistema.

				Saludos,
				Universidad Tecnológica de la zona de UTEZ
				SISA — Sistema Integral de Servicios Académicos""", temporaryPassword);
	}
}