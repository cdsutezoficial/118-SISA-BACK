package mx.edu.utez.sisa.academic_config.infrastructure.bootstrap;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the divisions the test accounts need a division-scoped role for.
 *
 * <p>Exists because {@code identity}'s {@code TestAccountsSeedRunner} assigns
 * the {@code DIRECTOR_DIVISION} role with a {@code divisionId} scope, and
 * {@code ListCandidatesUseCase} enforces that scope server-side (RN-ADM-004:
 * "Cada director ve solo candidatos de sus programas"). Without a real
 * division the scope is {@code null}, the query fails closed and the Director
 * would see an empty list no matter what the database contains — so the seed
 * has to run BEFORE the test accounts. That ordering is what {@link Order}
 * (5) guarantees here; {@code TestAccountsSeedRunner} is {@code @Order(10)}.
 *
 * <p>Kept in {@code academic_config} rather than inside the test-accounts
 * runner so the WRITE to {@code academic_divisions} stays in the context that
 * owns that aggregate. {@code identity} only reads the id afterwards, through
 * its own {@code DivisionScopeLookupPort}.
 *
 * <p>Idempotent: the division is created only when its {@code code} is absent.
 * Like every other catalog in this codebase, {@code name} and {@code code} are
 * unique, so a re-run reuses the existing row. It does not depend on
 * {@code sisa.security.bootstrap.test.password} — a database that has the
 * division but no Director account is a perfectly normal intermediate state,
 * and the Director account seed is the one gated on the password.
 */
@Component
@Order(5)
public class TestDivisionsSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TestDivisionsSeedRunner.class);

	/**
	 * División Académica de Tecnologías de la Información y Diseño — the
	 * division the seeded {@code director@utez.edu.mx} account is scoped to.
	 * Its short code is the lookup key, so renaming the division does not break
	 * the scope resolution.
	 */
	private static final String DATID_CODE = "DATID";

	private static final String DATID_NAME = "División Académica de Tecnologías de la Información y Diseño";

	private static final String DATID_DESCRIPTION = "Programas educativos del área de tecnologías de la información y diseño.";

	private final AcademicDivisionRepository divisionRepository;

	public TestDivisionsSeedRunner(AcademicDivisionRepository divisionRepository) {
		this.divisionRepository = divisionRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		seedDivision(DATID_CODE, DATID_NAME, DATID_DESCRIPTION);
	}

	private void seedDivision(String code, String name, String description) {
		if (divisionRepository.findByCode(code).isPresent()) {
			return;
		}
		// directorPersonId stays null on purpose: the division's director is a
		// separate manual step (plan 2026-07-28-director-division-role-filter —
		// the field only exists in "Editar División", after the role exists).
		divisionRepository.save(new AcademicDivision(name, code, description, null));
		log.info("Seeded academic division {} ({})", name, code);
	}
}
