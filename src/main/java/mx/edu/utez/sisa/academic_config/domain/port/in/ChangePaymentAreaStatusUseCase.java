package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;

import java.util.UUID;

/**
 * Toggles a {@code PaymentArea}'s status between {@code ACTIVE} and
 * {@code INACTIVE}. Single interactor parameterized by target status.
 */
public interface ChangePaymentAreaStatusUseCase {

	PaymentAreaResult changeStatus(ChangeStatusCommand command);

	/**
	 * @param callerId      reserved for future audit-log attribution; not consulted in this slice
	 * @param paymentAreaId the area whose status is transitioning
	 * @param target        the desired {@link PaymentAreaStatus} — {@code ACTIVE} or {@code INACTIVE}
	 */
	record ChangeStatusCommand(UUID callerId, UUID paymentAreaId, PaymentAreaStatus target) {
	}
}
