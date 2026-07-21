package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Lifecycle status of an {@link AcademicPeriod} (docs:
 * {@code 02-config-academica.md} lines 151-166 — "controla qué operaciones
 * están habilitadas en el sistema en cada momento del ciclo escolar").
 * Unlike every other status enum in this module ({@code ClassificationStatus},
 * {@code DivisionStatus}, {@code ProgramStatus}, {@code PlanStatus} — all
 * binary ACTIVE/INACTIVE toggles), this is a 4-state lifecycle with
 * PO-confirmed (2026-07-20) strictly sequential, forward-only transitions:
 * {@code CONFIGURATION -> ENROLLMENT -> ACTIVE -> CLOSED}. No skips (e.g.
 * {@code CONFIGURATION -> ACTIVE} directly) and no going backward (e.g.
 * {@code CLOSED -> ACTIVE}) — enforced by {@link AcademicPeriod#changeStatus}.
 */
public enum PeriodStatus {
	CONFIGURATION,
	ENROLLMENT,
	ACTIVE,
	CLOSED
}
