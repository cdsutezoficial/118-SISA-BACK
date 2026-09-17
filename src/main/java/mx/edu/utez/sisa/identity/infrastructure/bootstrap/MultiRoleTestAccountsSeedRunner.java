package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

/**
 * Dev/testing convenience seed for MULTI-ROLE accounts — NOT a system
 * invariant like {@link AdminSeedRunner}. Exists so multi-role auth can be
 * exercised end-to-end (post-login role selection + shell role switcher +
 * role-scoped sidebar) once users are provisionable; until then the seed is
 * the only way to get accounts carrying 2+ roles the frontend maps.
 *
 * <p>Same idempotent pattern as {@link ServiciosEscolaresSeedRunner}: each
 * account is created only if its username doesn't exist yet (checked per
 * account, not per role, so partially seeded state self-heals), and the whole
 * runner skips when the shared password property is blank. Test accounts use
 * one shared dev password — this is explicitly a prototype convenience, never
 * a prod bootstrap.
 *
 * <p>Roles are seeded with {@code divisionId = null}: division-scoped roles
 * ({@code GESTOR_ACADEMICO}, {@code DIRECTOR_DIVISION}) still map and gate
 * navigation in the frontend regardless of scope, so a null division is
 * sufficient for exercising role-based UI behavior without a division catalog.
 */
@Component
@Order(12)
public class MultiRoleTestAccountsSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(MultiRoleTestAccountsSeedRunner.class);

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final PersonRepository personRepository;
	private final PasswordHasher passwordHasher;
	private final RoleRepository roleRepository;
	private final String testAccountsPassword;

	public MultiRoleTestAccountsSeedRunner(UserRepository userRepository, UserRoleRepository userRoleRepository,
			PersonRepository personRepository, PasswordHasher passwordHasher, RoleRepository roleRepository,
			@Value("${sisa.security.bootstrap.test-accounts.password}") String testAccountsPassword) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
		this.personRepository = personRepository;
		this.passwordHasher = passwordHasher;
		this.roleRepository = roleRepository;
		this.testAccountsPassword = testAccountsPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (testAccountsPassword == null || testAccountsPassword.isBlank()) {
			log.info("sisa.security.bootstrap.test-accounts.password is blank; skipping multi-role test-accounts seed");
			return;
		}

		seed("gestor.se@utez.edu.mx", "GI2026SE1234567890", "Gestor Académico", "Multirol · Servicios Escolares",
				RoleType.GESTOR_ACADEMICO, RoleType.SERVICIOS_ESCOLARES);
		seed("finanzas.se@utez.edu.mx", "FI2026SE0987654321", "Personal de Finanzas", "Multirol · Servicios Escolares",
				RoleType.PERSONAL_FINANZAS, RoleType.SERVICIOS_ESCOLARES);
		seed("director.gestor@utez.edu.mx", "DG2026GA1122334455", "Director de División", "Multirol · Gestor Académico",
				RoleType.DIRECTOR_DIVISION, RoleType.GESTOR_ACADEMICO);
	}

	private void seed(String username, String curp, String firstName, String lastName, RoleType... roles) {
		if (userRepository.findByUsername(username).isPresent()) {
			log.info("Multi-role test account {} already exists; skipping", username);
			return;
		}

		Person person = personRepository.save(new Person(curp, firstName, lastName, null, username));
		User user = userRepository.save(new User(person.getId(), username, passwordHasher.hash(testAccountsPassword)));
		for (RoleType role : roles) {
			userRoleRepository.save(new UserRole(user.getId(), resolveRoleId(role), null));
		}

		log.info("Seeded multi-role test account {} with roles {}", username, Arrays.toString(roles));
	}

	private UUID resolveRoleId(RoleType roleType) {
		return roleRepository.findByKey(roleType.name()).map(mx.edu.utez.sisa.identity.domain.model.Role::getId)
				.orElseThrow(() -> new IllegalStateException("Missing seeded role: " + roleType.name()));
	}
}