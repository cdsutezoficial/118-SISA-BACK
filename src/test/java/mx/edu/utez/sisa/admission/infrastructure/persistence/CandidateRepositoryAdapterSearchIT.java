package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository.CandidateSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository.CandidateSearchPage;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB coverage for {@link CandidateRepositoryAdapter#search}: the JPQL is
 * only validated by Hibernate when executed, so a mocked repository would
 * happily pass a query that filters on the wrong column or compiles into
 * different SQL than intended. This is the half a unit test cannot cover — the
 * three {@code EXISTS} subqueries (program, period, division) and the free-text
 * {@code OR} over folio/CURP/name.
 *
 * <p>{@code programId} is asserted as the ROUND TRIP: the filter matches
 * {@code ProgramAdmissionConfig.programId} (the {@code AcademicProgram} id),
 * and the row that comes back must be identified by that same value, or the
 * "Programa Solicitado" filter would match rows it then cannot re-find.
 *
 * <h2>Warning: this class recreates the schema it points at</h2>
 * {@code ddl-auto=create-drop} drops and rebuilds whatever database
 * {@code DB_URL} names. Point it at a throwaway database:
 *
 * <pre>
 * mvn verify -Dit.test=CandidateRepositoryAdapterSearchIT \
 *     -DDB_URL='jdbc:mysql://localhost:3306/sisa_it?createDatabaseIfNotExist=true&amp;serverTimezone=UTC'
 * </pre>
 */
@DataJpaTest
@Import(CandidateRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class CandidateRepositoryAdapterSearchIT {

	@Autowired
	private CandidateRepositoryAdapter adapter;

	@Autowired
	private CandidateJpaRepository candidateJpaRepository;

	@Autowired
	private CandidatePersonJpaRepository personJpaRepository;

	@Autowired
	private mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicDivisionJpaRepository divisionRepository;

	@Autowired
	private mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicProgramJpaRepository programRepository;

	@Autowired
	private mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicPeriodJpaRepository periodRepository;

	@Autowired
	private mx.edu.utez.sisa.academic_config.infrastructure.persistence.ProgramAdmissionConfigJpaRepository configRepository;

	/** Unique columns (division/program code, offer name, period name) must not repeat across seeds. */
	private final AtomicInteger seq = new AtomicInteger();

	private CandidateSearchPage search(CandidateStatus status, UUID programId, UUID periodId, UUID divisionId,
			String term) {
		return adapter.search(new CandidateSearchCriteria(status, programId, periodId, divisionId, term, 0, 20));
	}

	private UUID newDivision() {
		int n = seq.incrementAndGet();
		return divisionRepository.save(new AcademicDivision("Division " + n, "DIV" + n, "d", null)).getId();
	}

	private UUID newPeriod() {
		int n = seq.incrementAndGet();
		return periodRepository
				.save(new AcademicPeriod("Periodo " + n, 2026, n, PeriodType.CUATRIMESTRAL, LocalDate.of(2026, 1, 5),
						LocalDate.of(2026, 4, 30), LocalDate.of(2025, 11, 1), LocalDate.of(2025, 12, 15)))
				.getId();
	}

	/** A real {@code ProgramAdmissionConfig}, returning BOTH ids the filters need. */
	private ProgramSeeds newConfig(UUID divisionId, UUID periodId) {
		int n = seq.incrementAndGet();
		AcademicProgram program = programRepository.save(new AcademicProgram(divisionId, "Ingenieria " + n,
				"Oferta " + n, "PROG" + n, AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, null, null));
		ProgramAdmissionConfig config = configRepository.save(new ProgramAdmissionConfig(program.getId(), periodId,
				UUID.randomUUID(), true, 50, Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-12-31T00:00:00Z")));
		return new ProgramSeeds(program.getId(), config.getId());
	}

	private UUID newPerson(String curp, String firstName, String lastName1, String lastName2) {
		return personJpaRepository.save(new Person(curp, firstName, lastName1, lastName2, null)).getId();
	}

	/** {@code Person.curp} is {@code length = 18, unique}; this is exactly 18 and unique per call. */
	private String nextCurp() {
		return String.format("CURP%014d", seq.incrementAndGet());
	}

	private Candidate newCandidate(UUID personId, UUID configId, String folio) {
		return candidateJpaRepository.save(new Candidate(personId, configId, folio, true, true, null));
	}

	/** Creates its own person + config so folios/CURPs never collide between tests. */
	private Candidate newCandidate(String folio) {
		int n = seq.incrementAndGet();
		UUID personId = newPerson(nextCurp(), "Nombre" + n, "Apellido" + n, null);
		UUID configId = newConfig(newDivision(), newPeriod()).configId();
		return newCandidate(personId, configId, folio);
	}

	private record ProgramSeeds(UUID programId, UUID configId) {
	}

	// ── filters ────────────────────────────────────────────────────────────────

	@Test
	void unrestrictedSearchReturnsEveryCandidate() {
		newCandidate("ADM-2026-000001");
		newCandidate("ADM-2026-000002");

		assertThat(search(null, null, null, null, null).totalElements()).isEqualTo(2L);
	}

	@Test
	void statusFilterMatchesExactStatus() {
		newCandidate("ADM-2026-000001");
		Candidate paid = newCandidate("ADM-2026-000002");
		paid.markPaid();
		candidateJpaRepository.save(paid);

		assertThat(search(CandidateStatus.REGISTERED, null, null, null, null).totalElements()).isEqualTo(1L);
		assertThat(search(CandidateStatus.PAID, null, null, null, null).totalElements()).isEqualTo(1L);
		assertThat(search(CandidateStatus.ACCEPTED, null, null, null, null).totalElements()).isZero();
	}

	@Test
	void programFilterMatchesOnlyThatProgram() {
		ProgramSeeds targeted = newConfig(newDivision(), newPeriod());
		UUID otherConfig = newConfig(newDivision(), newPeriod()).configId();
		newCandidate(newPerson(nextCurp(), "Ana", "Uno", null), targeted.configId(), "ADM-2026-000001");
		newCandidate(newPerson(nextCurp(), "Beto", "Dos", null), otherConfig, "ADM-2026-000002");

		assertThat(search(null, targeted.programId(), null, null, null).totalElements()).isEqualTo(1L);
		assertThat(search(null, UUID.randomUUID(), null, null, null).totalElements()).isZero();
	}

	@Test
	void periodFilterMatchesOnlyThatPeriod() {
		UUID periodA = newPeriod();
		UUID periodB = newPeriod();
		UUID configA = newConfig(newDivision(), periodA).configId();
		UUID configB = newConfig(newDivision(), periodB).configId();
		newCandidate(newPerson(nextCurp(), "Ana", "Uno", null), configA, "ADM-2026-000001");
		newCandidate(newPerson(nextCurp(), "Beto", "Dos", null), configB, "ADM-2026-000002");

		assertThat(search(null, null, periodA, null, null).totalElements()).isEqualTo(1L);
		assertThat(search(null, null, periodB, null, null).totalElements()).isEqualTo(1L);
	}

	@Test
	void divisionFilterMatchesOnlyProgramsInThatDivision() {
		UUID divisionA = newDivision();
		UUID divisionB = newDivision();
		UUID configA = newConfig(divisionA, newPeriod()).configId();
		UUID configB = newConfig(divisionB, newPeriod()).configId();
		newCandidate(newPerson(nextCurp(), "Ana", "Uno", null), configA, "ADM-2026-000001");
		newCandidate(newPerson(nextCurp(), "Beto", "Dos", null), configB, "ADM-2026-000002");

		assertThat(search(null, null, null, divisionA, null).totalElements()).isEqualTo(1L);
		assertThat(search(null, null, null, divisionB, null).totalElements()).isEqualTo(1L);
		assertThat(search(null, null, null, UUID.randomUUID(), null).totalElements()).isZero();
	}

	// ── free text ──────────────────────────────────────────────────────────────

	@Test
	void searchMatchesFolioCaseInsensitively() {
		newCandidate("ADM-2026-000001");
		newCandidate("ADM-2026-000002");

		assertThat(search(null, null, null, null, "000001").totalElements()).isEqualTo(1L);
		assertThat(search(null, null, null, null, "adm-2026").totalElements()).isEqualTo(2L);
	}

	@Test
	void searchMatchesCurpAndEitherSurname() {
		UUID personId = newPerson("GOCD050101HDFRNS04", "Ana", "Torres", "Ramos");
		newCandidate(personId, newConfig(newDivision(), newPeriod()).configId(), "ADM-2026-000001");

		assertThat(search(null, null, null, null, "gocd05").content()).hasSize(1);
		assertThat(search(null, null, null, null, "torres").content()).hasSize(1);
		assertThat(search(null, null, null, null, "ramos").content()).hasSize(1);
		assertThat(search(null, null, null, null, "ana").content()).hasSize(1);
		assertThat(search(null, null, null, null, "inexistente").content()).isEmpty();
	}

	// ── pagination ─────────────────────────────────────────────────────────────

	@Test
	void paginationMetadataReflectsTotalElementsAcrossPages() {
		for (int i = 1; i <= 5; i++) {
			newCandidate(String.format("ADM-2026-%06d", i));
		}

		CandidateSearchPage first = adapter.search(new CandidateSearchCriteria(null, null, null, null, null, 0, 2));
		CandidateSearchPage second = adapter.search(new CandidateSearchCriteria(null, null, null, null, null, 1, 2));

		assertThat(first.totalElements()).isEqualTo(5L);
		assertThat(first.totalPages()).isEqualTo(3);
		assertThat(first.content()).hasSize(2);
		assertThat(second.content()).hasSize(2);
	}
}
