package mx.edu.utez.sisa.admission.shared.exception;

/**
 * E4: the bank captured a ficha's payment after its checkout hold ran out, and by then
 * the career had no place left. The money was kept as saldo a favor of the person and the
 * attempt was closed before this was thrown, so it reports an outcome, not a failure to
 * retry. Maps to HTTP 409 {@code ADMISSION_PAID_WITHOUT_QUOTA}.
 */
public class AdmissionPaidWithoutQuotaException extends RuntimeException {

	public AdmissionPaidWithoutQuotaException(String message) {
		super(message);
	}
}
