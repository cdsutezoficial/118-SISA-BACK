package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
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
 * skips when the shared password property is blank.
 *
 * <p>Accounts are seeded with {@code personId = null}: an app user does not
 * need a linked {@code Person} (account credentials live in
 * {@code app_user}; personal data lives in {@code person}), so no fake
 * person rows are fabricated just to satisfy the FK. The seeded usernames
 * double as the notification address (seeds convention), so credential
 * emails still reach them.
 *
 * <p>Roles are seeded with {@code divisionId = null} because this module
 * cannot see which divisions exist — {@code academic_config} owns that
 * catalog. The one grant that needs a scope, {@code DIRECTOR_DIVISION}, is
 * backfilled right after this runner by
 * {@code academic_config.TestDirectorScopeSeedRunner} ({@code @Order(15)});
 * until then the null scope simply hides the seeded Director from the
 * division-filtered lists, which is why that follow-up exists rather than
 * being optional.
 */
@Component
@Order(10)
public class TestAccountsSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TestAccountsSeedRunner.class);

	/**
	 * One entry per role with developed functionality. The username doubles
	 * as the account's notification address (seeds convention).
	 */
	private static final List<TestAccount> ACCOUNTS = List.of(
			new TestAccount(RoleType.ADMIN, "admin@utez.edu.mx"),
			new TestAccount(RoleType.SERVICIOS_ESCOLARES, "servicios.escolares@utez.edu.mx"),
			new TestAccount(RoleType.PERSONAL_FINANZAS, "finanzas@utez.edu.mx"),
			new TestAccount(RoleType.DIRECTOR_DIVISION, "director@utez.edu.mx"));

	private final UserRepository userRepository;
	private final UserRoleRepository userRoleRepository;
	private final PasswordHasher passwordHasher;
	private final RoleRepository roleRepository;
	private final String testPassword;

	public TestAccountsSeedRunner(UserRepository userRepository, UserRoleRepository userRoleRepository,
			PasswordHasher passwordHasher, RoleRepository roleRepository,
			@Value("${sisa.security.bootstrap.test.password}") String testPassword) {
		this.userRepository = userRepository;
		this.userRoleRepository = userRoleRepository;
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

		User user = userRepository.save(new User(null, account.username(), passwordHasher.hash(testPassword)));
		userRoleRepository.save(new UserRole(user.getId(), roleId, null));

		log.info("Seeded test account {} with role {}", account.username(), account.roleType().name());
	}

	private UUID resolveRoleId(RoleType roleType) {
		return roleRepository.findByKey(roleType.name()).map(mx.edu.utez.sisa.identity.domain.model.Role::getId)
				.orElseThrow(() -> new IllegalStateException("Missing seeded role: " + roleType.name()));
	}

	record TestAccount(RoleType roleType, String username) {
	}
}
