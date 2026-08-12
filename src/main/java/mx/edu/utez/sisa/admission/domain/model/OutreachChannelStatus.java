package mx.edu.utez.sisa.admission.domain.model;

/**
 * Lifecycle status of an {@link OutreachChannel}. Deliberately its own enum
 * (same rationale as {@code academic_config}'s {@code ClassificationStatus}/
 * {@code DivisionStatus} — every aggregate in this codebase owns its status
 * type rather than sharing one across unrelated lifecycles).
 */
public enum OutreachChannelStatus {
	ACTIVE,
	INACTIVE
}
