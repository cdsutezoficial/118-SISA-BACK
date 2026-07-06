package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleCommand;
import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase.AssignRoleResult;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.DivisionRuleViolationException;
import mx.edu.utez.sisa.shared.model.RoleType;
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
class AssignRoleUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserRoleRepository userRoleRepository;

	private AssignRoleUseCaseImpl useCase;

	private User adminCaller;
	private User targetUser;
	private UUID callerId;
	private UUID targetUserId;

	@BeforeEach
	void setUp() {
		useCase = new AssignRoleUseCaseImpl(userRepository, userRoleRepository);
		adminCaller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed-admin-pw");
		adminCaller.changePassword("hashed-admin-pw-2"); // clears mustChangePassword so the caller can operate
		targetUser = new User(UUID.randomUUID(), "target@utez.edu.mx", "hashed-target-pw");
		// User#id is JPA-generated and null pre-persistence; assign deterministic ids
		// so distinct mocked User instances don't collide on findById(null).
		callerId = UUID.randomUUID();
		targetUserId = UUID.randomUUID();
		ReflectionTestUtils.setField(adminCaller, "id", callerId);
		ReflectionTestUtils.setField(targetUser, "id", targetUserId);
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
	}

	@Test
	void assignRole_divisionScopedRoleRequiresDivisionId() {
		UUID divisionId = UUID.randomUUID();
		when(userRoleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AssignRoleResult result = useCase.assignRole(
				new AssignRoleCommand(callerId, targetUserId, RoleType.DIRECTOR_DIVISION, divisionId));

		assertThat(result.roleType()).isEqualTo(RoleType.DIRECTOR_DIVISION);
		assertThat(result.divisionId()).isEqualTo(divisionId);
	}

	@Test
	void assignRole_divisionScopedRoleRejectedWithoutDivisionId() {
		assertThatThrownBy(() -> useCase
				.assignRole(new AssignRoleCommand(callerId, targetUserId, RoleType.GESTOR_ACADEMICO, null)))
				.isInstanceOf(DivisionRuleViolationException.class);
	}

	@Test
	void assignRole_nonDivisionRoleRejectedWithADivisionId() {
		assertThatThrownBy(() -> useCase
				.assignRole(new AssignRoleCommand(callerId, targetUserId, RoleType.ADMIN, UUID.randomUUID())))
				.isInstanceOf(DivisionRuleViolationException.class);
	}

	@Test
	void assignRole_userAccumulatesMultipleScopedRoles() {
		UUID divisionA = UUID.randomUUID();
		UUID divisionB = UUID.randomUUID();
		when(userRoleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AssignRoleResult first = useCase.assignRole(
				new AssignRoleCommand(callerId, targetUserId, RoleType.COORDINACION_ESTADIAS_DIVISION, divisionA));
		AssignRoleResult second = useCase
				.assignRole(new AssignRoleCommand(callerId, targetUserId, RoleType.GESTOR_ACADEMICO, divisionB));

		assertThat(first.divisionId()).isEqualTo(divisionA);
		assertThat(second.divisionId()).isEqualTo(divisionB);
	}
}
