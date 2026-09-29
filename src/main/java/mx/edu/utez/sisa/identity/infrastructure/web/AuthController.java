package mx.edu.utez.sisa.identity.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase.AuthenticateCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase.AuthenticationResult;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePasswordUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePasswordUseCase.ChangePasswordCommand;
import mx.edu.utez.sisa.identity.domain.port.in.GetCurrentProfileUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.GetCurrentProfileUseCase.CurrentProfileQuery;
import mx.edu.utez.sisa.identity.domain.port.in.GetCurrentProfileUseCase.CurrentProfileResult;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase.RefreshCommand;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase.RefreshResult;
import mx.edu.utez.sisa.identity.domain.port.in.RequestPasswordResetUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.RequestPasswordResetUseCase.RequestPasswordResetCommand;
import mx.edu.utez.sisa.identity.domain.port.in.ResetPasswordUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ResetPasswordUseCase.ResetPasswordCommand;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.ChangePasswordRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.ForgotPasswordRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.LoginRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.LoginResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.MeProfileResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.RefreshRequest;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.RefreshResponse;
import mx.edu.utez.sisa.identity.infrastructure.web.dto.ResetPasswordRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Thin controller for {@code POST /auth/login}, {@code /auth/refresh} and
 * {@code /auth/change-password} (design.md — REST endpoints, task 5.5). All
 * business rules — credential validation, refresh-token/LOCKED re-check,
 * mustChangePassword gate — live in the use cases; this class only maps
 * HTTP <-> use case commands/results.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthenticateUseCase authenticateUseCase;
	private final RefreshAccessTokenUseCase refreshAccessTokenUseCase;
	private final ChangePasswordUseCase changePasswordUseCase;
	private final RequestPasswordResetUseCase requestPasswordResetUseCase;
	private final ResetPasswordUseCase resetPasswordUseCase;
	private final GetCurrentProfileUseCase getCurrentProfileUseCase;
	private final long accessTokenTtlSeconds;

	public AuthController(AuthenticateUseCase authenticateUseCase,
			RefreshAccessTokenUseCase refreshAccessTokenUseCase, ChangePasswordUseCase changePasswordUseCase,
			RequestPasswordResetUseCase requestPasswordResetUseCase, ResetPasswordUseCase resetPasswordUseCase,
			GetCurrentProfileUseCase getCurrentProfileUseCase,
			@Value("${sisa.security.jwt.access-token-ttl}") Duration accessTokenTtl) {
		this.authenticateUseCase = authenticateUseCase;
		this.refreshAccessTokenUseCase = refreshAccessTokenUseCase;
		this.changePasswordUseCase = changePasswordUseCase;
		this.requestPasswordResetUseCase = requestPasswordResetUseCase;
		this.resetPasswordUseCase = resetPasswordUseCase;
		this.getCurrentProfileUseCase = getCurrentProfileUseCase;
		this.accessTokenTtlSeconds = accessTokenTtl.getSeconds();
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		AuthenticationResult result = authenticateUseCase
				.authenticate(new AuthenticateCommand(request.username(), request.password()));
		return ResponseEntity.ok(new LoginResponse(result.accessToken(), result.refreshToken(), "Bearer",
				accessTokenTtlSeconds, result.mustChangePassword()));
	}

	@PostMapping("/refresh")
	public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		RefreshResult result = refreshAccessTokenUseCase.refresh(new RefreshCommand(request.refreshToken()));
		return ResponseEntity.ok(new RefreshResponse(result.accessToken(), "Bearer", accessTokenTtlSeconds));
	}

	/**
	 * Self-service profile: the CALLER's own fullName/email from its linked
	 * {@code Person} (the {@code sub} claim picks the user — never a target id
	 * from the request, so nobody can read someone else's profile). Any
	 * authenticated role may call it; the route is not registered in the
	 * fine-grained layer, so it falls back to {@code .anyRequest().
	 * authenticated()} in the coarse matcher (same as
	 * {@code /auth/me/capabilities}).
	 */
	@GetMapping("/me")
	public ResponseEntity<MeProfileResponse> me() {
		CurrentProfileResult result = getCurrentProfileUseCase
				.getCurrentProfile(new CurrentProfileQuery(AuthenticatedCaller.currentUserId()));
		return ResponseEntity.ok(new MeProfileResponse(result.fullName(), result.username(), result.email()));
	}

	@PostMapping("/change-password")
	public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		UUID callerId = AuthenticatedCaller.currentUserId();
		changePasswordUseCase
				.changePassword(new ChangePasswordCommand(callerId, request.currentPassword(), request.newPassword()));
		return ResponseEntity.noContent().build();
	}

	/**
	 * Public "forgot my password" entry (01-identidad.md). Always answers 204 —
	 * even for an unknown username — so the endpoint never reveals whether an
	 * account exists; the email is simply not sent in that case.
	 */
	@PostMapping("/forgot-password")
	public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		requestPasswordResetUseCase.request(new RequestPasswordResetCommand(request.username()));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		resetPasswordUseCase.reset(new ResetPasswordCommand(request.token(), request.newPassword()));
		return ResponseEntity.noContent().build();
	}
}
