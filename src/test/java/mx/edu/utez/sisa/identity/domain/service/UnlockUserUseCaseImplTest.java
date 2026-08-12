package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.in.UnlockUserUseCase.UnlockUserCommand;
import mx.edu.utez.sisa.identity.domain.port.in.UnlockUserUseCase.UnlockUserResult;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnlockUserUseCaseImplTest {

	@Mock
	private UserRepository userRepository;

	private UnlockUserUseCaseImpl useCase;

	private User adminCaller;
	private UUID callerId;

	@BeforeEach
	void setUp() {
		useCase = new UnlockUserUseCaseImpl(userRepository);
		adminCaller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed-admin-pw");
		adminCaller.changePassword("hashed-admin-pw-2");
		callerId = UUID.randomUUID();
		ReflectionTestUtils.setField(adminCaller, "id", callerId);
	}

	@Test
	void unlockUser_callerNotFoundThrowsUserNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.unlockUser(new UnlockUserCommand(callerId, UUID.randomUUID())))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void unlockUser_callerWithPendingPasswordChangeIsRejected() {
		User pendingCaller = new User(UUID.randomUUID(), "pending@utez.edu.mx", "hash");
		UUID pendingCallerId = UUID.randomUUID();
		ReflectionTestUtils.setField(pendingCaller, "id", pendingCallerId);
		when(userRepository.findById(pendingCallerId)).thenReturn(Optional.of(pendingCaller));

		assertThatThrownBy(() -> useCase.unlockUser(new UnlockUserCommand(pendingCallerId, UUID.randomUUID())))
				.isInstanceOf(MustChangePasswordException.class);
	}

	@Test
	void unlockUser_targetNotFoundThrowsUserNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		UUID targetId = UUID.randomUUID();
		when(userRepository.findById(targetId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.unlockUser(new UnlockUserCommand(callerId, targetId)))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void unlockUser_reactivatesLockedAccount() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		UUID targetId = UUID.randomUUID();
		User target = new User(UUID.randomUUID(), "locked@utez.edu.mx", "hash");
		ReflectionTestUtils.setField(target, "id", targetId);
		target.registerFailedLogin();
		target.registerFailedLogin();
		target.registerFailedLogin();
		when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
		when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		UnlockUserResult result = useCase.unlockUser(new UnlockUserCommand(callerId, targetId));

		assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
		assertThat(result.failedLoginAttempts()).isZero();
	}
}
