package mx.edu.utez.sisa.admission.shared.exception;

/**
 * F1: the ficha owes nothing the cash desk can collect, because a 100 % discount covers
 * it. The exemption is settled from the portal, with its own {@code $0} receipt. Maps to
 * HTTP 409 {@code ADMISSION_NOTHING_TO_COLLECT}.
 */
public class NothingToCollectException extends RuntimeException {

	public NothingToCollectException(String message) {
		super(message);
	}
}
