package mx.edu.utez.sisa.academic_config.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicDivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DirectorNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateDivisionNameException;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Maps {@code academic_config}'s 4 domain exceptions to HTTP status codes
 * (design.md — Decision: Own {@code @RestControllerAdvice}, additive only).
 * Generic handlers (bean validation, type-mismatch, catch-all) already exist
 * app-globally in {@code identity.GlobalExceptionHandler} and are
 * deliberately NOT redeclared here — a duplicate {@code @ExceptionHandler}
 * for the same exception type across two {@code @RestControllerAdvice} beans
 * is ambiguous.
 *
 * <p>Explicit {@link Component} bean name: both this class and
 * {@code identity.infrastructure.web.GlobalExceptionHandler} share the same
 * simple class name, which otherwise collide under Spring's default
 * annotation-derived bean naming (both would resolve to
 * {@code "globalExceptionHandler"}) — {@code @RestControllerAdvice} itself
 * has no name attribute, so an explicit {@code @Component} name is the
 * disambiguation.
 */
@RestControllerAdvice
@Component("academicConfigGlobalExceptionHandler")
public class GlobalExceptionHandler {

	@ExceptionHandler(AcademicDivisionNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(AcademicDivisionNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler({ DuplicateDivisionCodeException.class, DuplicateDivisionNameException.class })
	public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(DirectorNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleDirectorNotFound(DirectorNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}
