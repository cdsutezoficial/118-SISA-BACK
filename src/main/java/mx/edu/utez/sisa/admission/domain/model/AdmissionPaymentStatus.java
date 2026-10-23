package mx.edu.utez.sisa.admission.domain.model;

/**
 * The ficha's payment status as admission shows it, per
 * {@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md}:
 * {@code PENDING → PAID}. It is read from the ficha's finance charge
 * ({@code PAID}/{@code EXEMPT} → {@code PAID}, anything else → {@code PENDING});
 * the charge is settled by {@code ConfirmAdmissionPaymentUseCase}.
 */
public enum AdmissionPaymentStatus {
	PENDING, PAID
}