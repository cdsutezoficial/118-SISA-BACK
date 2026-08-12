package mx.edu.utez.sisa.shared.model;

/**
 * Turno (delivery time-of-day) a {@code Group} operates in, shared across
 * every bounded context that needs it. Defined once in the shared kernel per
 * {@code 00-shared-kernel.md} — no module owns this enum. First consumer:
 * {@code academic_config.domain.model.Group} (plan:
 * {@code docs/plans/2026-07-20-generation-group.md}).
 */
public enum Shift {
	MORNING,
	AFTERNOON,
	MIXED
}
