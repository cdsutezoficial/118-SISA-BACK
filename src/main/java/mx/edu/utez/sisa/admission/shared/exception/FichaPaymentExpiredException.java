package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when a checkout is attempted on a ficha whose payment window has
 * lapsed — the earlier of the applicant's own clock (day 0 = the registration
 * date, plus the configured deadline) and the closing day of her admission
 * process, counted in the admission calendar. Maps to HTTP 409 in the web
 * layer's {@code GlobalExceptionHandler} ({@code ADMISSION_FICHA_EXPIRED}).
 *
 * <p>One exception for both bounds because from the applicant's side they are
 * the same event: her ficha can no longer be paid, and which of the two dates
 * arrived first is not a fact she can act on. The checkout used to report the
 * process closing separately
 * ({@link ProgramAdmissionConfigSalesClosedException}), which told her a sale had
 * ended while the screen she was standing on still offered her a button.
 *
 * <p>Still distinct from {@link PaymentConceptExpiredException}, which is about
 * the tuition catalog — configuration that can be wrong, so it deserves its own
 * message. This one is about her ficha, which is not a configuration error.
 *
 * <p><b>No identifiers in the message.</b> This string is rendered to
 * applicants.
 */
public class FichaPaymentExpiredException extends RuntimeException {

	public FichaPaymentExpiredException(String message) {
		super(message);
	}
}
