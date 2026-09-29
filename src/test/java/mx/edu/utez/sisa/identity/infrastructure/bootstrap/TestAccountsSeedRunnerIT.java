package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Spring-context coverage for the {@code TestAccountsSeedRunner} dev
 * seed. Boots the actual {@code ApplicationContext} with real JPA
 * repositories + BCrypt to verify the wiring: on boot exactly one account per
 * developed role (ADMIN, SERVICIOS_ESCOLARES, PERSONAL_FINANZAS,
 * DIRECTOR_DIVISION) is created as a SINGLE role grant each, and re-running
 * the seed logic against already-seeded state — the situation a real restart
 * against a persistent database would produce — does not create duplicates.
 *
 * <p>Runs against a dedicated temporary MySQL schema ({@code sisa_seed_test},
 * {@code ddl-auto=create-drop}) instead of the developer's everyday
 * {@code sisa} database, so existing development data cannot trip the
 * role-based idempotency checks. The idempotency path is exercised by
 * invoking the already wired bean's {@code run()} a second time directly
 * (same rationale as the former {@code AdminSeedIT}).
 */
@SpringBootTest(properties = {
		"sisa.security.bootstrap.test.password=SeedItPass!1",
		"spring.datasource.url=jdbc:mysql://localhost:3306/sisa_seed_test?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
		"spring.jpa.hibernate.ddl-auto=create-drop" })
class TestAccountsSeedRunnerIT {

	/**
	 * One expected username per developed role, with the role it must be
	 * granted. The account must carry EXACTLY that grant — multi-role test
	 * accounts were deliberately removed.
	 */
	private static final Map<String, RoleType> EXPECTED = Map.of(
			"admin@utez.edu.mx", RoleType.ADMIN,
			"servicios.escolares@utez.edu.mx", RoleType.SERVICIOS_ESCOLARES,
			"finanzas@utez.edu.mx", RoleType.PERSONAL_FINANZAS,
			"director@utez.edu.mx", RoleType.DIRECTOR_DIVISION);

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserRoleRepository userRoleRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private TestAccountsSeedRunner testAccountsSeedRunner;

	@Test
	void freshApplicationContextCreatesExactlyOneAccountPerDevelopedRole() {
		for (Map.Entry<String, RoleType> expected : EXPECTED.entrySet()) {
			Optional<User> user = userRepository.findByUsername(expected.getKey());
			assertThat(user).withFailMessage("Expected seeded account %s", expected.getKey()).isPresent();

			List<UserRole> grants = userRoleRepository.findByUserId(user.get().getId());
			assertThat(grants).withFailMessage("Account %s must carry exactly one role", expected.getKey()).hasSize(1);
			assertThat(grants.get(0).getRoleId()).isEqualTo(resolveRoleId(expected.getValue()));
		}
	}

	@Test
	void reRunningSeedRunnerAfterBootSkipsReseed() {
		Optional<User> directorBefore = userRepository.findByUsername("director@utez.edu.mx");
		assertThat(directorBefore).isPresent();
		UUID directorIdBefore = directorBefore.get().getId();

		// Simulates what a real application restart would trigger against a
		// persistent database that still has the seeded accounts: the same
		// ApplicationRunner logic runs again.
		testAccountsSeedRunner.run(new DefaultApplicationArguments());

		Optional<User> directorAfter = userRepository.findByUsername("director@utez.edu.mx");
		assertThat(directorAfter).isPresent();
		assertThat(directorAfter.get().getId()).isEqualTo(directorIdBefore);
		assertThat(userRoleRepository.findByUserId(directorIdBefore)).hasSize(1);
	}

	private UUID resolveRoleId(RoleType roleType) {
		return roleRepository.findByKey(roleType.name()).map(Role::getId).orElseThrow();
	}
}