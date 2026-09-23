package mx.edu.utez.sisa.admission.domain.model;

/**
 * Lifecycle of an {@link AdmissionPayment}, per
 * {@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md}:
 * {@code PENDING → PAID}. Created {@code PENDING} together with its
 * {@link Candidate}; transitioned to {@code PAID} by
 * {@code ConfirmAdmissionPaymentUseCase} (today: the portal "Pagar en línea"
 * trigger; later: the EVO webhook / manual window capture).
 */
public enum AdmissionPaymentStatus {
	PENDING, PAID
}