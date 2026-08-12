package mx.edu.utez.sisa.admission.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Maps the {@code admission} bounded context's domain exceptions
 * ({@link OutreachChannelNotFoundException}, {@link HighSchoolTypeNotFoundException})
 * to HTTP 404 (same "own {@code @RestControllerAdvice}, additive only"
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

	@ExceptionHandler(OutreachChannelNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleOutreachChannelNotFound(OutreachChannelNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(HighSchoolTypeNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleHighSchoolTypeNotFound(HighSchoolTypeNotFoundException ex,
			HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}
