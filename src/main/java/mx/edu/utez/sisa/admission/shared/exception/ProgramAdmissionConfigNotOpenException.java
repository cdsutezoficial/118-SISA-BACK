package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when candidate registration references a
 * {@code ProgramAdmissionConfig} whose {@code status} is not {@code OPEN} —
 * the ticket-sales window is closed. Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler} (the resource exists but is in a state that
 * conflicts with the request), same convention as
 * {@code identity.DuplicateCurpException}'s 409.
 */
public class ProgramAdmissionConfigNotOpenException extends RuntimeException {

	public ProgramAdmissionConfigNotOpenException(String message) {
		super(message);
	}
}