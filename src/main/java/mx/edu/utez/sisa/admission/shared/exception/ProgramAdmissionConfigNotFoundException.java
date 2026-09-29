package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when candidate registration references a
 * {@code ProgramAdmissionConfig} id that does not exist. Maps to HTTP 404 in
 * the web layer's {@code GlobalExceptionHandler}.
 */
public class ProgramAdmissionConfigNotFoundException extends RuntimeException {

	public ProgramAdmissionConfigNotFoundException(String message) {
		super(message);
	}
}