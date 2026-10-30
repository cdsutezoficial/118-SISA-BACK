package mx.edu.utez.sisa.identity.infrastructure.bootstrap;

import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.shared.model.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Gives the seeded {@code director@utez.edu.mx} account a linked {@code Person}
 * so it can be selected as a division's director.
 *
 * <p>{@link TestAccountsSeedRunner} intentionally creates app users with
 * {@code personId = null} (credentials and personal data live in separate
 * tables). That is fine for login, but {@code AcademicDivision.directorPersonId}
 * is a FK to {@code person}, so the director picker has nothing valid to send
 * while the seeded account has no linked person. This runner closes that gap in
 * dev/test only: it no-ops when the account does not exist (i.e. when the test
 * password is blank and {@link TestAccountsSeedRunner} never ran).
 *
 * <p>Runs after the accounts ({@code @Order(10)}) and before the division-scope
 * backfill ({@code @Order(15)}). Idempotent: it returns immediately once the
 * account already has a {@code personId}, and it reuses an existing seeded
 * person instead of creating a duplicate.
 */
@Component
@Order(11)
public class TestDirectorPersonSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(TestDirectorPersonSeedRunner.class);

	private static final String DIRECTOR_USERNAME = "director@utez.edu.mx";

	/**
	 * Clearly synthetic CURP reserved for this seed so it can never collide with
	 * a real person's CURP, while still satisfying the 18-char column.
	 */
	private static final String DIRECTOR_CURP = "DITD800101HDFXXX01";

	private static final String DIRECTOR_FIRST_NAME = "Director";

	private static final String DIRECTOR_LAST_NAME1 = "de Divisi\u00f3n";

	private static final String DIRECTOR_LAST_NAME2 = "Prueba";

	private final UserRepository userRepository;

	private final PersonRepository personRepository;

	public TestDirectorPersonSeedRunner(UserRepository userRepository, PersonRepository personRepository) {
		this.userRepository = userRepository;
		this.personRepository = personRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		User user = userRepository.findByUsername(DIRECTOR_USERNAME).orElse(null);
		if (user == null || user.getPersonId() != null) {
			return;
		}

		Person person = personRepository.findByInstitutionalEmail(DIRECTOR_USERNAME)
				.or(() -> personRepository.findByCurp(DIRECTOR_CURP))
				.orElse(null);
		if (person == null) {
			person = personRepository.save(new Person(DIRECTOR_CURP, DIRECTOR_FIRST_NAME, DIRECTOR_LAST_NAME1,
					DIRECTOR_LAST_NAME2, DIRECTOR_USERNAME));
		} else if (userRepository.findByPersonId(person.getId()).isPresent()) {
			log.warn("Person {} is already linked to another user; leaving {} without a person", person.getId(),
					DIRECTOR_USERNAME);
			return;
		}

		user.linkPerson(person.getId());
		userRepository.save(user);
		log.info("Linked test account {} to person {}", DIRECTOR_USERNAME, person.getId());
	}
}
