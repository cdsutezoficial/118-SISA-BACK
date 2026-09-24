package mx.edu.utez.sisa.admission.shared.exception;

/**
 * A payment-confirmation attempt that fails EVO verification (failed /
 * pending / unknown gateway result, mismatched amount, mismatched or missing
 * {@code order.id}) → HTTP 400. The applicant cannot fix a {code PENDING} or
 * mismatched processor result by retrying the same confirmation, so the
 * message tells her to retry ("inténtalo de nuevo") — Fase 5 of the payment
 * plan (no false "pagado" without a SUCCESS verdict from EVO).
 */
public class InvalidPaymentVerificationException extends RuntimeException {

	public InvalidPaymentVerificationException(String message) {
		super(message);
	}
}