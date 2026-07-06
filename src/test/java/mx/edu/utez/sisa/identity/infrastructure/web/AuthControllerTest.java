package mx.edu.utez.sisa.identity.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase.AuthenticateCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase.AuthenticationResult;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePasswordUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase.RefreshCommand;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase.RefreshResult;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidCredentialsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for task 5.5: {@code AuthController} delegates to the
 * use cases and maps their results/exceptions to the documented HTTP
 * contracts, with no inline token/lock logic.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuthenticateUseCase authenticateUseCase;

	@MockitoBean
	private RefreshAccessTokenUseCase refreshAccessTokenUseCase;

	@MockitoBean
	private ChangePasswordUseCase changePasswordUseCase;

	/**
	 * {@code JwtAuthenticationFilter} is auto-detected as a Filter bean by the
	 * {@code @WebMvcTest} slice (it's a {@code @Component}); it needs a
	 * {@code JwtService} bean to construct even though {@code addFilters =
	 * false} keeps it out of the actual MockMvc request pipeline.
	 */
	@MockitoBean
	private JwtService jwtService;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void loginReturns200WithTokensAndMustChangePassword() throws Exception {
		when(authenticateUseCase.authenticate(new AuthenticateCommand("admin@utez.edu.mx", "temp-pass")))
				.thenReturn(new AuthenticationResult("access-token", "refresh-token", true));

		mockMvc.perform(post("/auth/login").contentType("application/json")
				.content(objectMapper.writeValueAsString(new LoginBody("admin@utez.edu.mx", "temp-pass"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token"))
				.andExpect(jsonPath("$.refreshToken").value("refresh-token"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	@Test
	void loginWithInvalidCredentialsReturns401() throws Exception {
		when(authenticateUseCase.authenticate(any()))
				.thenThrow(new InvalidCredentialsException("Invalid username or password"));

		mockMvc.perform(post("/auth/login").contentType("application/json")
				.content(objectMapper.writeValueAsString(new LoginBody("admin@utez.edu.mx", "wrong"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginWithLockedAccountReturns423() throws Exception {
		when(authenticateUseCase.authenticate(any())).thenThrow(new AccountLockedException("locked"));

		mockMvc.perform(post("/auth/login").contentType("application/json")
				.content(objectMapper.writeValueAsString(new LoginBody("admin@utez.edu.mx", "correct"))))
				.andExpect(status().isLocked());
	}

	@Test
	void loginWithBlankUsernameReturns400() throws Exception {
		mockMvc.perform(post("/auth/login").contentType("application/json")
				.content(objectMapper.writeValueAsString(new LoginBody("", "pw")))).andExpect(status().isBadRequest());
	}

	@Test
	void refreshDelegatesToRefreshAccessTokenUseCase() throws Exception {
		when(refreshAccessTokenUseCase.refresh(new RefreshCommand("refresh-token")))
				.thenReturn(new RefreshResult("new-access-token"));

		mockMvc.perform(post("/auth/refresh").contentType("application/json")
				.content(objectMapper.writeValueAsString(new RefreshBody("refresh-token"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("new-access-token"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"));
	}

	@Test
	void changePasswordUsesCallerIdFromSecurityContextAndReturns204() throws Exception {
		UUID callerId = UUID.randomUUID();
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				callerId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

		mockMvc.perform(post("/auth/change-password").contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangePasswordBody("old-pass", "new-pass"))))
				.andExpect(status().isNoContent());

		verify(changePasswordUseCase).changePassword(eq(new ChangePasswordUseCase.ChangePasswordCommand(callerId,
				"old-pass", "new-pass")));
	}

	private record LoginBody(String username, String password) {
	}

	private record RefreshBody(String refreshToken) {
	}

	private record ChangePasswordBody(String currentPassword, String newPassword) {
	}
}
