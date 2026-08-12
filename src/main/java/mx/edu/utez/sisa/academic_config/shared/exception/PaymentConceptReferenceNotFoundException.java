package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a {@code conceptId} provided to {@code SetPaymentRateUseCase} is
 * missing or does not resolve to an existing {@code PaymentConcept}.
 * Deliberately distinct from {@code PaymentConceptNotFoundException} (404): a
 * 404 there means "the PaymentConcept resource itself wasn't found"
 * (Get-by-id/Update/ChangeStatus on {@code /payment-concepts}); here the
 * *PaymentRate* request references a bad FK — same shape as
 * {@code GenerationReferenceNotFoundException}/{@code PlanNotFoundException}/
 * {@code PeriodNotFoundException} (all 400, not resource-not-found).
 * {@code PaymentRate} is architecturally a separate aggregate from
 * {@code PaymentConcept} (own repository — plan section 5), so this follows
 * the same cross-aggregate-FK convention as {@code Group}'s validation of
 * {@code generationId}, not the same-aggregate-child convention {@code PlanLevel}
 * uses for {@code planId} (which reuses the parent's own 404). Maps to HTTP
 * 400 in the web layer's {@code GlobalExceptionHandler}.
 */
public class PaymentConceptReferenceNotFoundException extends RuntimeException {

	public PaymentConceptReferenceNotFoundException(String message) {
		super(message);
	}
}
