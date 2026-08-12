package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a caller-supplied {@code ProgramAdmissionConfig} field fails a
 * pure same-entity invariant enforced by the domain model itself:
 * {@code maxCandidates <= 0}, or {@code closesAt} not strictly after
 * {@code opensAt} (plan: {@code docs/plans/2026-07-28-program-admission-config.md}
 * §7 — same class of two-field date-ordering invariant as
 * {@code AcademicPeriod#validateDateRanges}, which is why the check lives in
 * {@link mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig}'s
 * constructor/{@code updateDetails}, not in a use-case interactor like
 * {@code InvalidPaymentConceptDataException}'s independent per-field range
 * checks). Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler}.
 */
public class InvalidProgramAdmissionConfigDataException extends RuntimeException {

	public InvalidProgramAdmissionConfigDataException(String message) {
		super(message);
	}
}
