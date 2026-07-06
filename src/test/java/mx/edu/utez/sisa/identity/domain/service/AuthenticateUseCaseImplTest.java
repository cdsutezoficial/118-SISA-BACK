package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase.AuthenticateCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase.AuthenticationResult;
import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.AccountLockedException;
import mx.edu.utez.sisa.identity.shared.exception.InvalidCredentialsException;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserRoleRepository userRoleRepository;
	@Mock
	private PasswordHasher passwordHasher;
	@Mock
	private AccessTokenIssuer accessTokenIssuer;
	@Mock
	private RefreshTokenGenerator refreshTokenGenerator;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private AuthenticateUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new AuthenticateUseCaseImpl(userRepository, userRoleRepository, passwordHasher, accessTokenIssuer,
				refreshTokenGenerator, refreshTokenRepository, Duration.ofDays(1));
	}

	@Test
	void authenticate_validCredentialsIssueBothTokens() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-pw");
		when(userRepository.findByUsername("jane.doe@utez.edu.mx")).thenReturn(Optional.of(user));
		when(passwordHasher.matches("correct-password", "hashed-pw")).thenReturn(true);
		when(userRoleRepository.findByUserId(any())).thenReturn(List.of(new UserRole(user.getId(), RoleType.ADMIN, null)));
		when(accessTokenIssuer.issue(any(), anySet())).thenReturn("access-token-value");
		when(refreshTokenGenerator.generate()).thenReturn("refresh-token-value");

		AuthenticationResult result = useCase
				.authenticate(new AuthenticateCommand("jane.doe@utez.edu.mx", "correct-password"));

		assertThat(result.accessToken()).isEqualTo("access-token-value");
		assertThat(result.refreshToken()).isEqualTo("refresh-token-value");
		verify(refreshTokenRepository).save(any());
	}

	@Test
	void authenticate_wrongPasswordIncrementsFailedAttempts() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-pw");
		when(userRepository.findByUsername("jane.doe@utez.edu.mx")).thenReturn(Optional.of(user));
		when(passwordHasher.matches("wrong-password", "hashed-pw")).thenReturn(false);

		assertThatThrownBy(() -> useCase.authenticate(new AuthenticateCommand("jane.doe@utez.edu.mx", "wrong-password")))
				.isInstanceOf(InvalidCredentialsException.class);

		assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
		assertThat(user.getStatus()).isEqualTo(mx.edu.utez.sisa.identity.domain.model.UserStatus.ACTIVE);
		verify(accessTokenIssuer, never()).issue(any(), anySet());
	}

	@Test
	void authenticate_thirdConsecutiveFailureLocksTheAccount() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-pw");
		user.registerFailedLogin();
		user.registerFailedLogin();
		when(userRepository.findByUsername("jane.doe@utez.edu.mx")).thenReturn(Optional.of(user));
		when(passwordHasher.matches("wrong-password", "hashed-pw")).thenReturn(false);

		assertThatThrownBy(() -> useCase.authenticate(new AuthenticateCommand("jane.doe@utez.edu.mx", "wrong-password")))
				.isInstanceOf(InvalidCredentialsException.class);

		assertThat(user.getStatus()).isEqualTo(mx.edu.utez.sisa.identity.domain.model.UserStatus.LOCKED);
	}

	@Test
	void authenticate_lockedAccountRejectsEvenCorrectCredentials() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-pw");
		user.registerFailedLogin();
		user.registerFailedLogin();
		user.registerFailedLogin();
		when(userRepository.findByUsername("jane.doe@utez.edu.mx")).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> useCase.authenticate(new AuthenticateCommand("jane.doe@utez.edu.mx", "correct-password")))
				.isInstanceOf(AccountLockedException.class);

		verify(passwordHasher, never()).matches(any(), any());
		verify(accessTokenIssuer, never()).issue(any(), anySet());
	}

	@Test
	void authenticate_firstAccessLoginStillIssuesTokens() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed-pw");
		when(userRepository.findByUsername("jane.doe@utez.edu.mx")).thenReturn(Optional.of(user));
		when(passwordHasher.matches("correct-password", "hashed-pw")).thenReturn(true);
		when(userRoleRepository.findByUserId(any())).thenReturn(List.of());
		when(accessTokenIssuer.issue(any(), anySet())).thenReturn("access-token-value");
		when(refreshTokenGenerator.generate()).thenReturn("refresh-token-value");

		AuthenticationResult result = useCase
				.authenticate(new AuthenticateCommand("jane.doe@utez.edu.mx", "correct-password"));

		assertThat(result.mustChangePassword()).isTrue();
		assertThat(result.accessToken()).isEqualTo("access-token-value");
	}
}
