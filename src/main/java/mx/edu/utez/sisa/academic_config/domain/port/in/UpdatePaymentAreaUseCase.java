package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;

import java.util.UUID;

/**
 * Updates an existing {@code PaymentArea}'s catalog fields (does NOT touch
 * status — same convention as every other aggregate's Update use case in this
 * module). Re-applies the same name/code uniqueness validations as Create.
 */
public interface UpdatePaymentAreaUseCase {

	PaymentAreaResult updatePaymentArea(UpdatePaymentAreaCommand command);

	record UpdatePaymentAreaCommand(UUID paymentAreaId, String name, String code, String description) {
	}
}
