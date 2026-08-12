package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;

import java.util.UUID;

/**
 * Fetches a single {@code PaymentConcept} by id. No special get-by-id
 * business rules beyond standard 404-if-missing (no FK relationships
 * reference this catalog yet — {@code PaymentRate}, Fase 2, will). Reuses
 * {@link PaymentConceptResult} — same shape as Create, same convention as
 * {@code GetSubjectClassificationUseCase} reusing {@code ClassificationResult}.
 */
public interface GetPaymentConceptUseCase {

	PaymentConceptResult getById(UUID id);
}
