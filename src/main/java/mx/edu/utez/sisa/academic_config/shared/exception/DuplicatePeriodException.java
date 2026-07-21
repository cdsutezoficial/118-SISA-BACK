package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateAcademicPeriodUseCase}/{@code UpdateAcademicPeriodUseCase}
 * is invoked with a {@code (year, periodNumber)} pair already used by another
 * {@code AcademicPeriod} (plan: {@code docs/plans/2026-07-20-academic-period.md}
 * §4 — "unicidad (year, periodNumber)": the domain doc's own description of
 * {@code periodNumber} as "1, 2, 3 dentro del año" implies it cannot repeat
 * within the same year). Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler} — same pattern as
 * {@code DuplicateClassificationCodeException}/{@code DuplicateDivisionCodeException}.
 */
public class DuplicatePeriodException extends RuntimeException {

	public DuplicatePeriodException(String message) {
		super(message);
	}
}
