package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Lifecycle status of a {@link Group}. Same class of status as
 * {@link GenerationStatus} — a simple 2-value toggle, NOT a sequential state
 * machine like {@link PeriodStatus}'s 4 states. Both directions are valid at
 * any time — {@code PATCH /groups/{id}/status} enforces no sequence.
 */
public enum GroupStatus {
	OPEN, CLOSED
}
