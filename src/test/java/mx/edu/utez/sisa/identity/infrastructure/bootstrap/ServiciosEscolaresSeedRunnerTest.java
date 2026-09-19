package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.Role;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dev/testing convenience seed (NOT a system invariant like
 * {@link AdminSeedRunner} — SERVICIOS_ESCOLARES is one of several roles the
 * real ADMIN would normally provision via {@code CreateUserUseCase}, but no
 * such provisioning UI exists yet). Same idempotent pattern: seeds exactly
 * one SERVICIOS_ESCOLARES user when none exists, skips otherwise, never
 * seeds with a blank password.
 */
class ServiciosEscolaresSeedRunnerTest {

	private static final String USERNAME = "servicios.escolares@utez.edu.mx";
	private static final String PASSWORD = "Secret123!";
	private static final String CURP = "XXXX000000MDFXXX00";

	private final UserRepository userRepository = mock(UserRepository.class);
	private final UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
	private final PersonRepository personRepository = mock(PersonRepository.class);
	private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
	private final RoleRepository roleRepository = mock(RoleRepository.class);

	@Test
	void seedsServiciosEscolaresWhenNoneExists() {
		when(roleRepository.findByKey(RoleType.SERVICIOS_ESCOLARES.name()))
				.thenReturn(java.util.Optional.of(role(RoleType.SERVICIOS_ESCOLARES)));
		when(userRoleRepository.existsByRoleId(roleId(RoleType.SERVICIOS_ESCOLARES))).thenReturn(false);

		Person seededPerson = new Person(CURP, "Servicios", "Escolares", null, USERNAME);
		ReflectionTestUtils.setField(seededPerson, "id", UUID.randomUUID());
		when(personRepository.save(any(Person.class))).thenReturn(seededPerson);

		when(passwordHasher.hash(PASSWORD)).thenReturn("hashed-secret");

		User seededUser = new User(seededPerson.getId(), USERNAME, "hashed-secret");
		ReflectionTestUtils.setField(seededUser, "id", UUID.randomUUID());
		when(userRepository.save(any(User.class))).thenReturn(seededUser);

		ServiciosEscolaresSeedRunner runner = new ServiciosEscolaresSeedRunner(userRepository, userRoleRepository,
				personRepository, passwordHasher, roleRepository, USERNAME, PASSWORD, CURP);

		runner.run(mock(ApplicationArguments.class));

		verify(personRepository, times(1)).save(any(Person.class));
		verify(userRepository, times(1)).save(any(User.class));
		verify(userRoleRepository, times(1)).save(any());
	}

	@Test
	void skipsWhenServiciosEscolaresAlreadyExists() {
		when(roleRepository.findByKey(RoleType.SERVICIOS_ESCOLARES.name()))
				.thenReturn(java.util.Optional.of(role(RoleType.SERVICIOS_ESCOLARES)));
		when(userRoleRepository.existsByRoleId(roleId(RoleType.SERVICIOS_ESCOLARES))).thenReturn(true);

		ServiciosEscolaresSeedRunner runner = new ServiciosEscolaresSeedRunner(userRepository, userRoleRepository,
				personRepository, passwordHasher, roleRepository, USERNAME, PASSWORD, CURP);

		runner.run(mock(ApplicationArguments.class));

		verify(personRepository, never()).save(any());
		verify(userRepository, never()).save(any());
		verify(userRoleRepository, never()).save(any());
	}

	@Test
	void skipsWhenPasswordIsBlank() {
		when(roleRepository.findByKey(RoleType.SERVICIOS_ESCOLARES.name()))
				.thenReturn(java.util.Optional.of(role(RoleType.SERVICIOS_ESCOLARES)));
		when(userRoleRepository.existsByRoleId(roleId(RoleType.SERVICIOS_ESCOLARES))).thenReturn(false);

		ServiciosEscolaresSeedRunner runner = new ServiciosEscolaresSeedRunner(userRepository, userRoleRepository,
				personRepository, passwordHasher, roleRepository, USERNAME, "", CURP);

		runner.run(mock(ApplicationArguments.class));

		verify(personRepository, never()).save(any());
		verify(userRepository, never()).save(any());
		verify(userRoleRepository, never()).save(any());
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
