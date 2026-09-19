package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dev/testing convenience seed (NOT a system invariant like
 * {@link AdminSeedRunner} — multi-role accounts exist so the frontend's
 * post-login role selection + shell role switcher can be exercised before
 * user provisioning is wired up). Same conventions as
 * {@link ServiciosEscolaresSeedRunnerTest}: seeds three multi-role accounts
 * when they don't exist, skips existing ones, never seeds with a blank
 * password.
 */
class MultiRoleTestAccountsSeedRunnerTest {

	private static final String PASSWORD = "TestAcc!123";

	private final UserRepository userRepository = mock(UserRepository.class);
	private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
	private final PersonRepository personRepository = mock(PersonRepository.class);
	private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
	private final RoleRepository roleRepository = mock(RoleRepository.class);

	private MultiRoleTestAccountsSeedRunner newRunner() {
		return new MultiRoleTestAccountsSeedRunner(userRepository, userRoleRepository, personRepository,
				passwordHasher, roleRepository, PASSWORD);
	}

	private void stubNoneExist() {
		when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
		stubRoles();
		when(personRepository.save(any(Person.class))).thenAnswer(invocation -> {
			Person person = invocation.getArgument(0);
			ReflectionTestUtils.setField(person, "id", UUID.randomUUID());
			return person;
		});
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
			return user;
		});
	}

	@Test
	void seedsThreeMultiRoleAccountsWhenNoneExist() {
		stubNoneExist();
		when(passwordHasher.hash(PASSWORD)).thenReturn("hashed-secret");

		newRunner().run(mock(ApplicationArguments.class));

		verify(personRepository, times(3)).save(any(Person.class));
		verify(userRepository, times(3)).save(any(User.class));
		// 2 roles per account — three accounts with exactly two roles each
		verify(userRoleRepository, times(6)).save(any(UserRole.class));
	}

	@Test
	void skipsAccountsThatAlreadyExist() {
		when(userRepository.findByUsername("gestor.se@utez.edu.mx")).thenReturn(Optional.empty());
		when(userRepository.findByUsername("finanzas.se@utez.edu.mx"))
				.thenReturn(Optional.of(mock(User.class)));
		when(userRepository.findByUsername("director.gestor@utez.edu.mx")).thenReturn(Optional.empty());
		stubRoles();
		when(personRepository.save(any(Person.class))).thenAnswer(invocation -> {
			Person person = invocation.getArgument(0);
			ReflectionTestUtils.setField(person, "id", UUID.randomUUID());
			return person;
		});
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
			return user;
		});
		when(passwordHasher.hash(PASSWORD)).thenReturn("hashed-secret");

		newRunner().run(mock(ApplicationArguments.class));

		// "finanzas.se" already exists → skipped; the other two are seeded (2 roles each)
		verify(personRepository, times(2)).save(any(Person.class));
		verify(userRepository, times(2)).save(any(User.class));
		verify(userRoleRepository, times(4)).save(any(UserRole.class));
	}

	@Test
	void skipsEntirelyWhenPasswordIsBlank() {
		MultiRoleTestAccountsSeedRunner runner = new MultiRoleTestAccountsSeedRunner(userRepository, userRoleRepository,
				personRepository, passwordHasher, roleRepository, "");

		runner.run(mock(ApplicationArguments.class));

		verify(userRepository, never()).findByUsername(any());
		verify(personRepository, never()).save(any());
		verify(userRepository, never()).save(any());
		verify(userRoleRepository, never()).save(any());
	}

	private void stubRoles() {
		for (RoleType roleType : RoleType.values()) {
			when(roleRepository.findByKey(roleType.name())).thenReturn(Optional.of(role(roleType)));
		}
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