package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Spring-context coverage for task 6.6 (design.md — Decision: Bootstrap
 * ADMIN seed). {@link AdminSeedRunnerTest} (PR4) already covers the runner's
 * branching logic with mocked ports; this class instead boots the actual
 * {@code ApplicationContext} with real JPA repositories + BCrypt to verify
 * the wiring itself: exactly one ADMIN gets created on boot, and re-running
 * the seed logic against already-seeded state — the situation a real restart
 * against a persistent production database would produce — does not create a
 * duplicate.
 *
 * <p>Note: this repo's H2 schema uses {@code ddl-auto=create-drop}, which
 * drops and recreates all tables on every {@code ApplicationContext}
 * start/stop. A literal second {@code @SpringBootTest} context in the same
 * JVM would therefore always see an empty, freshly-recreated schema and
 * would reseed regardless of prior state — it cannot exercise "restart
 * skips reseed" the way a real deployment (persistent DB, data survives
 * process restarts) would. Instead of introducing an artificial
 * {@code @DirtiesContext} restart that can't actually preserve data under
 * {@code create-drop}, this test invokes the already-wired
 * {@link AdminSeedRunner} bean's {@code run()} a second time directly,
 * which is exactly what a real restart would do against surviving data —
 * and asserts it stays idempotent.
 */
@SpringBootTest(properties = { "sisa.security.bootstrap.admin.username=admin.seed.it@utez.edu.mx",
		"sisa.security.bootstrap.admin.password=SeedItPass!1", "sisa.security.bootstrap.admin.curp=SEEDIT00000000001" })
class AdminSeedIT {

	private static final String ADMIN_USERNAME = "admin.seed.it@utez.edu.mx";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserRoleRepository userRoleRepository;

	@Autowired
	private AdminSeedRunner adminSeedRunner;

	@Test
	void freshApplicationContextCreatesExactlyOneAdmin() {
		assertThat(userRoleRepository.existsByRoleType(RoleType.ADMIN)).isTrue();

		Optional<User> admin = userRepository.findByUsername(ADMIN_USERNAME);
		assertThat(admin).isPresent();
		assertThat(userRoleRepository.findByUserId(admin.get().getId())).hasSize(1);
	}

	@Test
	void reRunningSeedRunnerAfterBootSkipsReseed() {
		Optional<User> adminAfterBoot = userRepository.findByUsername(ADMIN_USERNAME);
		assertThat(adminAfterBoot).isPresent();
		UUID adminIdAfterBoot = adminAfterBoot.get().getId();

		// Simulates what a real application restart would trigger against a
		// persistent database that still has the seeded ADMIN: the same
		// ApplicationRunner logic runs again.
		adminSeedRunner.run(new DefaultApplicationArguments());

		Optional<User> adminAfterRerun = userRepository.findByUsername(ADMIN_USERNAME);
		assertThat(adminAfterRerun).isPresent();
		assertThat(adminAfterRerun.get().getId()).isEqualTo(adminIdAfterBoot);
		assertThat(userRoleRepository.findByUserId(adminIdAfterBoot)).hasSize(1);
	}
}
