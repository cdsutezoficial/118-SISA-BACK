package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.DivisionRuleViolationException;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateCurpException;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateInstitutionalEmailException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidCredentialsException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidRefreshTokenException;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.identity.shared.exception.UserRoleNotFoundException;
import mx.edu.utez.sisa.shared.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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
 * page. Extended by plan
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} with 3 more
 * mappings: {@code DuplicateCurpException}/{@code DuplicateInstitutionalEmailException}
 * (409, grouped with the existing conflict handler) and
 * {@code UserRoleNotFoundException} (404, grouped with
 * {@code UserNotFoundException} — both are "the referenced id does not
 * resolve to what the caller expected").
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
	 * design.md's table; {@code DuplicateCurpException}/
	 * {@code DuplicateInstitutionalEmailException} (plan:
	 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1)
	 * join the same group, same 409 conflict semantics.
	 */
	@ExceptionHandler({ mx.edu.utez.sisa.identity.shared.exception.PersonAlreadyHasUserException.class,
			mx.edu.utez.sisa.identity.shared.exception.MissingInstitutionalEmailException.class,
			DuplicateCurpException.class, DuplicateInstitutionalEmailException.class })
	public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	/**
	 * {@code UserRoleNotFoundException} (plan:
	 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.4)
	 * shares the 404 mapping with {@code UserNotFoundException} — both mean
	 * "the referenced id does not resolve to what the caller expected".
	 */
	@ExceptionHandler({ UserNotFoundException.class, UserRoleNotFoundException.class })
	public ResponseEntity<ErrorResponse> handleUserNotFound(RuntimeException ex, HttpServletRequest request) {
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
	 * A required {@code @RequestParam} with no default value was omitted
	 * (first real case: {@code GET /municipalities}'s required {@code stateId}
	 * — plan: {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md}).
	 * Without this handler the request falls through to
	 * {@link #handleUnexpected}, returning a misleading 500 for what is really
	 * a 400-level client mistake — same "malformed request, not an app
	 * exception" rationale as {@link #handleTypeMismatch}.
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
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
