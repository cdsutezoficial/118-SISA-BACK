package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code AddSubjectToPlanUseCase}/{@code UpdateSubjectUseCase}
 * (or {@code AcademicPlan.addSubject}/{@code updateSubject}) would result in
 * two {@link mx.edu.utez.sisa.academic_config.domain.model.Subject} records
 * sharing the same {@code code} within the same plan, across all of its
 * levels (spec: "Rejects duplicate code within the same plan"). Maps to HTTP
 * 409 in the web layer's {@code GlobalExceptionHandler} (Phase 6).
 */
public class DuplicateSubjectCodeException extends RuntimeException {

	public DuplicateSubjectCodeException(String message) {
		super(message);
	}
}
