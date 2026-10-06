package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

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
	 * Validación de parámetros de ruta/método, no de cuerpo: {@code @Validated}
	 * sobre la clase + {@code @Min}/{@code @Max}/{@code Pattern} en un
	 * {@code @RequestParam} lanza {@code ConstraintViolationException}. Antes
	 * caía en {@link #handleUnexpected} y respondía 500 (D1 de las incidencias
	 * 2026-10-06: {@code GET /groups/next-codes?quantity=999} devolvía "error
	 * interno" en vez de 400).
	 *
	 * <p>El copy es genérico y en español a propósito: el mensaje por defecto de
	 * Hibernate Validator es inglés ({@code "must be greater than or equal to
	 * 1"}), y salirle al usuario con eso es peor que no decir nada. Los detalles
	 * sí van al log, que es donde corresponde verlos.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		String violations = ex.getConstraintViolations().stream()
				.map(violation -> violation.getPropertyPath() + " " + violation.getMessage()).toList().toString();
		log.warn("Constraint violation on {}: {}", request.getRequestURI(), violations);
		return build(HttpStatus.BAD_REQUEST, "La solicitud contiene un dato fuera del rango permitido.", request);
	}

	/**
	 * The request body could not be READ: absent on an endpoint whose
	 * {@code @RequestBody} is required, or malformed/unparseable JSON.
	 * Spring raises this before the controller method runs, so it is a
	 * client-shape problem, not an application failure — without this handler
	 * it fell through to {@link #handleUnexpected} and returned a misleading
	 * 500. It matters now that
	 * {@code POST /candidates/{id}/payments/confirm} REQUIRES a body: a
	 * bodyless POST is a 400 ("falta el identificador del pedido"), not a
	 * server error.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "La solicitud no contiene un cuerpo válido o falta información requerida.",
				request);
	}

	/**
	 * A database constraint rejected a write: NOT NULL, length, FK, CHECK. Spring
	 * defers the INSERT/UPDATE to commit time, so this surfaces from the flush,
	 * long after the controller returned — which means it used to reach
	 * {@link #handleUnexpected} and answer 500 "intenta más tarde" for what is a
	 * client-shape problem ("no pasa la validación de la base"). Same rationale
	 * as {@link #handleUnreadableBody}.
	 *
	 * <p>The message is deliberately generic: the exception text carries table and
	 * column names, and echoing those back leaks the schema to the caller. The
	 * root cause is logged instead.
	 *
	 * <p>Not a substitute for validating the request. {@code RegisterCandidateRequest}
	 * had 16 nested {@code @NotBlank} annotations that never ran because the root
	 * record lacked {@code @Valid}; that is fixed at the DTO. This handler is the
	 * net for whatever a new writer forgets, not the primary mechanism.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		log.warn("Database constraint violated on {}", request.getRequestURI(), ex);
		return build(HttpStatus.BAD_REQUEST,
				"Los datos enviados no cumplen las reglas de información del sistema. Revisa el formulario e inténtalo de nuevo.",
				request);
	}

	/**
	 * Last-resort handler so unexpected failures still return the standard
	 * {@link ErrorResponse} envelope. The internal exception message is
	 * logged but never returned to the caller.
	 *
	 * <p>Antes devolvía 500 <b>siempre</b>, y eso se tragaba el status real de
	 * las excepciones que Spring ya trae con uno propio (D1 + hallazgo de paso
	 * de las incidencias 2026-10-06): una ruta inexistente
	 * ({@code NoResourceFoundException} → 404), un método que la ruta no acepta
	 * (405), un content-type insoportable (415) y cualquier
	 * {@code ResponseStatusException} acababan como "Ocurrió un error al
	 * procesar la solicitud". Ahí se respeta el status que la excepción declara
	 * y sólo queda 500 para lo que de verdad no tiene.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		HttpStatus declared = declaredStatusOf(ex);
		if (declared != null) {
			log.warn("Request failed with declared status {} on {}: {}", declared.value(), request.getRequestURI(),
					ex.toString());
			return build(declared, messageFor(declared), request);
		}
		log.error("Unhandled exception on {}", request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error al procesar la solicitud. Intenta nuevamente más tarde.", request);
	}

	/**
	 * The status the exception itself declares, or {@code null} when it doesn't
	 * declare one (i.e. when it really is an unexpected server failure).
	 *
	 * <p>{@code org.springframework.web.ErrorResponse} cubre las excepciones de
	 * Spring MVC con status propio — entre ellas
	 * {@code NoResourceFoundException} (404 para rutas inexistentes),
	 * {@code NoHandlerFoundException} y {@code ErrorResponseException} — y
	 * {@link ResponseStatusException} es el equivalente del lado de aplicaciones
	 * que usan {@code @ResponseStatus}. Las de Spring que no implementan
	 * ninguna de las dos se declaran aparte. Se usa el nombre calificado porque
	 * {@code ErrorResponse} ya está tomado por el DTO propio del proyecto.
	 */
	private static HttpStatus declaredStatusOf(Exception ex) {
		if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
			return HttpStatus.resolve(errorResponse.getStatusCode().value());
		}
		if (ex instanceof ResponseStatusException responseStatus) {
			return HttpStatus.resolve(responseStatus.getStatusCode().value());
		}
		if (ex instanceof HttpRequestMethodNotSupportedException) {
			return HttpStatus.METHOD_NOT_ALLOWED;
		}
		if (ex instanceof HttpMediaTypeNotSupportedException) {
			return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
		}
		if (ex instanceof HttpMediaTypeNotAcceptableException) {
			return HttpStatus.NOT_ACCEPTABLE;
		}
		return null;
	}

	private static String messageFor(HttpStatus status) {
		return switch (status) {
			case NOT_FOUND -> "La ruta solicitada no existe.";
			case METHOD_NOT_ALLOWED -> "El método HTTP utilizado no está permitido para esta ruta.";
			case UNSUPPORTED_MEDIA_TYPE -> "El tipo de contenido enviado no es compatible con esta ruta.";
			case NOT_ACCEPTABLE -> "El tipo de contenido solicitado no está disponible.";
			default -> "La solicitud no pudo procesarse. Revisa los datos enviados.";
		};
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
