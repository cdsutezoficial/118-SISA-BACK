package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when the concept exists but cannot be sold today — it is INACTIVE or
 * the date is outside {@code availableFrom/availableUntil}. Maps to HTTP 409:
 * the request parsed, and the refusal is about catalog state, not about the
 * payload. Kept apart from {@link ConceptNotFoundException} for the same
 * reason the admission flow separates the two cases: "no existe" and "ya no
 * está a la venta" are different problems for the person at the counter.
 */
public class ConceptUnavailableException extends RuntimeException {

	public ConceptUnavailableException(String message) {
		super(message);
	}
}
