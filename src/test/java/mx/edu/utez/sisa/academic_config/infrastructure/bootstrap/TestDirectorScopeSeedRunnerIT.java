package mx.edu.utez.sisa.academic_config.infrastructure.bootstrap;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.model.UserRole;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Spring-context coverage for the two-runner division-scope seed:
 * {@code TestDivisionsSeedRunner} ({@code @Order(5)}, creates DATID) and
 * {@code TestDirectorScopeSeedRunner} ({@code @Order(15)}, points the seeded
 * {@code director@utez.edu.mx} grant at it).
 *
 * <p>Boots with the same properties as {@link
 * mx.edu.utez.sisa.identity.infrastructure.bootstrap.TestAccountsSeedRunnerIT}
 * so the two share one cached context and a dedicated {@code sisa_seed_test}
 * schema. This is the fail-closed guarantee {@code ListCandidatesUseCase}
 * depends on: if the scope were missing, a Director would silently see an
 * empty list, so the wiring is asserted end to end rather than unit-mocked.
 */
@SpringBootTest(properties = {
		"sisa.security.bootstrap.test.password=SeedItPass!1",
		"spring.datasource.url=jdbc:mysql://localhost:3306/sisa_seed_test?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
		"spring.jpa.hibernate.ddl-auto=create-drop" })
class TestDirectorScopeSeedRunnerIT {

	private static final String DIRECTOR_USERNAME = "director@utez.edu.mx";

	@Autowired
	private AcademicDivisionRepository divisionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserRoleRepository userRoleRepository;

	@Autowired
	private TestDirectorScopeSeedRunner testDirectorScopeSeedRunner;

	@Test
	void datidDivisionIsSeededWithItsStableCode() {
		Optional<AcademicDivision> datid = divisionRepository.findByCode("DATID");
		assertThat(datid).isPresent();
		assertThat(datid.get().getCode()).isEqualTo("DATID");
	}

	@Test
	void directorGrantIsScopedToTheDatidDivision() {
		AcademicDivision datid = divisionRepository.findByCode("DATID").orElseThrow();
		User director = userRepository.findByUsername(DIRECTOR_USERNAME).orElseThrow();

		List<UserRole> grants = userRoleRepository.findByUserId(director.getId());
		assertThat(grants).hasSize(1);
		assertThat(grants.get(0).getDivisionId()).isEqualTo(datid.getId());
	}

	@Test
	void reRunningScopeSeedKeepsTheSameSingleScopedGrant() {
		AcademicDivision datid = divisionRepository.findByCode("DATID").orElseThrow();
		User director = userRepository.findByUsername(DIRECTOR_USERNAME).orElseThrow();

		testDirectorScopeSeedRunner.run(new DefaultApplicationArguments());

		List<UserRole> grants = userRoleRepository.findByUserId(director.getId());
		assertThat(grants).hasSize(1);
		assertThat(grants.get(0).getDivisionId()).isEqualTo(datid.getId());
		assertThat(grants.get(0).getRoleId()).isNotNull();
	}
}
