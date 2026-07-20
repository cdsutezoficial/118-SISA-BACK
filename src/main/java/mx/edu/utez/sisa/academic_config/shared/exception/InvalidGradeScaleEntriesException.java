package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when the {@code GradeScaleEntry} ranges submitted to
 * {@code AcademicPlan.setGradeScale}/{@code updateGradeScale} do not exactly
 * cover the scale's {@code [numericMin, numericMax]} — either a gap (some
 * numeric value in range has no entry) or an overlap (some numeric value
 * falls inside two entries) between two adjacent, sorted-by-{@code fromValue}
 * entries (PO decision 2026-07-20, docs/plans/2026-07-20-grade-scale.md §4:
 * "evita que, al calificar, un valor numérico se quede sin letra asignada o
 * caiga en dos tramos a la vez"). Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler} — same convention as
 * {@code InvalidPlanDataException}, kept as its own type (rather than reused)
 * so the message can be specific about which of the two failure modes
 * (gap/overlap) triggered it.
 */
public class InvalidGradeScaleEntriesException extends RuntimeException {

	public InvalidGradeScaleEntriesException(String message) {
		super(message);
	}
}
