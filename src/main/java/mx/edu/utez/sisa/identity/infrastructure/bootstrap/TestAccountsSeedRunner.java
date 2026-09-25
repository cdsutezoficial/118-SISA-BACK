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

import java.util.List;
import java.util.UUID;

/**
 * Dev/testing bootstrap that seeds exactly ONE single-role account per role
 * with already-developed functionality — ADMIN, SERVICIOS_ESCOLARES,
 * PERSONAL_FINANZAS and DIRECTOR_DIVISION — all sharing one test password
 * ({@code SISA_TEST_PASSWORD}). Replaces the old {@code AdminSeedRunner} +
 * {@code ServiciosEscolaresSeedRunner} + {@code MultiRoleTestAccountsSeedRunner}
 * trio: no more GESTOR_ACADEMICO breadcrumbs or multi-role test accounts, so
 * logging in with any of these usernames exercises exactly the role being
 * tested. Same idempotent pattern as before: an account is seeded only when
 * its role doesn't exist yet (checked per role via
 * {@link UserRoleRepository#existsByRoleId(UUID)}), and the whole runner
 * skips when the shared password property is blank. Roles are seeded with
 * {@code divisionId = null} — the DIRECTOR_DIVISION UI isn't division-scoped
 * yet, so a null division is sufficient for exercising the role-based
 * frontend.
 */
@Component
@Order(10)
public class TestAccountsSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TestAccountsSeedRunner.class);

	/**
	 * One entry per role with developed functionality. Usernames double as the
	 * person's institutional email (seeds convention); CURPs are well-formed
	 * placeholders of exactly 18 characters.
	 */
	private static final List<TestAccount> ACCOUNTS = List.of(
			new TestAccount(RoleType.ADMIN, "admin@utez.edu.mx", "ADMS000000HDFSTX01", "Administrador", "Sistema"),
			new TestAccount(RoleType.SERVICIOS_ESCOLARES, "servicios.escolares@utez.edu.mx",
					"SERV000000MDFESC02", "Servicios", "Escolares"),
			new TestAccount(RoleType.PERSONAL_FINANZAS, "finanzas@utez.edu.mx", "FINP000000MDFFIN03", "Personal",
					"Finanzas"),
			new TestAccount(RoleType.DIRECTOR_DIVISION, "director@utez.edu.mx", "DIRD000000HDFDIV04", "Director",
					"División"));

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final PersonRepository personRepository;
	private final PasswordHasher passwordHasher;
	private final RoleRepository roleRepository;
	private final String testPassword;

	public TestAccountsSeedRunner(UserRepository userRepository, UserRoleRepository userRoleRepository,
			PersonRepository personRepository, PasswordHasher passwordHasher, RoleRepository roleRepository,
			@Value("${sisa.security.bootstrap.test.password}") String testPassword) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
		this.personRepository = personRepository;
		this.passwordHasher = passwordHasher;
		this.roleRepository = roleRepository;
		this.testPassword = testPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (testPassword == null || testPassword.isBlank()) {
			log.info("sisa.security.bootstrap.test.password is blank; skipping test-accounts seed");
			return;
		}

		for (TestAccount account : ACCOUNTS) {
			seed(account);
		}
	}

	private void seed(TestAccount account) {
		UUID roleId = resolveRoleId(account.roleType());
		if (userRoleRepository.existsByRoleId(roleId)) {
			log.info("A {} user already exists; skipping test-account seed for {}", account.roleType().name(),
					account.username());
			return;
		}

		Person person = personRepository
				.save(new Person(account.curp(), account.firstName(), account.lastName(), null, account.username()));
		User user = userRepository.save(new User(person.getId(), account.username(), passwordHasher.hash(testPassword)));
		userRoleRepository.save(new UserRole(user.getId(), roleId, null));

		log.info("Seeded test account {} with role {}", account.username(), account.roleType().name());
	}

	private UUID resolveRoleId(RoleType roleType) {
		return roleRepository.findByKey(roleType.name()).map(mx.edu.utez.sisa.identity.domain.model.Role::getId)
				.orElseThrow(() -> new IllegalStateException("Missing seeded role: " + roleType.name()));
	}

	record TestAccount(RoleType roleType, String username, String curp, String firstName, String lastName) {
	}
}