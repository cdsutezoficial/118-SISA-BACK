package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.ErrorResponse;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.DivisionRuleViolationException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidCredentialsException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidRefreshTokenException;
import mx.edu.utez.sisa.identity.shared.exception.MissingInstitutionalEmailException;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.PersonAlreadyHasUserException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies the exception -> HTTP status mapping table from design.md (task
 * 5.2). Exercised directly (no MockMvc) since the handler methods are plain
 * exception-in, {@code ResponseEntity}-out functions.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

	@Mock
	private HttpServletRequest request;

	private GlobalExceptionHandler handler;

	@BeforeEach
	void setUp() {
		handler = new GlobalExceptionHandler();
		when(request.getRequestURI()).thenReturn("/auth/login");
	}

	@Test
	void invalidCredentialsMapsTo401() {
		ResponseEntity<ErrorResponse> response = handler
				.handleInvalidCredentials(new InvalidCredentialsException("bad creds"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo("bad creds");
		assertThat(response.getBody().path()).isEqualTo("/auth/login");
	}

	@Test
	void accountLockedMapsTo423() {
		ResponseEntity<ErrorResponse> response = handler.handleAccountLocked(new AccountLockedException("locked"),
				request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
	}

	@Test
	void mustChangePasswordMapsTo403() {
		ResponseEntity<ErrorResponse> response = handler
				.handleMustChangePassword(new MustChangePasswordException("must change"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void invalidRefreshTokenMapsTo401() {
		ResponseEntity<ErrorResponse> response = handler
				.handleInvalidRefreshToken(new InvalidRefreshTokenException("invalid refresh"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void divisionRuleViolationMapsTo400() {
		ResponseEntity<ErrorResponse> response = handler
				.handleDivisionRuleViolation(new DivisionRuleViolationException("division rule"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void personAlreadyHasUserMapsTo409() {
		ResponseEntity<ErrorResponse> response = handler
				.handleConflict(new PersonAlreadyHasUserException("already has user"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void missingInstitutionalEmailMapsTo409() {
		ResponseEntity<ErrorResponse> response = handler
				.handleConflict(new MissingInstitutionalEmailException("no email"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void userNotFoundMapsTo404() {
		ResponseEntity<ErrorResponse> response = handler.handleUserNotFound(new UserNotFoundException("not found"),
				request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void methodArgumentNotValidMapsTo400() {
		MethodArgumentNotValidException ex = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
		BindingResult bindingResult = org.mockito.Mockito.mock(BindingResult.class);
		FieldError fieldError = new FieldError("loginRequest", "username", "must not be blank");
		when(ex.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

		ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).contains("username");
	}
}
