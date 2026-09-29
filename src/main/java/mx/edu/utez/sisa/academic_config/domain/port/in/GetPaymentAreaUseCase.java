package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;

import java.util.UUID;

/**
 * Fetches a single {@code PaymentArea} by id. No special get-by-id business
 * rules beyond standard 404-if-missing. Reuses {@link PaymentAreaResult} —
 * same shape as Create.
 */
public interface GetPaymentAreaUseCase {

	PaymentAreaResult getById(UUID id);
}
