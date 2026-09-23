package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.DivisionRuleViolationException;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateCurpException;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateInstitutionalEmailException;
import mx.edu.utez.sisa.identity.shared.exception.DuplicatePermissionKeyException;
import mx.edu.utez.sisa.identity.shared.exception.DuplicateRoleKeyException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidCredentialsException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidPasswordResetTokenException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidRefreshTokenException;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.MissingInstitutionalEmailException;
import mx.edu.utez.sisa.identity.shared.exception.PermissionNotFoundException;
import mx.edu.utez.sisa.identity.shared.exception.PersonAlreadyHasUserException;
import mx.edu.utez.sisa.identity.shared.exception.RoleNotFoundException;
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
		return build(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos.", request);
	}

	@ExceptionHandler(AccountLockedException.class)
	public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
		return build(HttpStatus.LOCKED, "Tu cuenta está bloqueada. Solicita apoyo al administrador.", request);
	}

	@ExceptionHandler(MustChangePasswordException.class)
	public ResponseEntity<ErrorResponse> handleMustChangePassword(MustChangePasswordException ex,
			HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, "Debes cambiar tu contraseña antes de continuar.", request);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
			HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, "Tu sesión no es válida o ha expirado. Inicia sesión nuevamente.", request);
	}

	/**
	 * Password-reset flow (01-identidad.md — ResetPasswordUseCase): a token that
	 * is unknown, already used, or expired maps to 400. The message varies by
	 * case but this handler keeps the client-facing copy generic; the specific
	 * failure lives in the exception message already chosen by the use case.
	 */
	@ExceptionHandler(InvalidPasswordResetTokenException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPasswordResetToken(InvalidPasswordResetTokenException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "El enlace de restablecimiento no es válido o ha expirado.", request);
	}

	@ExceptionHandler(DivisionRuleViolationException.class)
	public ResponseEntity<ErrorResponse> handleDivisionRuleViolation(DivisionRuleViolationException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Revisa la división académica seleccionada para este rol.", request);
	}

	/**
	 * {@code PersonAlreadyHasUserException} and
	 * {@code MissingInstitutionalEmailException} share the 409 mapping per
	 * design.md's table; {@code DuplicateCurpException}/
	 * {@code DuplicateInstitutionalEmailException} (plan:
	 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.1)
	 * join the same group, same 409 conflict semantics.
	 */
	@ExceptionHandler({ PersonAlreadyHasUserException.class, MissingInstitutionalEmailException.class,
			DuplicateCurpException.class, DuplicateInstitutionalEmailException.class,
			DuplicateRoleKeyException.class, DuplicatePermissionKeyException.class })
	public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "Ya existe un registro con la información proporcionada.", request);
	}

	/**
	 * {@code UserRoleNotFoundException} (plan:
	 * {@code docs/plans/2026-07-28-persons-and-user-management.md} — 4.4)
	 * shares the 404 mapping with {@code UserNotFoundException} — both mean
	 * "the referenced id does not resolve to what the caller expected".
	 */
	@ExceptionHandler({ UserNotFoundException.class, UserRoleNotFoundException.class, RoleNotFoundException.class,
			PermissionNotFoundException.class })
	public ResponseEntity<ErrorResponse> handleUserNotFound(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "No se encontró el registro solicitado.", request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream().map(error -> error.getDefaultMessage())
				.filter(text -> text != null && !text.isBlank()).findFirst()
				.orElse("Revisa los datos proporcionados.");
		return build(HttpStatus.BAD_REQUEST, message, request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "La solicitud contiene un dato con formato inválido.", request);
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
		return build(HttpStatus.BAD_REQUEST, "Falta información requerida para procesar la solicitud.", request);
	}

	/**
	 * Last-resort handler so unexpected failures still return the standard
	 * {@link ErrorResponse} envelope. The internal exception message is
	 * logged but never returned to the caller.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception on {}", request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error al procesar la solicitud. Intenta nuevamente más tarde.", request);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), statusLabel(status), message,
				request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}

	private static String statusLabel(HttpStatus status) {
		return switch (status) {
			case BAD_REQUEST -> "Solicitud inválida";
			case UNAUTHORIZED -> "No autorizado";
			case FORBIDDEN -> "Acceso denegado";
			case NOT_FOUND -> "No encontrado";
			case CONFLICT -> "Conflicto";
			case LOCKED -> "Cuenta bloqueada";
			case INTERNAL_SERVER_ERROR -> "Error interno";
			default -> "Error";
		};
	}
}
