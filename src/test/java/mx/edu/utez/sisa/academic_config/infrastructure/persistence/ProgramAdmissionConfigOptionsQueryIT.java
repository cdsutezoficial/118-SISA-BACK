package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.infrastructure.persistence.AdmissionPaymentJpaRepository;
import mx.edu.utez.sisa.admission.infrastructure.persistence.CandidateJpaRepository;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB coverage for
 * {@link ProgramAdmissionConfigJpaRepository#findOpenOfferedOptions}, in the
 * style of {@code ProgramAdmissionConfigRepositoryAdapterSearchIT}.
 *
 * <p>This query is the one place where the sales window and the paid-ficha quota
 * are enforced <em>in SQL</em> rather than in a use case, so a unit test with a
 * mocked repository would prove nothing about it: a typo in the JPQL, a wrong
 * entity name, or a filter that quietly compares against the wrong column only
 * surfaces when Hibernate parses the query and the database executes it. Both
 * happen here.
 *
 * <h2>Warning: this class recreates the schema it points at</h2>
 * There is no embedded test database configured, so {@code ddl-auto=create-drop}
 * runs against whatever {@code DB_URL} names — the local MySQL development
 * database by default — dropping and rebuilding it from the entities. Point
 * {@code DB_URL} at a throwaway database when running this locally.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ProgramAdmissionConfigOptionsQueryIT {

	private static final Instant NOW = Instant.parse("2026-09-25T18:00:00Z");

	private static final Instant OPENS_AT = Instant.parse("2026-09-01T15:00:00Z");

	private static final Instant CLOSES_AT = Instant.parse("2026-10-01T05:00:00Z");

	private static final int MAX_CANDIDATES = 40;

	@Autowired
	private ProgramAdmissionConfigJpaRepository jpaRepository;

	@Autowired
	private AcademicProgramJpaRepository academicProgramJpaRepository;

	@Autowired
	private CandidateJpaRepository candidateJpaRepository;

	@Autowired
	private AdmissionPaymentJpaRepository admissionPaymentJpaRepository;

	@Test
	void returnsAnOpenOfferedConfigInsideItsWindow() {
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);

		List<ProgramAdmissionConfigJpaRepository.ProgramAdmissionConfigOptionProjection> options = options();

		assertThat(idsOf(options)).containsExactly(config.getId());
	}

	@Test
	void hidesAConfigWhoseSaleHasNotOpenedYet() {
		saveOpenOfferedConfig(MAX_CANDIDATES, NOW.plusSeconds(60), CLOSES_AT);

		assertThat(options()).isEmpty();
	}

	@Test
	void hidesAConfigWhoseSaleAlreadyClosed() {
		saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, NOW.minusSeconds(60));

		assertThat(options()).isEmpty();
	}

	@Test
	void keepsAConfigOnTheExactInstantsItOpensAndCloses() {
		// Inclusive on both ends, same as RegisterCandidateUseCaseImpl. If the two
		// ever disagree, the picker shows a career the registration endpoint
		// rejects — for a whole day, at each edge.
		saveOpenOfferedConfig(MAX_CANDIDATES, NOW, CLOSES_AT);
		saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, NOW);

		assertThat(options()).hasSize(2);
	}

	@Test
	void hidesAConfigThatIsNotOpen() {
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		config.close();
		jpaRepository.save(config);

		assertThat(options()).isEmpty();
	}

	@Test
	void hidesAConfigThatIsNotOffered() {
		saveConfig(false, MAX_CANDIDATES, OPENS_AT, CLOSES_AT);

		assertThat(options()).isEmpty();
	}

	@Test
	void hidesAConfigWhosePaidFichasReachedTheQuota() {
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		savePaidFichas(config.getId(), MAX_CANDIDATES);

		assertThat(options()).isEmpty();
	}

	@Test
	void keepsAConfigOnItsLastFreeSlot() {
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		savePaidFichas(config.getId(), MAX_CANDIDATES - 1);

		assertThat(idsOf(options())).containsExactly(config.getId());
	}

	@Test
	void doesNotCountPendingFichasAgainstTheQuota() {
		// The quota is fichas *sold*. Unpaid registrations are the whole reason
		// "pueden registrarse 100 pero solo pagan 15" is allowed, so counting
		// PENDING here would close the sale the moment anyone filled the form.
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		savePendingFichas(config.getId(), MAX_CANDIDATES + 20);

		assertThat(idsOf(options())).containsExactly(config.getId());
	}

	@Test
	void countsOnlyTheFichasOfTheConfigBeingOffered() {
		// A full career must not close a different one: the subquery is joined on
		// cand.admissionConfigId = c.id, and this is what proves the join is real.
		ProgramAdmissionConfig full = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		ProgramAdmissionConfig other = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		savePaidFichas(full.getId(), MAX_CANDIDATES);

		assertThat(idsOf(options())).containsExactly(other.getId());
	}

	@Test
	void ordersOptionsByProgramName() {
		saveOpenOfferedConfigFor("Zepelin", MAX_CANDIDATES);
		saveOpenOfferedConfigFor("Aeronautica", MAX_CANDIDATES);

		assertThat(options()).extracting(p -> p.getProgramName()).containsExactly("Aeronautica", "Zepelin");
	}

	private List<ProgramAdmissionConfigJpaRepository.ProgramAdmissionConfigOptionProjection> options() {
		return jpaRepository.findOpenOfferedOptions(NOW);
	}

	private static List<UUID> idsOf(
			List<ProgramAdmissionConfigJpaRepository.ProgramAdmissionConfigOptionProjection> options) {
		return options.stream().map(p -> p.getId()).collect(Collectors.toList());
	}

	private ProgramAdmissionConfig saveOpenOfferedConfigFor(String programName, int maxCandidates) {
		return saveConfig(true, maxCandidates, OPENS_AT, CLOSES_AT, programName);
	}

	private ProgramAdmissionConfig saveOpenOfferedConfig(int maxCandidates, Instant opensAt, Instant closesAt) {
		return saveConfig(true, maxCandidates, opensAt, closesAt, "Ingeniería en Software");
	}

	private ProgramAdmissionConfig saveConfig(boolean offered, int maxCandidates, Instant opensAt, Instant closesAt) {
		return saveConfig(offered, maxCandidates, opensAt, closesAt, "Ingeniería en Software");
	}

	/**
	 * A config row plus the {@code AcademicProgram} the query joins to, since
	 * {@code programId} is a bare {@code UUID} column with no JPA relation.
	 *
	 * <p>{@code offerName} carries a random suffix because {@code academic_program}
	 * has a unique key on {@code (offer_name, modality)} and several of these
	 * tests deliberately create more than one program at once.
	 */
	private ProgramAdmissionConfig saveConfig(boolean offered, int maxCandidates, Instant opensAt, Instant closesAt,
			String programName) {
		AcademicProgram program = new AcademicProgram(UUID.randomUUID(), programName,
				programName + " " + UUID.randomUUID(), "code-" + UUID.randomUUID(), AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, null, null);
		academicProgramJpaRepository.save(program);
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(program.getId(), UUID.randomUUID(), UUID.randomUUID(),
				offered, maxCandidates, opensAt, closesAt);
		return jpaRepository.save(config);
	}

	private void savePaidFichas(UUID admissionConfigId, int count) {
		IntStream.range(0, count).forEach(i -> saveFicha(admissionConfigId, true));
	}

	private void savePendingFichas(UUID admissionConfigId, int count) {
		IntStream.range(0, count).forEach(i -> saveFicha(admissionConfigId, false));
	}

	private void saveFicha(UUID admissionConfigId, boolean paid) {
		Candidate candidate = candidateJpaRepository.save(new Candidate(UUID.randomUUID(), admissionConfigId,
				"FOLIO-" + UUID.randomUUID(), false, true, null));
		AdmissionPayment payment = new AdmissionPayment(candidate.getId(), AdmissionPaymentConcept.ADMISSION_FICHA,
				new BigDecimal("1578.00"), "REF-" + UUID.randomUUID(), LocalDate.of(2026, 10, 5));
		if (paid) {
			payment.markPaid("REC-" + UUID.randomUUID());
		}
		admissionPaymentJpaRepository.save(payment);
	}

	/**
	 * Guards the assumption {@link #doesNotCountPendingFichasAgainstTheQuota()}
	 * rests on: a paid payment really is {@code PAID} in the column the query
	 * filters. Without it, that test would also pass if {@code markPaid} were a
	 * no-op and the quota were never counting anything.
	 */
	@Test
	void paidFichaIsStoredWithPaidStatus() {
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		savePaidFichas(config.getId(), 1);

		assertThat(admissionPaymentJpaRepository.findAll()).isNotEmpty()
				.extracting(AdmissionPayment::getPaymentStatus).containsOnly(AdmissionPaymentStatus.PAID);
	}
}
