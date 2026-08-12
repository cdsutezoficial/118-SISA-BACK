package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.in.RevokeRoleUseCase.RevokeRoleCommand;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.identity.shared.exception.UserRoleNotFoundException;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeRoleUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserRoleRepository userRoleRepository;

	private RevokeRoleUseCaseImpl useCase;

	private User adminCaller;
	private UUID callerId;

	@BeforeEach
	void setUp() {
		useCase = new RevokeRoleUseCaseImpl(userRepository, userRoleRepository);
		adminCaller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed-admin-pw");
		adminCaller.changePassword("hashed-admin-pw-2");
		callerId = UUID.randomUUID();
		ReflectionTestUtils.setField(adminCaller, "id", callerId);
	}

	@Test
	void revokeRole_callerNotFoundThrowsUserNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.revokeRole(new RevokeRoleCommand(callerId, UUID.randomUUID(), UUID.randomUUID())))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void revokeRole_callerWithPendingPasswordChangeIsRejected() {
		User pendingCaller = new User(UUID.randomUUID(), "pending@utez.edu.mx", "hash");
		UUID pendingCallerId = UUID.randomUUID();
		ReflectionTestUtils.setField(pendingCaller, "id", pendingCallerId);
		when(userRepository.findById(pendingCallerId)).thenReturn(Optional.of(pendingCaller));

		assertThatThrownBy(() -> useCase
				.revokeRole(new RevokeRoleCommand(pendingCallerId, UUID.randomUUID(), UUID.randomUUID())))
				.isInstanceOf(MustChangePasswordException.class);
	}

	@Test
	void revokeRole_unknownUserRoleIdThrowsUserRoleNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		UUID userRoleId = UUID.randomUUID();
		when(userRoleRepository.findById(userRoleId)).thenReturn(Optional.empty());

		assertThatThrownBy(
				() -> useCase.revokeRole(new RevokeRoleCommand(callerId, UUID.randomUUID(), userRoleId)))
				.isInstanceOf(UserRoleNotFoundException.class);
	}

	@Test
	void revokeRole_userRoleBelongingToDifferentUserThrowsUserRoleNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		UUID actualOwnerId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID userRoleId = UUID.randomUUID();
		UserRole userRole = new UserRole(actualOwnerId, RoleType.DOCENTE, null);
		ReflectionTestUtils.setField(userRole, "id", userRoleId);
		when(userRoleRepository.findById(userRoleId)).thenReturn(Optional.of(userRole));

		assertThatThrownBy(() -> useCase.revokeRole(new RevokeRoleCommand(callerId, otherUserId, userRoleId)))
				.isInstanceOf(UserRoleNotFoundException.class);

		verify(userRoleRepository, org.mockito.Mockito.never()).delete(userRole);
	}

	@Test
	void revokeRole_matchingUserRoleIsDeleted() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		UUID userId = UUID.randomUUID();
		UUID userRoleId = UUID.randomUUID();
		UserRole userRole = new UserRole(userId, RoleType.DOCENTE, null);
		ReflectionTestUtils.setField(userRole, "id", userRoleId);
		when(userRoleRepository.findById(userRoleId)).thenReturn(Optional.of(userRole));

		assertThatCode(() -> useCase.revokeRole(new RevokeRoleCommand(callerId, userId, userRoleId)))
				.doesNotThrowAnyException();

		verify(userRoleRepository).delete(userRole);
	}
}
