package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Lifecycle status of a {@link PaymentArea}. Deliberately its own enum (same
 * rationale as {@code PaymentConceptStatus}/{@code DivisionStatus} — every
 * aggregate in this module owns its status type rather than sharing one
 * across unrelated lifecycles).
 */
public enum PaymentAreaStatus {
	ACTIVE,
	INACTIVE
}
