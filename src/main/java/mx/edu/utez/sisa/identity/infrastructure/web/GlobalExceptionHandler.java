package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.ErrorResponse;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.DivisionRuleViolationException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidCredentialsException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidRefreshTokenException;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Maps the 8 identity domain exceptions (plus bean validation failures) to
 * HTTP status codes per design.md's "Exception -> HTTP mapping" table (task
 * 5.2), plus two consistency handlers added on review: malformed
 * {@code @PathVariable} values (e.g. non-UUID {@code userId}) and a
 * catch-all for unexpected exceptions, so every error response uses the
 * same {@link ErrorResponse} envelope instead of Spring's default whitelabel
 * page.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
			HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
	}

	@ExceptionHandler(AccountLockedException.class)
	public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
		return build(HttpStatus.LOCKED, ex.getMessage(), request);
	}

	@ExceptionHandler(MustChangePasswordException.class)
	public ResponseEntity<ErrorResponse> handleMustChangePassword(MustChangePasswordException ex,
			HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
			HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
	}

	@ExceptionHandler(DivisionRuleViolationException.class)
	public ResponseEntity<ErrorResponse> handleDivisionRuleViolation(DivisionRuleViolationException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	/**
	 * {@code PersonAlreadyHasUserException} and
	 * {@code MissingInstitutionalEmailException} share the 409 mapping per
	 * design.md's table.
	 */
	@ExceptionHandler({ mx.edu.utez.sisa.identity.shared.exception.PersonAlreadyHasUserException.class,
			mx.edu.utez.sisa.identity.shared.exception.MissingInstitutionalEmailException.class })
	public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message, request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		String message = "%s: invalid value '%s'".formatted(ex.getName(), ex.getValue());
		return build(HttpStatus.BAD_REQUEST, message, request);
	}

	/**
	 * Last-resort handler so unexpected failures still return the standard
	 * {@link ErrorResponse} envelope. The internal exception message is
	 * logged but never returned to the caller.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception on {}", request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}
