package mx.edu.utez.sisa.admission.infrastructure.notification;

import jakarta.mail.internet.MimeMessage;
import mx.edu.utez.sisa.admission.shared.exception.FichaEmailSendException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Sends the applicant's admission-ficha emails: payment instructions
 * ({@code sendPaymentInstructions}, triggered from the portal's "Enviar
 * instrucciones a mi correo" button) and the paid-ficha confirmation
 * ({@code sendPaymentConfirmation}, triggered once {@code ConfirmAdmissionPaymentUseCase}
 * marks the ticket {@code PAID}).
 *
 * <p>Best-effort by default: a failed SMTP send is logged with the FULL stack
 * trace and swallowed — it must never fail the HTTP response nor delay it. To
 * keep the HTTP answer fast, delivery fires on a separate thread via
 * {@link CompletableFuture#runAsync}; no {@code @EnableAsync} is needed for
 * that.
 *
 * <p>The payment-INSTRUCTIONS resend breaks that rule on purpose:
 * {@link #sendPaymentInstructionsSync} is always synchronous and rethrows
 * {@link FichaEmailSendException} on SMTP failure, so the portal's "Enviar
 * instrucciones a mi correo" button shows the real failure (HTTP 502) instead
 * of a fake 204 — while the payment-confirmation email stays best-effort.
 *
 * <p>When {@code sisa.mail.fail-fast=true} (dev-only flag) the async methods
 * also deliver synchronously, so a broken SMTP setup surfaces promptly in
 * development.
 *
 * <p>SMTP configuration lives in {@code .env} ({@code spring.mail.*}),
 * consumed by {@code application.properties}' {@code spring.config.import} —
 * see {@code docs/plans} for the mail integration notes.
 */
@Component
public class CandidateFichaMailService {

	private static final Logger log = LoggerFactory.getLogger(CandidateFichaMailService.class);

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final JavaMailSender mailSender;

	private final boolean failFast;

	public CandidateFichaMailService(JavaMailSender mailSender,
			@Value("${sisa.mail.fail-fast:false}") boolean failFast) {
		this.mailSender = mailSender;
		this.failFast = failFast;
	}

	/**
	 * Sends payment instructions for the admission ticket: folio, amount,
	 * payment reference and deadline. Asynchronous unless {@code fail-fast} —
	 * best-effort, a failed SMTP delivery never fails the HTTP response.
	 */
	public void sendPaymentInstructions(String to, String name, String folio, String program,
			BigDecimal amount, String reference, LocalDate deadline) {
		dispatch(() -> doSend(to, "Instrucciones de pago — Ficha de Admisión UTEZ",
				instructionsBody(name, folio, program, amount, reference, deadline), false));
	}

	/**
	 * Sends payment instructions SYNCHRONOUSLY, failing the call with
	 * {@link FichaEmailSendException} when SMTP delivery fails. Used by the
	 * portal's "Enviar instrucciones a mi correo" button so a broken delivery
	 * surfaces to the applicant as an HTTP 502 with the SMTP cause instead of
	 * the old fake 204.
	 */
	public void sendPaymentInstructionsSync(String to, String name, String folio, String program,
			BigDecimal amount, String reference, LocalDate deadline) {
		doSend(to, "Instrucciones de pago — Ficha de Admisión UTEZ",
				instructionsBody(name, folio, program, amount, reference, deadline), true);
	}

	/**
	 * Sends the confirmation that the ficha was paid (candidate now {@code PAID}):
	 * folio, amount, reference, deadline and receipt. Asynchronous unless
	 * {@code fail-fast} — best-effort, never fails the HTTP response.
	 */
	public void sendPaymentConfirmation(String to, String name, String folio, String program,
			BigDecimal amount, String reference, LocalDate deadline, String receipt) {
		dispatch(() -> doSend(to, "Confirmación de pago — Ficha de Admisión UTEZ",
				confirmationBody(name, folio, program, amount, reference, deadline, receipt), false));
	}

	private void dispatch(Runnable send) {
		if (failFast) {
			send.run();
			return;
		}
		CompletableFuture.runAsync(send);
	}

	private void doSend(String to, String subject, String body, boolean sync) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(body);
			mailSender.send(message);
		} catch (Exception ex) {
			if (sync) {
				// Surface the real SMTP cause (auth refused, DNS/network, TLS…) to
				// the caller so the 502 shows why the email did not arrive.
				throw new FichaEmailSendException(failureMessage(to, ex), ex);
			}
			// Full stack trace on purpose — the silent-getMessage() failure mode
			// of the old registration mail hid the SMTP root cause.
			log.warn("No se pudo enviar el correo de ficha a {} (asunto '{}'): ", to, subject, ex);
		}
	}

	private static String failureMessage(String to, Exception ex) {
		Throwable root = ex;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String detail = root.getMessage();
		if (detail == null || detail.isBlank()) {
			detail = root.getClass().getSimpleName();
		}
		String oneLine = detail.replaceAll("\\s+", " ").trim();
		if (oneLine.length() > 200) {
			oneLine = oneLine.substring(0, 200) + "…";
		}
		return "No se pudo enviar el correo de instrucciones a " + to + ": " + oneLine;
	}

	private static String instructionsBody(String name, String folio, String program, BigDecimal amount,
			String reference, LocalDate deadline) {
		return String.format("""
				Hola %s:

				Tu ficha de admisión fue generada correctamente. Para completar tu registro debes pagar el monto de la ficha antes de la fecha límite.

				Ficha de admisión
				Folio: %s
				Carrera: %s
				Monto a pagar: $%s MXN
				Referencia de pago: %s
				Fecha límite de pago: %s

				Métodos de pago:
				1. En línea: paga con tarjeta o transferencia desde el portal con el botón "Pagar en línea".
				2. En ventanilla: presenta tu referencia en la ventanilla de Finanzas de la universidad.

				Una vez confirmado tu pago recibirás un correo de confirmación y podrás continuar con el proceso de admisión.

				Saludos,
				Universidad Tecnológica de la zona de UTEZ
				SISA — Sistema Integral de Servicios Académicos""",
				name, folio, program, formatAmount(amount), reference, formatDate(deadline));
	}

	private static String confirmationBody(String name, String folio, String program, BigDecimal amount,
			String reference, LocalDate deadline, String receipt) {
		return String.format("""
				Hola %s:

				Tu pago de ficha de admisión fue confirmado. Con esto tu registro de aspirante quedó completado.

				Detalle del pago
				Folio: %s
				Carrera: %s
				Monto pagado: $%s MXN
				Referencia de pago: %s
				Fecha de pago: %s
				Recibo: %s

				Conserva tu folio, lo necesitarás para los siguientes pasos del proceso de admisión.

				Saludos,
				Universidad Tecnológica de la zona de UTEZ
				SISA — Sistema Integral de Servicios Académicos""",
				name, folio, program, formatAmount(amount), reference, formatDate(LocalDate.now()), receipt);
	}

	private static String formatAmount(BigDecimal amount) {
		return amount == null ? "0.00" : amount.toPlainString();
	}

	private static String formatDate(LocalDate date) {
		return date == null ? "-" : date.format(DATE_FORMATTER);
	}
}