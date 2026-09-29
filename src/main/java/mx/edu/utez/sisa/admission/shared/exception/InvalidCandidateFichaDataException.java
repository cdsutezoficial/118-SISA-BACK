package mx.edu.utez.sisa.admission.shared.exception;

/**
 * Thrown by the web layer's {@code CandidateController} when a request field
 * fails a format contract that bean validation cannot express — e.g.
 * {@code fechaNacimiento} not in {@code dd/MM/yyyy} or a work hour not in
 * {@code HH:mm} (the repo has no Jackson date-format configuration, so the
 * strings arrive here straight from the frontend ficha). Maps to HTTP 400 in
 * the web layer's {@code GlobalExceptionHandler} — same family as the
 * generic {@code MethodArgumentNotValidException} handler that 400s bean
 * violations.
 */
public class InvalidCandidateFichaDataException extends RuntimeException {

	public InvalidCandidateFichaDataException(String message) {
		super(message);
	}
}