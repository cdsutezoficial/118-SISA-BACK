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
 * Bootstraps a single ADMIN user on startup (design.md — Decision: Bootstrap
 * ADMIN seed). Checks the real invariant — "at least one ADMIN role exists" —
 * via {@link UserRoleRepository#existsByRoleType(RoleType)} rather than table
 * emptiness, so a deleted admin is re-seeded even while other users remain.
 * Never seeds with a blank password.
 */
@Component
public class AdminSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final PersonRepository personRepository;
	private final PasswordHasher passwordHasher;
	private final String adminUsername;
	private final String adminPassword;
	private final String adminCurp;

	public AdminSeedRunner(UserRepository userRepository, UserRoleRepository userRoleRepository,
			PersonRepository personRepository, PasswordHasher passwordHasher,
			@Value("${sisa.security.bootstrap.admin.username}") String adminUsername,
			@Value("${sisa.security.bootstrap.admin.password}") String adminPassword,
			@Value("${sisa.security.bootstrap.admin.curp}") String adminCurp) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
		this.personRepository = personRepository;
		this.passwordHasher = passwordHasher;
		this.adminUsername = adminUsername;
		this.adminPassword = adminPassword;
		this.adminCurp = adminCurp;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userRoleRepository.existsByRoleType(RoleType.ADMIN)) {
			log.info("An ADMIN user already exists; skipping bootstrap seed");
			return;
		}
		if (adminPassword == null || adminPassword.isBlank()) {
			log.warn("sisa.security.bootstrap.admin.password is blank; skipping ADMIN bootstrap seed");
			return;
		}

		Person person = new Person(adminCurp, "Administrador", "Sistema", null, adminUsername);
		Person savedPerson = personRepository.save(person);

		String passwordHash = passwordHasher.hash(adminPassword);
		User user = new User(savedPerson.getId(), adminUsername, passwordHash);
		User savedUser = userRepository.save(user);

		userRoleRepository.save(new UserRole(savedUser.getId(), RoleType.ADMIN, null));

		log.info("Seeded bootstrap ADMIN user: {}", adminUsername);
	}
}
