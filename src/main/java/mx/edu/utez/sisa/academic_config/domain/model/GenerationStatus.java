package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Lifecycle status of a {@link Generation}. Unlike {@link PeriodStatus}'s
 * strictly sequential 4-state machine, this is a simple 2-value toggle — same
 * class of status as {@link ClassificationStatus}/{@link DivisionStatus}/
 * {@link ProgramStatus}/{@link PlanStatus} (ACTIVE/INACTIVE-shaped), just
 * named {@code FINISHED} instead of {@code INACTIVE} to match the domain
 * vocabulary in {@code 02-config-academica.md} lines 170-182. Both directions
 * are valid at any time — {@code PATCH /generations/{id}/status} enforces no
 * sequence.
 */
public enum GenerationStatus {
	ACTIVE, FINISHED
}
