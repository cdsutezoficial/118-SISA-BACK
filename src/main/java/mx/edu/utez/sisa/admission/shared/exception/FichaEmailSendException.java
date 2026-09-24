package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when the payment-instructions email ("Enviar instrucciones a mi
 * correo" button) cannot be delivered. Unlike the best-effort (async,
 * swallowed) confirmation email, the instructions resend must surface the
 * delivery failure to the applicant instead of silently returning 204 — this
 * exception maps to HTTP 502 (BAD_GATEWAY) with a user-facing Spanish message
 * that includes the underlying SMTP cause for diagnosis.
 */
public class FichaEmailSendException extends RuntimeException {

	public FichaEmailSendException(String message) {
		super(message);
	}

	public FichaEmailSendException(String message, Throwable cause) {
		super(message, cause);
	}
}