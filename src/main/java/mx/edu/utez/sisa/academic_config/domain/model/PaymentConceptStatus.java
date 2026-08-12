package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Lifecycle status of a {@link PaymentConcept}. Deliberately its own enum
 * (same rationale as {@code ClassificationStatus}/{@code DivisionStatus} —
 * every aggregate in this module owns its status type rather than sharing
 * one across unrelated lifecycles).
 */
public enum PaymentConceptStatus {
	ACTIVE,
	INACTIVE
}
