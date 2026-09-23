package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when the payment-confirmation flow receives a candidate whose ficha
 * was already paid — the {@code PENDING → PAID} transition is not repeatable.
 * Maps to HTTP 409 (idempotency needs are served by treating the already-OK
 * state as success on the consumer side).
 */
public class CandidateAlreadyPaidException extends RuntimeException {

	public CandidateAlreadyPaidException(String message) {
		super(message);
	}
}