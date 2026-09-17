package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.in.GetUserUseCase.GetUserQuery;
import mx.edu.utez.sisa.identity.domain.port.in.GetUserUseCase.UserDetailResult;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PersonRepository personRepository;
	@Mock
	private UserRoleRepository userRoleRepository;
	@Mock
	private RoleRepository roleRepository;

	private GetUserUseCaseImpl useCase;

	private User adminCaller;
	private UUID callerId;

	@BeforeEach
	void setUp() {
		useCase = new GetUserUseCaseImpl(userRepository, personRepository, userRoleRepository, roleRepository);
		adminCaller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed-admin-pw");
		adminCaller.changePassword("hashed-admin-pw-2");
		callerId = UUID.randomUUID();
		ReflectionTestUtils.setField(adminCaller, "id", callerId);
	}

	@Test
	void getUser_callerNotFoundThrowsUserNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getUser(new GetUserQuery(callerId, UUID.randomUUID())))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void getUser_callerWithPendingPasswordChangeIsRejected() {
		User pendingCaller = new User(UUID.randomUUID(), "pending@utez.edu.mx", "hash");
		UUID pendingCallerId = UUID.randomUUID();
		ReflectionTestUtils.setField(pendingCaller, "id", pendingCallerId);
		when(userRepository.findById(pendingCallerId)).thenReturn(Optional.of(pendingCaller));

		assertThatThrownBy(() -> useCase.getUser(new GetUserQuery(pendingCallerId, UUID.randomUUID())))
				.isInstanceOf(MustChangePasswordException.class);
	}

	@Test
	void getUser_targetNotFoundThrowsUserNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		UUID targetId = UUID.randomUUID();
		when(userRepository.findById(targetId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getUser(new GetUserQuery(callerId, targetId)))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void getUser_returnsFullDetailWithUserRoleIdsExposed() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));

		UUID targetId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		User target = new User(personId, "target@utez.edu.mx", "hash");
		ReflectionTestUtils.setField(target, "id", targetId);
		when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

		Person person = new Person("CURP123456789012", "Ana", "García", "López", "target@utez.edu.mx");
		ReflectionTestUtils.setField(person, "id", personId);
		when(personRepository.findById(personId)).thenReturn(Optional.of(person));

		UUID divisionId = UUID.randomUUID();
		UserRole role = new UserRole(targetId, roleId(RoleType.DIRECTOR_DIVISION), divisionId);
		UUID userRoleId = UUID.randomUUID();
		ReflectionTestUtils.setField(role, "id", userRoleId);
		when(userRoleRepository.findByUserId(targetId)).thenReturn(List.of(role));
		when(roleRepository.findByIds(List.of(roleId(RoleType.DIRECTOR_DIVISION)))).thenReturn(
				List.of(role(RoleType.DIRECTOR_DIVISION)));

		UserDetailResult result = useCase.getUser(new GetUserQuery(callerId, targetId));

		assertThat(result.userId()).isEqualTo(targetId);
		assertThat(result.personId()).isEqualTo(personId);
		assertThat(result.fullName()).isEqualTo("Ana García López");
		assertThat(result.username()).isEqualTo("target@utez.edu.mx");
		assertThat(result.roles()).hasSize(1);
		assertThat(result.roles().get(0).userRoleId()).isEqualTo(userRoleId);
		assertThat(result.roles().get(0).roleKey()).isEqualTo(RoleType.DIRECTOR_DIVISION.name());
		assertThat(result.roles().get(0).divisionId()).isEqualTo(divisionId);
	}

	@Test
	void getUser_missingPersonFallsBackToEmptyFullName() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));

		UUID targetId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		User target = new User(personId, "target2@utez.edu.mx", "hash");
		ReflectionTestUtils.setField(target, "id", targetId);
		when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
		when(personRepository.findById(personId)).thenReturn(Optional.empty());
		when(userRoleRepository.findByUserId(targetId)).thenReturn(List.of());
		when(roleRepository.findByIds(List.of())).thenReturn(List.of());

		UserDetailResult result = useCase.getUser(new GetUserQuery(callerId, targetId));

		assertThat(result.fullName()).isEmpty();
		assertThat(result.roles()).isEmpty();
	}

	private static Role role(RoleType roleType) {
		Role role = new Role(roleType.name(), roleType.name(), roleType.name());
		ReflectionTestUtils.setField(role, "id", roleId(roleType));
		return role;
	}

	private static UUID roleId(RoleType roleType) {
		return UUID.nameUUIDFromBytes(("role-" + roleType.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
