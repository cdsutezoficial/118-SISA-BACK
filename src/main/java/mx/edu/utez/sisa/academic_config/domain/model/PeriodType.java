package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * The academic calendar cadence an {@link AcademicPeriod} follows (docs:
 * {@code 02-config-academica.md} lines 151-166; RF-PROG-003 — "NO asumir
 * cuatrimestres fijos, 100% configurable", "debe soportar
 * bimestrales/semestrales a futuro (posgrados)"). Deliberately its own enum,
 * same rationale as {@code ClassificationStatus}/{@code DivisionStatus} —
 * every aggregate in this module owns its type/status enums rather than
 * sharing one across unrelated concepts.
 */
public enum PeriodType {
	CUATRIMESTRAL,
	SEMESTRAL,
	BIMESTRAL
}
