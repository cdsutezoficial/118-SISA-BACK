package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import java.util.ArrayList;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.service.FichaPaymentWindow;
import mx.edu.utez.sisa.admission.infrastructure.persistence.CandidateJpaRepository;
import mx.edu.utez.sisa.admission.infrastructure.persistence.FichaOccupancyJpaRepository;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

	/**
	 * The calendar date the claim-expiry half of the occupancy rule compares
	 * against. Pinned alongside {@link #NOW} so the two arguments cannot drift:
	 * {@code NOW} is 2026-09-25T18:00Z, which in the admission zone is the 25th.
	 */
	private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

	/**
	 * The admission zone. Not the server's default: {@link #NOW} is 18:00Z, which is
	 * the 25th in Mexico and would already be the 26th in UTC, so a test that
	 * resolved "today" in the wrong zone would sit a day off the boundary this whole
	 * class is about.
	 */
	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	/** The ficha's payment window, the default the application ships with. */
	private static final int PAYMENT_WINDOW_DAYS = 10;

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
	private FichaOccupancyJpaRepository fichaOccupancyJpaRepository;

	@Autowired
	private PaymentConceptJpaRepository paymentConceptJpaRepository;

	@Autowired
	private PaymentRateJpaRepository paymentRateJpaRepository;

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
		// A full cycle must not close a different cycle — and the two cycles have to
		// belong to the SAME program for this to prove anything. With two separate
		// programs the subquery would return the same answer whether it joined on
		// cand.admissionConfigId = c.id or on c.programId, so the join was never
		// actually under test. This is the shape that caught the drift.
		UUID programId = saveProgram("Ingeniería en Software").getId();
		ProgramAdmissionConfig full = saveConfigForProgram(programId, true, MAX_CANDIDATES, OPENS_AT, CLOSES_AT,
				UUID.randomUUID());
		ProgramAdmissionConfig other = saveConfigForProgram(programId, true, MAX_CANDIDATES, OPENS_AT, CLOSES_AT,
				UUID.randomUUID());
		savePaidFichas(full.getId(), MAX_CANDIDATES);

		assertThat(idsOf(options())).containsExactly(other.getId());
	}

	@Test
	void aFullOldCycleDoesNotBlockTheSameProgramsNewCycle() {
		// The exact production bug of §2.1: Ingeniería 2026-1 sold out and closed,
		// Ingeniería 2027-1 opened with a fresh quota. Counting by program summed the
		// old cycle's fichas into the new cycle's count, so the picker offered 2027-1
		// (it counts per config) and the checkout refused it (it counted per program).
		// Both cycles are still inside their window here on purpose — a closed old
		// cycle would drop out of the picker for a different reason and the assertion
		// would pass without the fix.
		UUID programId = saveProgram("Ingeniería en Software").getId();
		ProgramAdmissionConfig oldCycle = saveConfigForProgram(programId, true, MAX_CANDIDATES, OPENS_AT, CLOSES_AT,
				UUID.randomUUID());
		ProgramAdmissionConfig newCycle = saveConfigForProgram(programId, true, MAX_CANDIDATES, OPENS_AT, CLOSES_AT,
				UUID.randomUUID());
		savePaidFichas(oldCycle.getId(), MAX_CANDIDATES);

		assertThat(idsOf(options())).containsExactly(newCycle.getId());
	}

	@Test
	void ordersOptionsByProgramName() {
		saveOpenOfferedConfigFor("Zepelin", MAX_CANDIDATES);
		saveOpenOfferedConfigFor("Aeronautica", MAX_CANDIDATES);

		assertThat(options()).extracting(p -> p.getProgramName()).containsExactly("Aeronautica", "Zepelin");
	}

	@Test
	void hidesAConfigWhoseOpenCheckoutsReachedTheQuota() {
		// A ficha whose applicant is paying right now (an open attempt younger than the
		// 15-minute hold) occupies a place, though no money has arrived. Counting only
		// PAID here would offer a career whose last place is being paid for.
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(config, TODAY.plusDays(5));
		saveFichasPayingSince(config.getId(), MAX_CANDIDATES, NOW.minusSeconds(5 * 60));

		assertThat(options()).isEmpty();
	}

	@Test
	void keepsAConfigOnItsLastSlotWhenItIsHeldByOpenCheckouts() {
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(config, TODAY.plusDays(5));
		saveFichasPayingSince(config.getId(), MAX_CANDIDATES - 1, NOW.minusSeconds(5 * 60));

		assertThat(idsOf(options())).containsExactly(config.getId());
	}

	@Test
	void stopsCountingAnOpenAttemptOlderThanTheHold() {
		// Nobody closed these attempts: they are still STARTED. They leave the count
		// because they are older than the 15-minute hold, which is the rule that keeps
		// an abandoned checkout from shrinking a career — with no job involved.
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveFichasPayingSince(config.getId(), MAX_CANDIDATES, NOW.minusSeconds(16 * 60));

		assertThat(idsOf(options())).containsExactly(config.getId());
		assertThat(occupied(config.getId())).isZero();
	}

	@Test
	void keepsCountingAnOpenAttemptOnTheLastInstantOfTheHold() {
		// The boundary from the other side: opened exactly 15 minutes ago still holds.
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveFichasPayingSince(config.getId(), MAX_CANDIDATES, holdSince());

		assertThat(options()).isEmpty();
	}

	@Test
	void aClosedAttemptHoldsNothing() {
		// A refused or timed-out attempt is history: closing it is what released the place.
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		IntStream.range(0, MAX_CANDIDATES).forEach(i -> {
			Payment charge = saveFicha(config.getId(), PaymentStatus.PENDING);
			CheckoutAttempt attempt = attemptFor(charge, NOW.minusSeconds(60));
			attempt.close(CheckoutAttemptCloseReason.REJECTED, NOW);
			entityManager.persist(attempt);
		});

		assertThat(occupied(config.getId())).isZero();
		assertThat(idsOf(options())).containsExactly(config.getId());
	}

	@Test
	void anExemptFichaOccupiesAPlaceLikeAPaidOne() {
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		IntStream.range(0, MAX_CANDIDATES).forEach(i -> saveFicha(config.getId(), PaymentStatus.EXEMPT));

		assertThat(occupied(config.getId())).isEqualTo(MAX_CANDIDATES);
		assertThat(options()).isEmpty();
	}

	@Test
	void severalAttemptsOfOneFichaAreOnePlace() {
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		Payment charge = saveFicha(config.getId(), PaymentStatus.PENDING);
		entityManager.persist(attemptFor(charge, NOW.minusSeconds(60)));
		entityManager.persist(attemptFor(charge, NOW.minusSeconds(30)));

		assertThat(occupied(config.getId())).isEqualTo(1);
	}

	@Test
	void dropdownAndClaimerAgreeOnWhatOccupiesASlot() {
		// The promise made by FichaOccupancyJpaRepository: the dropdown and the checkout
		// enforce the quota with one definition, not two that happen to look alike. The
		// last two entries are two cycles of the SAME program, the only arrangement where
		// "by config" and "by program" give different answers.
		List<ProgramAdmissionConfig> configs = new ArrayList<>();

		ProgramAdmissionConfig empty = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		configs.add(empty);

		ProgramAdmissionConfig onePaid = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		savePaidFichas(onePaid.getId(), 1);
		configs.add(onePaid);

		ProgramAdmissionConfig full = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveFichasPayingSince(full.getId(), MAX_CANDIDATES, NOW.minusSeconds(60));
		configs.add(full);

		ProgramAdmissionConfig lapsed = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveFichasPayingSince(lapsed.getId(), MAX_CANDIDATES, NOW.minusSeconds(20 * 60));
		configs.add(lapsed);

		ProgramAdmissionConfig onTheEdge = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveFichasPayingSince(onTheEdge.getId(), MAX_CANDIDATES, holdSince());
		configs.add(onTheEdge);

		UUID sharedProgramId = saveProgram("Ingeniería en Software").getId();
		ProgramAdmissionConfig oldCycle = saveConfigForProgram(sharedProgramId, true, MAX_CANDIDATES, OPENS_AT,
				CLOSES_AT, UUID.randomUUID());
		saveFichasPayingSince(oldCycle.getId(), MAX_CANDIDATES, NOW.minusSeconds(60));
		configs.add(oldCycle);

		ProgramAdmissionConfig newCycle = saveConfigForProgram(sharedProgramId, true, MAX_CANDIDATES, OPENS_AT,
				CLOSES_AT, UUID.randomUUID());
		configs.add(newCycle);

		for (ProgramAdmissionConfig config : configs) {
			long occupiedByClaimer = occupied(config.getId());
			boolean roomForAnother = occupiedByClaimer < config.getMaxCandidates();
			boolean offeredByDropdown = idsOf(options()).contains(config.getId());

			assertThat(offeredByDropdown)
					.as("the picker and the checkout must agree on config %s (%d occupied of %d)", config.getId(),
							occupiedByClaimer, config.getMaxCandidates())
					.isEqualTo(roomForAnother);
		}
	}

	private List<ProgramAdmissionConfigJpaRepository.ProgramAdmissionConfigOptionProjection> options() {
		return jpaRepository.findOpenOfferedOptions(NOW, holdSince());
	}

	private static Instant holdSince() {
		return FichaPaymentWindow.attemptsHoldingSince(NOW, 15);
	}

	/** The checkout's count, excluding nobody. */
	private long occupied(UUID admissionConfigId) {
		return fichaOccupancyJpaRepository.countOccupied(admissionConfigId, new UUID(0L, 0L),
				List.of(PaymentStatus.PAID, PaymentStatus.EXEMPT), CheckoutAttemptCloseReason.STARTED, holdSince());
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

	private ProgramAdmissionConfig saveConfig(boolean offered, int maxCandidates, Instant opensAt, Instant closesAt,
			String programName) {
		AcademicProgram program = saveProgram(programName);
		return saveConfigForProgram(program.getId(), offered, maxCandidates, opensAt, closesAt, UUID.randomUUID());
	}

	private AcademicProgram saveProgram(String programName) {
		AcademicProgram program = new AcademicProgram(UUID.randomUUID(), programName,
				programName + " " + UUID.randomUUID(), "code-" + UUID.randomUUID(), AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, null, null);
		return academicProgramJpaRepository.save(program);
	}

	private ProgramAdmissionConfig saveConfigForProgram(UUID programId, boolean offered, int maxCandidates,
			Instant opensAt, Instant closesAt, UUID periodId) {
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(programId, periodId, UUID.randomUUID(), offered,
				maxCandidates, opensAt, closesAt);
		return jpaRepository.save(config);
	}

	private void savePaidFichas(UUID admissionConfigId, int count) {
		IntStream.range(0, count).forEach(i -> saveFicha(admissionConfigId, PaymentStatus.PAID));
	}

	private void savePendingFichas(UUID admissionConfigId, int count) {
		IntStream.range(0, count).forEach(i -> saveFicha(admissionConfigId, PaymentStatus.PENDING));
	}

	/** Pending fichas each with one open checkout attempt opened at {@code openedAt}. */
	private void saveFichasPayingSince(UUID admissionConfigId, int count, Instant openedAt) {
		IntStream.range(0, count).forEach(i -> {
			Payment charge = saveFicha(admissionConfigId, PaymentStatus.PENDING);
			entityManager.persist(attemptFor(charge, openedAt));
		});
		entityManager.flush();
	}

	private static CheckoutAttempt attemptFor(Payment charge, Instant openedAt) {
		return new CheckoutAttempt("ORD-" + UUID.randomUUID(), charge.getId(), charge.getCandidateId(), null,
				new BigDecimal("1578.00"), openedAt, null, new BigDecimal("1578.00"), BigDecimal.ZERO, null);
	}

	@Autowired
	private TestEntityManager entityManager;

	private void saveTuitionConceptFor(ProgramAdmissionConfig config, LocalDate availableUntil) {
		PaymentConcept concept = new PaymentConcept("Matrícula " + UUID.randomUUID(), "", "", "",
				PaymentConceptType.ADMISSION, null, false, null, null, false, TODAY.minusDays(30), availableUntil,
				null, new BigDecimal("1578.00"), false, null, false, false, null, List.of());
		concept.activate();
		concept = paymentConceptJpaRepository.save(concept);
		paymentRateJpaRepository.save(new PaymentRate(concept.getId(), config.getProgramId(), null,
				new BigDecimal("1578.00"), null, TODAY.minusDays(30).atStartOfDay()));
	}

	/** A candidate of the config with its ficha charge in {@code status}, linked. */
	private Payment saveFicha(UUID admissionConfigId, PaymentStatus status) {
		Candidate candidate = saveCandidate(admissionConfigId);
		boolean priced = status != PaymentStatus.PENDING;
		BigDecimal due = status == PaymentStatus.EXEMPT ? BigDecimal.ZERO.setScale(2) : new BigDecimal("1578.00");
		Payment charge = new Payment(candidate.getId(), null, "FICHA-ING", null, null, null,
				priced ? new BigDecimal("1578.00") : null, priced ? due : null, null, status,
				PaymentOrigin.ADMISSION, LocalDate.of(2026, 10, 5).atStartOfDay(ZONE).toInstant(), NOW);
		entityManager.persist(charge);
		candidate.linkFichaPayment(charge.getId());
		candidateJpaRepository.save(candidate);
		return charge;
	}

	private Candidate saveCandidate(UUID admissionConfigId) {
		return candidateJpaRepository.save(new Candidate(UUID.randomUUID(), admissionConfigId,
				"FOLIO-" + UUID.randomUUID(), false, true, null));
	}
}
