package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Dev/testing convenience seed for a SERVICIOS_ESCOLARES user — NOT a system
 * invariant like {@link AdminSeedRunner} (the system doesn't require this
 * role to exist to function). Exists because there's no user-provisioning UI
 * yet ({@code CreateUserUseCase} has no frontend caller), so this is
 * currently the only way to get a real, non-ADMIN account to develop and
 * exercise role-scoped screens against (starting with the Programación /
 * Configuración Académica catalogs — Divisiones, Programas, etc.). Same
 * idempotent pattern as {@link AdminSeedRunner}: checks the real invariant
 * via {@link UserRoleRepository#existsByRoleType(RoleType)}, never seeds
 * with a blank password.
 */
@Component
public class ServiciosEscolaresSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ServiciosEscolaresSeedRunner.class);

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final PersonRepository personRepository;
	private final PasswordHasher passwordHasher;
	private final String username;
	private final String password;
	private final String curp;

	public ServiciosEscolaresSeedRunner(UserRepository userRepository, UserRoleRepository userRoleRepository,
			PersonRepository personRepository, PasswordHasher passwordHasher,
			@Value("${sisa.security.bootstrap.servicios-escolares.username}") String username,
			@Value("${sisa.security.bootstrap.servicios-escolares.password}") String password,
			@Value("${sisa.security.bootstrap.servicios-escolares.curp}") String curp) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
		this.personRepository = personRepository;
		this.passwordHasher = passwordHasher;
		this.username = username;
		this.password = password;
		this.curp = curp;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userRoleRepository.existsByRoleType(RoleType.SERVICIOS_ESCOLARES)) {
			log.info("A SERVICIOS_ESCOLARES user already exists; skipping bootstrap seed");
			return;
		}
		if (password == null || password.isBlank()) {
			log.warn("sisa.security.bootstrap.servicios-escolares.password is blank; skipping bootstrap seed");
			return;
		}

		Person person = new Person(curp, "Servicios", "Escolares", null, username);
		Person savedPerson = personRepository.save(person);

		String passwordHash = passwordHasher.hash(password);
		User user = new User(savedPerson.getId(), username, passwordHash);
		User savedUser = userRepository.save(user);

		userRoleRepository.save(new UserRole(savedUser.getId(), RoleType.SERVICIOS_ESCOLARES, null));

		log.info("Seeded bootstrap SERVICIOS_ESCOLARES user: {}", username);
	}
}
