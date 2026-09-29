package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when candidate registration falls outside a
 * {@code ProgramAdmissionConfig}'s ticket-sales window
 * ({@code opensAt}/{@code closesAt}) — either the sale has not opened yet or it
 * already closed. Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler}.
 *
 * <p>Distinct from {@code ProgramAdmissionConfigNotOpenException}: that one is
 * about the {@code status} toggle, which staff flip by hand and which says
 * nothing about the calendar. This one is what makes the dates on the
 * Configuración de Admisión screen mean what their labels say. The message
 * carries the boundary that was missed and no identifiers, so it can be shown
 * to the applicant as-is.
 */
public class ProgramAdmissionConfigSalesClosedException extends RuntimeException {

	public ProgramAdmissionConfigSalesClosedException(String message) {
		super(message);
	}
}
