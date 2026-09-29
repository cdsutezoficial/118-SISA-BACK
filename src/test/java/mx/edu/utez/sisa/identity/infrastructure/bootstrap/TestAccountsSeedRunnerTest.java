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
 * Dev/testing seed (NOT a system invariant): seeds exactly one single-role
 * account per developed role when it doesn't exist, skips existing ones,
 * never seeds with a blank password.
 */
class TestAccountsSeedRunnerTest {

	private static final String PASSWORD = "TestAcc!123";

	private final UserRepository userRepository = mock(UserRepository.class);
	private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
	private final PersonRepository personRepository = mock(PersonRepository.class);
	private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
	private final RoleRepository roleRepository = mock(RoleRepository.class);

	private TestAccountsSeedRunner newRunner() {
		return new TestAccountsSeedRunner(userRepository, userRoleRepository, personRepository, passwordHasher,
				roleRepository, PASSWORD);
	}

	private void stubPersistedEntities() {
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
	void seedsFourSingleRoleAccountsWhenNoneExist() {
		stubRoles();
		when(userRoleRepository.existsByRoleId(any())).thenReturn(false);
		stubPersistedEntities();
		when(passwordHasher.hash(PASSWORD)).thenReturn("hashed-secret");

		newRunner().run(mock(ApplicationArguments.class));

		// Four accounts, exactly one role grant each — no multi-role leftovers.
		verify(personRepository, times(4)).save(any(Person.class));
		verify(userRepository, times(4)).save(any(User.class));
		verify(userRoleRepository, times(4)).save(any(UserRole.class));
	}

	@Test
	void skipsRolesThatAlreadyExist() {
		stubRoles();
		when(userRoleRepository.existsByRoleId(any())).thenAnswer(invocation -> {
			UUID roleId = invocation.getArgument(0);
			return roleId.equals(roleId(RoleType.PERSONAL_FINANZAS));
		});
		stubPersistedEntities();
		when(passwordHasher.hash(PASSWORD)).thenReturn("hashed-secret");

		newRunner().run(mock(ApplicationArguments.class));

		// PERSONAL_FINANZAS already exists → skipped; the other three are seeded.
		verify(personRepository, times(3)).save(any(Person.class));
		verify(userRepository, times(3)).save(any(User.class));
		verify(userRoleRepository, times(3)).save(any(UserRole.class));
	}

	@Test
	void skipsEntirelyWhenPasswordIsBlank() {
		TestAccountsSeedRunner runner = new TestAccountsSeedRunner(userRepository, userRoleRepository,
				personRepository, passwordHasher, roleRepository, "");

		runner.run(mock(ApplicationArguments.class));

		verify(userRoleRepository, never()).existsByRoleId(any());
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