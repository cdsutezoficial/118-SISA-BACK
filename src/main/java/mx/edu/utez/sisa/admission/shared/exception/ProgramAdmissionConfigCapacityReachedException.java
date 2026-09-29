package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when a {@code ProgramAdmissionConfig} has already sold as many fichas
 * as its {@code maxCandidates} allows. Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler}.
 *
 * <p>The quota counts <em>paid</em> fichas, not registrations: a hundred people
 * may sign up and the program keeps showing while fewer than
 * {@code maxCandidates} have paid. That is the "pueden registrarse 100 pero
 * solo pagan 15" rule, and it is why the count behind this exception is a query
 * over {@code AdmissionPayment} rather than a counter on the config.
 */
public class ProgramAdmissionConfigCapacityReachedException extends RuntimeException {

	public ProgramAdmissionConfigCapacityReachedException(String message) {
		super(message);
	}
}
