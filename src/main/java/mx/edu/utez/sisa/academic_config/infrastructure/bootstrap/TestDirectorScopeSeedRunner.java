package mx.edu.utez.sisa.academic_config.infrastructure.bootstrap;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleScopePort;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Attaches the {@code DATID} division to the seeded {@code director@utez.edu.mx}
 * account's {@code DIRECTOR_DIVISION} grant.
 *
 * <p>Split from {@link TestDivisionsSeedRunner} because the two facts must be
 * written by two different modules: the division is {@code academic_config}'s
 * aggregate, the grant is {@code identity}'s. The obvious one-liner —
 * having {@code identity.TestAccountsSeedRunner} look the division up — would
 * force {@code identity} to read a foreign catalog, which is exactly what
 * {@code academic_config.PersonLookupPort} was created to avoid. So the seed is
 * split in time instead: {@code @Order(5)} creates the division, {@code @Order(10)}
 * creates the accounts, and this runner ({@code @Order(15)}) joins the two once
 * both halves are on disk.
 *
 * <p>The alternative of skipping the join is not viable: since
 * {@code ListCandidatesUseCase} fails closed when a {@code DIRECTOR_DIVISION}
 * grant carries no division, a {@code null} scope would leave the Director
 * staring at an empty list with no error anywhere.
 *
 * <p>Idempotent, and silent by design when its inputs are absent — the account
 * only exists when {@code sisa.security.bootstrap.test.password} is set, so an
 * empty password must not produce a warning on every boot of a normal database.
 */
@Component
@Order(15)
public class TestDirectorScopeSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TestDirectorScopeSeedRunner.class);

	private static final String DATID_CODE = "DATID";

	/**
	 * Mirrors the username in {@code identity.TestAccountsSeedRunner}. Kept as a
	 * literal rather than a shared constant because {@code ACCOUNTS} there is
	 * private to that runner; a mismatch is harmless (the runner no-ops), while
	 * exporting the list would couple both seeds to each other.
	 */
	private static final String DIRECTOR_USERNAME = "director@utez.edu.mx";

	private final AcademicDivisionRepository divisionRepository;

	private final UserRoleScopePort userRoleScopePort;

	public TestDirectorScopeSeedRunner(AcademicDivisionRepository divisionRepository, UserRoleScopePort userRoleScopePort) {
		this.divisionRepository = divisionRepository;
		this.userRoleScopePort = userRoleScopePort;
	}

	@Override
	public void run(ApplicationArguments args) {
		AcademicDivision division = divisionRepository.findByCode(DATID_CODE).orElse(null);
		if (division == null) {
			return;
		}
		boolean scoped = userRoleScopePort.scopeDivisionByUsername(DIRECTOR_USERNAME, RoleType.DIRECTOR_DIVISION.name(),
				division.getId());
		if (scoped) {
			log.info("Scoped test account {} to division {}", DIRECTOR_USERNAME, DATID_CODE);
		}
	}
}