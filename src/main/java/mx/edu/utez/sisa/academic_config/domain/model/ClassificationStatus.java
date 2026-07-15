package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Lifecycle status of a {@link SubjectClassification}. Deliberately its own
 * enum (same rationale as {@code DivisionStatus}/{@code ProgramStatus} —
 * every aggregate in this module owns its status type rather than sharing
 * one across unrelated lifecycles).
 */
public enum ClassificationStatus {
	ACTIVE,
	INACTIVE
}
