package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when a checkout is attempted on a ficha whose own payment window has
 * lapsed — day 0 (the registration date) plus the configured deadline, counted
 * in the admission calendar. Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler} ({@code ADMISSION_FICHA_EXPIRED}).
 *
 * <p>Distinct from {@link PaymentConceptExpiredException} and
 * {@link ProgramAdmissionConfigSalesClosedException}: those are about the
 * catalog period and the admission process' dates, which are facts about the
 * configuration; this one is about the applicant's own 10-day clock, which
 * starts the day she registers and is the same for every ficha. It is the
 * first gate the checkout applies, because when the private deadline is the
 * shorter of the two it is the one that actually closed.
 *
 * <p><b>No identifiers in the message.</b> This string is rendered to
 * applicants.
 */
public class FichaPaymentExpiredException extends RuntimeException {

	public FichaPaymentExpiredException(String message) {
		super(message);
	}
}
