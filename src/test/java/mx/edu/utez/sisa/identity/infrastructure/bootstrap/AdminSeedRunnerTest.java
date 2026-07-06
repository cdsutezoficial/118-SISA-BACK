package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers design.md — Decision: Bootstrap ADMIN seed: seeds exactly one ADMIN
 * when the real invariant ("no ADMIN role exists") holds, and skips
 * idempotently otherwise (spec: 2 scenarios).
 */
class AdminSeedRunnerTest {

	private static final String ADMIN_USERNAME = "admin@utez.edu.mx";
	private static final String ADMIN_PASSWORD = "Secret123!";
	private static final String ADMIN_CURP = "XXXX000000HDFXXX00";

	private final UserRepository userRepository = mock(UserRepository.class);
	private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
	private final PersonRepository personRepository = mock(PersonRepository.class);
	private final PasswordHasher passwordHasher = mock(PasswordHasher.class);

	@Test
	void seedsAdminWhenNoneExists() {
		when(userRoleRepository.existsByRoleType(RoleType.ADMIN)).thenReturn(false);

		Person seededPerson = new Person(ADMIN_CURP, "Administrador", "Sistema", null, ADMIN_USERNAME);
		ReflectionTestUtils.setField(seededPerson, "id", UUID.randomUUID());
		when(personRepository.save(any(Person.class))).thenReturn(seededPerson);

		when(passwordHasher.hash(ADMIN_PASSWORD)).thenReturn("hashed-secret");

		User seededUser = new User(seededPerson.getId(), ADMIN_USERNAME, "hashed-secret");
		ReflectionTestUtils.setField(seededUser, "id", UUID.randomUUID());
		when(userRepository.save(any(User.class))).thenReturn(seededUser);

		AdminSeedRunner runner = new AdminSeedRunner(userRepository, userRoleRepository, personRepository,
				passwordHasher, ADMIN_USERNAME, ADMIN_PASSWORD, ADMIN_CURP);

		runner.run(mock(ApplicationArguments.class));

		verify(personRepository, times(1)).save(any(Person.class));
		verify(userRepository, times(1)).save(any(User.class));
		verify(userRoleRepository, times(1)).save(any());
	}

	@Test
	void skipsWhenAdminAlreadyExists() {
		when(userRoleRepository.existsByRoleType(RoleType.ADMIN)).thenReturn(true);

		AdminSeedRunner runner = new AdminSeedRunner(userRepository, userRoleRepository, personRepository,
				passwordHasher, ADMIN_USERNAME, ADMIN_PASSWORD, ADMIN_CURP);

		runner.run(mock(ApplicationArguments.class));

		verify(personRepository, never()).save(any());
		verify(userRepository, never()).save(any());
		verify(userRoleRepository, never()).save(any());
	}
}
