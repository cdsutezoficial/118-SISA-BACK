package mx.edu.utez.sisa.admission.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyExistsException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.FichaEmailSendException;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidCandidateFichaDataException;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotOpenException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Maps the {@code admission} bounded context's domain exceptions
 * ({@link OutreachChannelNotFoundException}, {@link HighSchoolTypeNotFoundException},
 * plus the candidate-registration family: {@code CandidateAlreadyExistsException},
 * {@code ProgramAdmissionConfigNotFoundException} (404),
 * {@code ProgramAdmissionConfigNotOpenException} (409),
 * {@code InvalidCandidateFichaDataException} (400),
 * {@code CandidateNotFoundException} (404 — payment-confirmation / ficha-read
 * family), {@code CandidateAlreadyPaidException} (409 — repeat
 * confirmation) and {@link FichaEmailSendException} (502 — the
 * "send instructions" email failed to deliver; BAD_GATEWAY since the SMTP
 * upstream is the failing dependency, never a client mistake))
 * to HTTP statuses (same "own {@code @RestControllerAdvice}, additive only"
 * decision as {@code academic_config.GlobalExceptionHandler}). Generic
 * handlers (bean validation, type-mismatch, catch-all) already exist
 * app-globally in {@code identity.GlobalExceptionHandler} and are
 * deliberately NOT redeclared here — a duplicate {@code @ExceptionHandler}
 * for the same exception type across two {@code @RestControllerAdvice} beans
 * is ambiguous.
 *
 * <p>Explicit {@link Component} bean name: this class,
 * {@code academic_config.infrastructure.web.GlobalExceptionHandler} and
 * {@code identity.infrastructure.web.GlobalExceptionHandler} all share the
 * same simple class name, which otherwise collide under Spring's default
 * annotation-derived bean naming — {@code @RestControllerAdvice} itself has
 * no name attribute, so an explicit {@code @Component} name is the
 * disambiguation.
 */
@RestControllerAdvice
@Component("admissionGlobalExceptionHandler")
public class GlobalExceptionHandler {

	@ExceptionHandler(HighSchoolTypeNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleHighSchoolTypeNotFound(HighSchoolTypeNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(ProgramAdmissionConfigNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleProgramAdmissionConfigNotFound(ProgramAdmissionConfigNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(OutreachChannelNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleOutreachChannelNotFound(OutreachChannelNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(CandidateNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleCandidateNotFound(CandidateNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(CandidateAlreadyExistsException.class)
	public ResponseEntity<ErrorResponse> handleCandidateAlreadyExists(CandidateAlreadyExistsException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(ProgramAdmissionConfigNotOpenException.class)
	public ResponseEntity<ErrorResponse> handleProgramAdmissionConfigNotOpen(ProgramAdmissionConfigNotOpenException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(CandidateAlreadyPaidException.class)
	public ResponseEntity<ErrorResponse> handleCandidateAlreadyPaid(CandidateAlreadyPaidException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(InvalidCandidateFichaDataException.class)
	public ResponseEntity<ErrorResponse> handleInvalidCandidateFichaData(InvalidCandidateFichaDataException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(FichaEmailSendException.class)
	public ResponseEntity<ErrorResponse> handleFichaEmailSend(FichaEmailSendException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}
