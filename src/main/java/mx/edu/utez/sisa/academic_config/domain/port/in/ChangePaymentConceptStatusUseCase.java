package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;

import java.util.UUID;

/**
 * Toggles a {@code PaymentConcept}'s status between {@code ACTIVE} and
 * {@code INACTIVE}. Single interactor parameterized by target status,
 * mirroring {@code ChangeSubjectClassificationStatusUseCase}.
 */
public interface ChangePaymentConceptStatusUseCase {

	PaymentConceptResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId        reserved for future audit-log attribution; not consulted in this slice —
	 *                        mirrors {@code ChangeSubjectClassificationStatusUseCase.ChangeStatusCommand}
	 * @param paymentConceptId the concept whose status is transitioning
	 * @param target          the desired {@link PaymentConceptStatus} — {@code ACTIVE} or {@code INACTIVE}
	 */
	record ChangeStatusCommand(UUID callerId, UUID paymentConceptId, PaymentConceptStatus target) {
	}
}
