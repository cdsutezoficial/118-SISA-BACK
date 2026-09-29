package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when the ficha's tuition concept DOES exist for the candidate's
 * program, but its availability window
 * ({@code payment_concept.available_from} / {@code available_until}) does not
 * contain today. Maps to HTTP 409.
 *
 * <p>Distinct from {@link FichaPaymentConceptNotFoundException} on purpose.
 * Before this existed, "the concept is missing" and "the sales period is over"
 * both arrived as the same empty result and had to be reported with the same
 * message, which sent applicants to a screen about a misconfigured catalog when
 * the honest answer was simply that the period closed on a date they could see
 * on the Conceptos de Pago screen.
 *
 * <p>Both boundaries of the window produce this exception. A period that has
 * not started yet is the same class of answer as one that has ended — "not
 * today" — and the message names whichever boundary was missed, so the
 * distinction survives in the text even though it does not need a separate type.
 *
 * <p><b>No UUIDs in the message.</b> This string is rendered to applicants.
 * Naming a program's internal id tells a reader nothing they can act on while
 * confirming to a prober which ids exist, so the date and nothing else.
 */
public class PaymentConceptExpiredException extends RuntimeException {

	public PaymentConceptExpiredException(String message) {
		super(message);
	}
}
