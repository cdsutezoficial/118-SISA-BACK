package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown when candidate registration is attempted with a {@code curp} that
 * already belongs to a {@code Person} — either a previously registered
 * candidate or a conflicting staff row (plan: {@code RegisterCandidateUseCase},
 * duplicate-CURP rule "no puede haber dos candidatos/Personas con el mismo
 * CURP"). Maps to HTTP 409 in the web layer's {@code GlobalExceptionHandler},
 * same convention as {@code identity.DuplicateCurpException}.
 */
public class CandidateAlreadyExistsException extends RuntimeException {

	public CandidateAlreadyExistsException(String message) {
		super(message);
	}
}