package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.model.UserStatus;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.ListUsersQuery;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.ListUsersResult;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase.UserSummary;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository.UserSearchCriteria;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository.UserSearchPage;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository.UserWithPerson;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;
import mx.edu.utez.sisa.identity.shared.exception.UserNotFoundException;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserRoleRepository userRoleRepository;

	private ListUsersUseCaseImpl useCase;

	private User adminCaller;
	private UUID callerId;

	@BeforeEach
	void setUp() {
		useCase = new ListUsersUseCaseImpl(userRepository, userRoleRepository);
		adminCaller = new User(UUID.randomUUID(), "admin@utez.edu.mx", "hashed-admin-pw");
		adminCaller.changePassword("hashed-admin-pw-2"); // clears mustChangePassword so the caller can operate
		callerId = UUID.randomUUID();
		ReflectionTestUtils.setField(adminCaller, "id", callerId);
	}

	@Test
	void listUsers_callerNotFoundThrowsUserNotFound() {
		when(userRepository.findById(callerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.listUsers(new ListUsersQuery(callerId, null, null, null, 0, 20)))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void listUsers_callerWithPendingPasswordChangeIsRejected() {
		User pendingCaller = new User(UUID.randomUUID(), "pending@utez.edu.mx", "hash");
		UUID pendingCallerId = UUID.randomUUID();
		ReflectionTestUtils.setField(pendingCaller, "id", pendingCallerId);
		when(userRepository.findById(pendingCallerId)).thenReturn(Optional.of(pendingCaller));

		assertThatThrownBy(
				() -> useCase.listUsers(new ListUsersQuery(pendingCallerId, null, null, null, 0, 20)))
						.isInstanceOf(MustChangePasswordException.class);
	}

	@Test
	void listUsers_mapsUsersWithPersonNameAndGroupedRoles() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));

		UUID targetUserId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		User target = new User(personId, "target@utez.edu.mx", "hash");
		ReflectionTestUtils.setField(target, "id", targetUserId);
		Person person = new Person("CURP123456789012", "Ana", "García", "López", "target@utez.edu.mx");
		ReflectionTestUtils.setField(person, "id", personId);

		when(userRepository.search(any())).thenReturn(
				new UserSearchPage(List.of(new UserWithPerson(target, person)), 1L, 1));

		UUID divisionId = UUID.randomUUID();
		when(userRoleRepository.findByUserIdIn(List.of(targetUserId))).thenReturn(List.of(
				new UserRole(targetUserId, RoleType.DOCENTE, null),
				new UserRole(targetUserId, RoleType.GESTOR_ACADEMICO, divisionId)));

		ListUsersResult result = useCase.listUsers(new ListUsersQuery(callerId, null, null, null, 0, 20));

		assertThat(result.users()).hasSize(1);
		UserSummary summary = result.users().get(0);
		assertThat(summary.userId()).isEqualTo(targetUserId);
		assertThat(summary.personId()).isEqualTo(personId);
		assertThat(summary.fullName()).isEqualTo("Ana García López");
		assertThat(summary.username()).isEqualTo("target@utez.edu.mx");
		assertThat(summary.status()).isEqualTo(UserStatus.ACTIVE);
		assertThat(summary.roles()).hasSize(2);
		assertThat(summary.roles()).extracting("roleType").containsExactlyInAnyOrder(RoleType.DOCENTE,
				RoleType.GESTOR_ACADEMICO);
		assertThat(result.totalElements()).isEqualTo(1L);
		assertThat(result.totalPages()).isEqualTo(1);
	}

	@Test
	void listUsers_omitsLastLastNameWhenAbsent() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));

		UUID targetUserId = UUID.randomUUID();
		UUID personId = UUID.randomUUID();
		User target = new User(personId, "target2@utez.edu.mx", "hash");
		ReflectionTestUtils.setField(target, "id", targetUserId);
		Person person = new Person("CURP223456789012", "Juan", "Pérez", null, "target2@utez.edu.mx");
		ReflectionTestUtils.setField(person, "id", personId);

		when(userRepository.search(any()))
				.thenReturn(new UserSearchPage(List.of(new UserWithPerson(target, person)), 1L, 1));
		when(userRoleRepository.findByUserIdIn(List.of(targetUserId))).thenReturn(List.of());

		ListUsersResult result = useCase.listUsers(new ListUsersQuery(callerId, null, null, null, 0, 20));

		assertThat(result.users().get(0).fullName()).isEqualTo("Juan Pérez");
		assertThat(result.users().get(0).roles()).isEmpty();
	}

	@Test
	void listUsers_passesFiltersThroughToRepository() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(userRepository.search(any())).thenReturn(new UserSearchPage(List.of(), 0L, 0));
		when(userRoleRepository.findByUserIdIn(List.of())).thenReturn(List.of());

		useCase.listUsers(new ListUsersQuery(callerId, RoleType.DIRECTOR_DIVISION, UserStatus.LOCKED, "ana", 2, 15));

		ArgumentCaptor<UserSearchCriteria> captor = ArgumentCaptor.forClass(UserSearchCriteria.class);
		verify(userRepository).search(captor.capture());
		UserSearchCriteria criteria = captor.getValue();
		assertThat(criteria.roleType()).isEqualTo(RoleType.DIRECTOR_DIVISION);
		assertThat(criteria.status()).isEqualTo(UserStatus.LOCKED);
		assertThat(criteria.search()).isEqualTo("ana");
		assertThat(criteria.page()).isEqualTo(2);
		assertThat(criteria.size()).isEqualTo(15);
	}

	@Test
	void listUsers_normalizesNegativePageToZero() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(userRepository.search(any())).thenReturn(new UserSearchPage(List.of(), 0L, 0));

		useCase.listUsers(new ListUsersQuery(callerId, null, null, null, -5, 20));

		ArgumentCaptor<UserSearchCriteria> captor = ArgumentCaptor.forClass(UserSearchCriteria.class);
		verify(userRepository).search(captor.capture());
		assertThat(captor.getValue().page()).isEqualTo(0);
	}

	@Test
	void listUsers_normalizesNonPositiveSizeToDefault() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(userRepository.search(any())).thenReturn(new UserSearchPage(List.of(), 0L, 0));

		useCase.listUsers(new ListUsersQuery(callerId, null, null, null, 0, 0));

		ArgumentCaptor<UserSearchCriteria> captor = ArgumentCaptor.forClass(UserSearchCriteria.class);
		verify(userRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListUsersQuery.DEFAULT_PAGE_SIZE);
	}

	@Test
	void listUsers_capsOversizedSizeAtMaximum() {
		when(userRepository.findById(callerId)).thenReturn(Optional.of(adminCaller));
		when(userRepository.search(any())).thenReturn(new UserSearchPage(List.of(), 0L, 0));

		useCase.listUsers(new ListUsersQuery(callerId, null, null, null, 0, 5000));

		ArgumentCaptor<UserSearchCriteria> captor = ArgumentCaptor.forClass(UserSearchCriteria.class);
		verify(userRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListUsersQuery.MAX_PAGE_SIZE);
	}
}
