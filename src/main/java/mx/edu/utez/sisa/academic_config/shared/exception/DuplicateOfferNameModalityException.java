package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateAcademicProgramUseCase} or
 * {@code UpdateAcademicProgramUseCase} is invoked with an
 * {@code (offerName, modality)} pair already used by another
 * {@code AcademicProgram} (spec: "Rejects duplicate (offerName, modality)
 * pair" / "Rejects update to an (offerName, modality) pair already used by
 * another program"). The same {@code offerName} with a different
 * {@code modality} MUST NOT trigger this exception (spec: "Same offerName
 * with a different modality succeeds"). Maps to HTTP 409 in the web layer's
 * {@code GlobalExceptionHandler} (Phase 6).
 */
public class DuplicateOfferNameModalityException extends RuntimeException {

	public DuplicateOfferNameModalityException(String message) {
		super(message);
	}
}
