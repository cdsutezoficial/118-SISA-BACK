package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code SetGradeScaleUseCase}/{@code UpdateGradeScaleUseCase}
 * (or {@code AcademicPlan.setGradeScale}/{@code updateGradeScale}) would
 * result in two {@link mx.edu.utez.sisa.academic_config.domain.model.GradeScale}
 * records sharing the same {@code classificationId} within the same plan
 * (spec: "a plan cannot have two scales for the same classification"). Maps
 * to HTTP 409 in the web layer's {@code GlobalExceptionHandler} — same
 * convention as {@code DuplicateLevelNumberException}.
 */
public class DuplicateGradeScaleException extends RuntimeException {

	public DuplicateGradeScaleException(String message) {
		super(message);
	}
}
