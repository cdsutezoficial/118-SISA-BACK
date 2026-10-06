package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.service.FichaPaymentWindow;
import mx.edu.utez.sisa.admission.infrastructure.persistence.AdmissionPaymentJpaRepository;
import mx.edu.utez.sisa.admission.infrastructure.persistence.CandidateJpaRepository;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.sql.Timestamp;
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
	private AdmissionPaymentJpaRepository admissionPaymentJpaRepository;

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
	void hidesAConfigWhoseClaimedCheckoutsReachedTheQuota() {
		// A ficha that reached the checkout and reserved its slot occupies one, even
		// though no money has arrived. Counting only PAID here would show a career as
		// available while the claim that blocks it is already recorded.
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(config, TODAY.plusDays(5));
		saveClaimedFichas(config.getId(), MAX_CANDIDATES);

		assertThat(options()).isEmpty();
	}

	@Test
	void keepsAConfigOnItsLastSlotWhenItIsHeldByClaims() {
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(config, TODAY.plusDays(5));
		saveClaimedFichas(config.getId(), MAX_CANDIDATES - 1);

		assertThat(idsOf(options())).containsExactly(config.getId());
	}

	@Test
	void stopsCountingAClaimWhoseFichaIsPastItsPaymentWindow() {
		// Nobody swept a counter here: the claim is still stamped and still PENDING.
		// It leaves the count because the ficha itself is too old to pay, which is
		// the rule that keeps a dropped checkout from shrinking a career for good.
		//
		// Stamped as registered 11 days ago against a 10-day window. The catalog is
		// deliberately left unconfigured: a claim is released by the ficha's own
		// dates, so it stops occupying a slot even when the admission concept is
		// still perfectly payable.
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(config, TODAY.plusDays(5));
		saveClaimedFichasRegisteredOn(config.getId(), MAX_CANDIDATES, TODAY.minusDays(11));

		assertThat(idsOf(options())).containsExactly(config.getId());
	}

	@Test
	void keepsCountingAClaimOnTheLastDayOfItsPaymentWindow() {
		// The boundary from the other side, and the one worth pinning. Registration
		// day is day 0, so a ficha registered on the 15th has its 10 days through the
		// 25th and is payable all of the 25th; it only lapses on the 26th.
		ProgramAdmissionConfig config = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(config, TODAY.plusDays(5));
		saveClaimedFichasRegisteredOn(config.getId(), MAX_CANDIDATES, TODAY.minusDays(PAYMENT_WINDOW_DAYS));

		assertThat(options()).isEmpty();
	}

	@Test
	void keepsCountingAClaimWhileTheProcessIsStillOpen() {
		// A ficha inside its own 10-day plazo still occupies its place right up to the
		// last day of sales. closesAt is compared against the <em>start</em> of today,
		// not against now, so a career closing at 23:00 is not shut by the hour of the
		// day it happens to close — and its claims are not released before that.
		ProgramAdmissionConfig closesTonight = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT,
				TODAY.atTime(23, 0).atZone(ZONE).toInstant());
		saveClaimedFichas(closesTonight.getId(), MAX_CANDIDATES);

		assertThat(claimed(closesTonight.getId())).isEqualTo(MAX_CANDIDATES);
	}

	@Test
	void releasesEveryClaimOnceTheProcessHasClosed() {
		// Closing yesterday releases the claims, which is the half of the rule the
		// catalog never enforced: a place that can no longer be bought is not
		// occupied. Asserted through the count rather than through the picker, because
		// a closed config is already excluded by the sales-window clause above it and
		// so the picker could not tell the two reasons apart.
		ProgramAdmissionConfig closedYesterday = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT,
				TODAY.minusDays(1).atTime(23, 0).atZone(ZONE).toInstant());
		saveClaimedFichas(closedYesterday.getId(), MAX_CANDIDATES);

		assertThat(claimed(closedYesterday.getId())).isZero();
	}

	@Test
	void dropdownAndClaimerAgreeOnWhatOccupiesASlot() {
		// The promise made by AdmissionPaymentOccupancyQueries: the dropdown and the
		// checkout enforce the quota with one definition, not two that happen to look
		// alike. The user-visible cost of a drift is a career offered in the picker
		// and then refused at the last step, so this walks a set of occupancy states
		// and requires both sides to reach the same verdict on every one.
		//
		// The last two entries are the important ones: they are two cycles of the SAME
		// program, which is the only arrangement where "by config" and "by program"
		// give different answers. Without them this test passed even against the buggy
		// per-program count, because every other config here lives in its own program.
		List<ProgramAdmissionConfig> configs = new java.util.ArrayList<>();

		ProgramAdmissionConfig empty = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(empty, TODAY.plusDays(5));
		configs.add(empty);

		ProgramAdmissionConfig onePaid = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(onePaid, TODAY.plusDays(5));
		savePaidFichas(onePaid.getId(), 1);
		configs.add(onePaid);

		ProgramAdmissionConfig full = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(full, TODAY.plusDays(5));
		saveClaimedFichas(full.getId(), MAX_CANDIDATES);
		configs.add(full);

		ProgramAdmissionConfig lapsed = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(lapsed, TODAY.minusDays(1));
		saveClaimedFichasRegisteredOn(lapsed.getId(), MAX_CANDIDATES, TODAY.minusDays(11));
		configs.add(lapsed);

		ProgramAdmissionConfig onItsLastDay = saveOpenOfferedConfig(MAX_CANDIDATES, OPENS_AT, CLOSES_AT);
		saveTuitionConceptFor(onItsLastDay, TODAY.plusDays(5));
		saveClaimedFichasRegisteredOn(onItsLastDay.getId(), MAX_CANDIDATES,
				TODAY.minusDays(PAYMENT_WINDOW_DAYS));
		configs.add(onItsLastDay);

		UUID sharedProgramId = saveProgram("Ingeniería en Software").getId();
		ProgramAdmissionConfig oldCycle = saveConfigForProgram(sharedProgramId, true, MAX_CANDIDATES, OPENS_AT,
				CLOSES_AT, UUID.randomUUID());
		saveTuitionConceptFor(oldCycle, TODAY.plusDays(5));
		saveClaimedFichas(oldCycle.getId(), MAX_CANDIDATES);
		configs.add(oldCycle);

		ProgramAdmissionConfig newCycle = saveConfigForProgram(sharedProgramId, true, MAX_CANDIDATES, OPENS_AT,
				CLOSES_AT, UUID.randomUUID());
		configs.add(newCycle);

		for (ProgramAdmissionConfig config : configs) {
			long occupiedByClaimer = admissionPaymentJpaRepository.countOccupiedByConfigId(config.getId(),
					AdmissionPaymentStatus.PAID, AdmissionPaymentStatus.PENDING, latestPayableRegistration(),
					midnightToday());
			boolean roomForAnother = occupiedByClaimer < config.getMaxCandidates();
			boolean offeredByDropdown = idsOf(options()).contains(config.getId());

			assertThat(offeredByDropdown)
					.as("the picker and the checkout must agree on config %s (%d occupied of %d)", config.getId(),
							occupiedByClaimer, config.getMaxCandidates())
					.isEqualTo(roomForAnother);
		}
	}

	private List<ProgramAdmissionConfigJpaRepository.ProgramAdmissionConfigOptionProjection> options() {
		return jpaRepository.findOpenOfferedOptions(NOW, latestPayableRegistration(), midnightToday());
	}

	/**
	 * The two cutoffs the occupancy rule compares against, built by
	 * {@code FichaPaymentWindow} rather than written out here.
	 *
	 * <p>This test exists to hold the picker and the claim against each other, and
	 * it can only do that if it asks the database the same question they do. A
	 * hand-written {@code TODAY.minusDays(9)} would be a second copy of the rule,
	 * and a copy is exactly what this test is meant to catch.
	 */
	private Instant latestPayableRegistration() {
		return FichaPaymentWindow.latestPayableRegistration(TODAY, PAYMENT_WINDOW_DAYS, ZONE);
	}

	private Instant midnightToday() {
		return FichaPaymentWindow.startOfDay(TODAY, ZONE);
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
	 *
	 * <p>This creates a <b>fresh program and a fresh period</b> every call, which is
	 * the reason the per-config-vs-per-program bug hid for so long: with each config
	 * in its own program, counting by config and counting by program return the same
	 * number. Tests that need to tell the two apart must use
	 * {@link #saveProgram(String)} once and {@link #saveConfigForProgram} twice.
	 */
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

	/**
	 * A config for an already-persisted program, optionally in a period other than
	 * the one a sibling config uses. This is what makes "two cycles of the same
	 * program" expressible — the exact shape the catalog and the checkout used to
	 * disagree about.
	 */
	private ProgramAdmissionConfig saveConfigForProgram(UUID programId, boolean offered, int maxCandidates,
			Instant opensAt, Instant closesAt, UUID periodId) {
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(programId, periodId, UUID.randomUUID(), offered,
				maxCandidates, opensAt, closesAt);
		return jpaRepository.save(config);
	}

	private void savePaidFichas(UUID admissionConfigId, int count) {
		IntStream.range(0, count).forEach(i -> saveFicha(admissionConfigId, true));
	}

	private void savePendingFichas(UUID admissionConfigId, int count) {
		IntStream.range(0, count).forEach(i -> saveFicha(admissionConfigId, false));
	}

	/**
	 * PENDING fichas that already reserved their slot at the checkout, which is the
	 * occupancy the quota was widened to include.
	 *
	 * <p>{@code Candidate}'s constructor stamps {@code Instant.now()}, so these land
	 * inside their payment window by accident of the machine clock rather than by
	 * construction. {@link #saveClaimedFichasRegisteredOn} exists for the tests that
	 * need the registration day itself to be the thing under test.
	 */
	private void saveClaimedFichas(UUID admissionConfigId, int count) {
		saveClaimedFichasRegisteredOn(admissionConfigId, count, null);
	}

	/**
	 * Claimed fichas whose registration day is chosen rather than inherited from the
	 * wall clock.
	 *
	 * <p>{@code registeredOn == null} keeps {@link Candidate}'s own
	 * {@code Instant.now()}, which is what every test that is not about the window
	 * wants. A named date is applied with a direct column update because
	 * {@code registeredAt} has no setter and no legitimate reason to grow one: a
	 * ficha's registration day is set once, at registration, and is not something
	 * production code should be able to rewrite.
	 */
	private void saveClaimedFichasRegisteredOn(UUID admissionConfigId, int count, LocalDate registeredOn) {
		IntStream.range(0, count).forEach(i -> {
			Candidate candidate = saveCandidate(admissionConfigId);
			if (registeredOn != null) {
				backdateRegistration(candidate, registeredOn);
			}
			AdmissionPayment payment = new AdmissionPayment(candidate.getId(), AdmissionPaymentConcept.ADMISSION_FICHA,
					new BigDecimal("1578.00"), "REF-" + UUID.randomUUID(), LocalDate.of(2026, 10, 5));
			payment.claimCheckoutSlot();
			admissionPaymentJpaRepository.save(payment);
		});
	}

	/**
	 * Rewrites {@code candidate.registered_at} after the insert, for tests that need a
	 * ficha whose window has already run out.
	 *
	 * <p>A native statement on purpose, and in its own transaction: this is fixture
	 * setup for a state that only arises days later in real life, not a state the
	 * application can produce. Going through the entity instead would mean adding a
	 * setter that exists only to let tests lie about history.
	 */
	@Autowired
	private TestEntityManager entityManager;

	private void backdateRegistration(Candidate candidate, LocalDate registeredOn) {
		// Flushed first, or the UPDATE finds no row: the candidate is still sitting in
		// the persistence context from the insert, and a statement that matches nothing
		// would leave the ficha dated "now" and quietly turn these tests into the
		// opposite of what they claim.
		entityManager.flush();
		Instant registeredAt = registeredOn.atTime(9, 0).atZone(ZONE).toInstant();
		entityManager.getEntityManager().createNativeQuery("UPDATE candidate SET registered_at = :at WHERE id = :id")
				.setParameter("at", Timestamp.from(registeredAt)).setParameter("id", candidate.getId()).executeUpdate();
		entityManager.clear();
	}

	/**
	 * What the occupancy rule counts for one config, read through the same interface
	 * the claimer uses.
	 */
	private long claimed(UUID admissionConfigId) {
		return admissionPaymentJpaRepository.countOccupiedByConfigId(admissionConfigId, AdmissionPaymentStatus.PAID,
				AdmissionPaymentStatus.PENDING, latestPayableRegistration(), midnightToday());
	}

	/**
	 * The admission concept a claimed ficha is measured against, plus the rate
	 * that makes it apply to this program. A claim only occupies a slot while
	 * this concept can still be paid, so a test that stamps claims without
	 * creating one is measuring nothing — and since scope now lives in the
	 * rates, a concept with no rate reaching the program does not apply at all
	 * and would leave the slot unoccupied. The type must be {@code ADMISSION}:
	 * the dropdown's embedded copy of the rule and
	 * {@code AdmissionPaymentOccupancyQueries} both filter on it, so an
	 * {@code ENROLLMENT} row would make the two definitions disagree and the
	 * agreement assertion below would fail for the wrong reason.
	 */
	private void saveTuitionConceptFor(ProgramAdmissionConfig config, LocalDate availableUntil) {
		PaymentConcept concept = new PaymentConcept("Matrícula " + UUID.randomUUID(), "", "", "",
				PaymentConceptType.ADMISSION, null, false, null, null, false, TODAY.minusDays(30), availableUntil,
				null, new BigDecimal("1578.00"), false, null, false, false, null, List.of());
		concept.activate();
		concept = paymentConceptJpaRepository.save(concept);
		// A rate bound to this exact program: the most specific rung of the ladder,
		// so the concept applies no matter what the program's level is.
		paymentRateJpaRepository.save(new PaymentRate(concept.getId(), config.getProgramId(), null,
				new BigDecimal("1578.00"), null, TODAY.minusDays(30).atStartOfDay()));
	}

	private void saveFicha(UUID admissionConfigId, boolean paid) {
		Candidate candidate = saveCandidate(admissionConfigId);
		AdmissionPayment payment = new AdmissionPayment(candidate.getId(), AdmissionPaymentConcept.ADMISSION_FICHA,
				new BigDecimal("1578.00"), "REF-" + UUID.randomUUID(), LocalDate.of(2026, 10, 5));
		if (paid) {
			payment.markPaid("REC-" + UUID.randomUUID());
		}
		admissionPaymentJpaRepository.save(payment);
	}

	private Candidate saveCandidate(UUID admissionConfigId) {
		return candidateJpaRepository.save(new Candidate(UUID.randomUUID(), admissionConfigId,
				"FOLIO-" + UUID.randomUUID(), false, true, null));
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
