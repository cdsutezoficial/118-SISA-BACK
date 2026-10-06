package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.in.AdminResetPasswordUseCase.AdminResetPasswordCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AdminResetPasswordUseCase.AdminResetPasswordResult;
import mx.edu.utez.sisa.identity.domain.port.out.NotificationPort;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.TemporaryPasswordGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminResetPasswordUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordHasher passwordHasher;
	@Mock
	private TemporaryPasswordGenerator temporaryPasswordGenerator;
	@Mock
	private NotificationPort notificationPort;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	private AdminResetPasswordUseCaseImpl useCase;

	private UUID callerId;
	private UUID targetId;

	@BeforeEach
	void setUp() {
		useCase = new AdminResetPasswordUseCaseImpl(userRepository, passwordHasher, temporaryPasswordGenerator,
				notificationPort, refreshTokenRepository);
		callerId = UUID.randomUUID();
		targetId = UUID.randomUUID();
		// JPA save returns the managed instance; the default mock answer (null)
		// would make the use case read ids off nothing.
		lenient().when(userRepository.save(ArgumentMatchers.any(User.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void reset_appliesTheGeneratedPasswordAsAHashAndNeverStoresThePlaintext() {
		givenAnAdminCaller();
		User target = givenATargetUser();
		when(temporaryPasswordGenerator.generate()).thenReturn("Pl4in!txt");
		when(passwordHasher.hash("Pl4in!txt")).thenReturn("bcrypt-hash");

		AdminResetPasswordResult result = useCase.reset(new AdminResetPasswordCommand(callerId, targetId));

		assertThat(target.getPasswordHash()).isEqualTo("bcrypt-hash");
		assertThat(target.getPasswordHash()).isNotEqualTo("Pl4in!txt");
		verify(userRepository).save(target);
		assertThat(result.temporaryPassword()).isEqualTo("Pl4in!txt");
	}

	@Test
	void reset_reArmsThePasswordChangeGateAndUnlocksTheTarget() {
		givenAnAdminCaller();
		User target = givenATargetUser();
		target.registerFailedLogin();
		target.registerFailedLogin();
		target.registerFailedLogin();
		when(temporaryPasswordGenerator.generate()).thenReturn("Pl4in!txt");
		when(passwordHasher.hash(anyString())).thenReturn("bcrypt-hash");

		useCase.reset(new AdminResetPasswordCommand(callerId, targetId));

		assertThat(target.isMustChangePassword()).isTrue();
		assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(target.getFailedLoginAttempts()).isZero();
	}

	@Test
	void reset_revokesTheTargetSessionsBeforeNotifying() {
		givenAnAdminCaller();
		User target = givenATargetUser();
		when(temporaryPasswordGenerator.generate()).thenReturn("Pl4in!txt");
		when(passwordHasher.hash(anyString())).thenReturn("bcrypt-hash");

		Instant before = Instant.now();
		useCase.reset(new AdminResetPasswordCommand(callerId, targetId));
		Instant after = Instant.now();

		ArgumentCaptor<Instant> revokedAtCaptor = ArgumentCaptor.forClass(Instant.class);
		verify(refreshTokenRepository).revokeAllForUser(eq(targetId),
				revokedAtCaptor.capture());
		assertThat(revokedAtCaptor.getValue()).isBetween(before, after);

		InOrder order = inOrder(refreshTokenRepository, notificationPort);
		order.verify(refreshTokenRepository).revokeAllForUser(eq(targetId),
				any(Instant.class));
		order.verify(notificationPort).sendTemporaryPassword(target.getUsername(), "Pl4in!txt");
	}

	@Test
	void reset_emailsThePlaintextToTheInstitutionalAddress() {
		givenAnAdminCaller();
		User target = givenATargetUser();
		when(temporaryPasswordGenerator.generate()).thenReturn("Pl4in!txt");
		when(passwordHasher.hash(anyString())).thenReturn("bcrypt-hash");

		useCase.reset(new AdminResetPasswordCommand(callerId, targetId));

		verify(notificationPort).sendTemporaryPassword("jane.doe@utez.edu.mx", "Pl4in!txt");
	}

	@Test
	void reset_succeedsEvenWhenTheEmailBounces() {
		givenAnAdminCaller();
		User target = givenATargetUser();
		when(temporaryPasswordGenerator.generate()).thenReturn("Pl4in!txt");
		when(passwordHasher.hash(anyString())).thenReturn("bcrypt-hash");
		doThrow(new IllegalStateException("smtp unavailable")).when(notificationPort)
				.sendTemporaryPassword(anyString(), anyString());

		// The ADMIN still holds the plaintext in the response, so a mail failure
		// must not undo the reset they just performed.
		AdminResetPasswordResult result = useCase.reset(new AdminResetPasswordCommand(callerId, targetId));

		assertThat(result.temporaryPassword()).isEqualTo("Pl4in!txt");
		verify(refreshTokenRepository).revokeAllForUser(eq(targetId),
				any(Instant.class));
	}

	@Test
	void reset_blocksACallerWhoseOwnPasswordChangeIsStillPending() {
		User caller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed");
		ReflectionTestUtils.setField(caller, "id", callerId);
		when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));

		assertThatThrownBy(() -> useCase.reset(new AdminResetPasswordCommand(callerId, targetId)))
				.isInstanceOf(MustChangePasswordException.class);

		verify(temporaryPasswordGenerator, never()).generate();
		verify(refreshTokenRepository, never()).revokeAllForUser(any(),
				any(Instant.class));
	}

	@Test
	void reset_failsWhenTheTargetDoesNotExist() {
		User caller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed");
		caller.changePassword("hashed");
		ReflectionTestUtils.setField(caller, "id", callerId);
		when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));
		when(userRepository.findById(targetId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.reset(new AdminResetPasswordCommand(callerId, targetId)))
				.isInstanceOf(UserNotFoundException.class);

		verify(temporaryPasswordGenerator, never()).generate();
	}

	private void givenAnAdminCaller() {
		User caller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed");
		caller.changePassword("hashed");
		ReflectionTestUtils.setField(caller, "id", callerId);
		when(userRepository.findById(callerId)).thenReturn(Optional.of(caller));
	}

	private User givenATargetUser() {
		User target = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "old-hashed-pw");
		target.changePassword("old-hashed-pw");
		ReflectionTestUtils.setField(target, "id", targetId);
		when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
		return target;
	}
}