package mx.edu.utez.sisa.finance.shared.exception;

/**
 * Thrown when the concept has no {@code PaymentRate} in force for the
 * requested {@code (programId, level)} combination. Maps to HTTP 409.
 *
 * <p>Deliberately an error and never a silent $0 or a fallback to the
 * concept's informative cost: an adeudo priced from anywhere nobody
 * configured would charge a number nobody set for that person.
 */
public class NoRateAvailableException extends RuntimeException {

	public NoRateAvailableException(String message) {
		super(message);
	}
}
