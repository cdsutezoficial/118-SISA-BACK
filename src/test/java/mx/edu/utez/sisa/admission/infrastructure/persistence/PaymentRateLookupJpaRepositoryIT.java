package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB coverage for {@link PaymentRateLookupJpaRepository#findRatesPricableForProgram}
 * and the precedence applied on top of it.
 *
 * <p>{@code PaymentConceptQueryAdapterTest} pins WHICH row wins once the
 * candidates are in hand, but it hands that test a list it built itself. This
 * class covers the half that list cannot: that the query parses at all (it
 * correlates a subquery inside a {@code WHERE}, which Hibernate validates only
 * when it is executed) and that its three {@code OR}ed alternatives really do
 * match the right rows. A mocked repository returns whatever it was told to
 * return and would pass happily against a query that filters on the wrong
 * column, or does not compile into the SQL anyone thought it did.
 *
 * <p>The subquery is the reason this test exists rather than a unit test: the
 * level rung compares {@code r.level} against {@code (SELECT p.level FROM
 * academic_program p WHERE p.id = :programId)}, and whether that resolves as a
 * correlated scalar subquery or is quietly rewritten is only knowable by
 * running it.
 *
 * <h2>Warning: this class recreates the schema it points at</h2>
 * {@code ddl-auto=create-drop} means whatever database {@code DB_URL} names is
 * dropped and rebuilt from the entities, and the data in it is not recovered
 * from a dump. Point {@code DB_URL} at a throwaway database when running this
 * locally — pointing it at the development database destroys the admission
 * catalogs, which nothing re-seeds:
 *
 * <pre>
 * mvn verify -Dit.test=PaymentRateLookupJpaRepositoryIT \
 *     -DDB_URL='jdbc:mysql://localhost:3306/sisa_it?createDatabaseIfNotExist=true&amp;serverTimezone=UTC'
 * </pre>
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PaymentRateLookupJpaRepositoryIT {

	private static final LocalDate ON_DATE = LocalDate.of(2026, 9, 25);

	@Autowired
	private PaymentRateLookupJpaRepository repository;

	@Autowired
	private mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicProgramJpaRepository programRepository;

	@Autowired
	private mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicDivisionJpaRepository divisionRepository;

	private UUID conceptId;

	private UUID tsuProgramId;

	private UUID engineeringProgramId;

	/** {@code academic_program} has a unique (offer_name, modality); keep them distinct. */
	private final AtomicInteger programSeq = new AtomicInteger();

	/** The candidates the query returns, as the adapter would order them. */
	private List<PaymentRate> candidatesFor(UUID programId) {
		return repository.findRatesPricableForProgram(conceptId, programId, ON_DATE);
	}

	private Optional<BigDecimal> priceFor(UUID programId) {
		return PaymentConceptQueryAdapter.pickAmount(candidatesFor(programId));
	}

	/**
	 * A rate of THIS test's concept. Not static on purpose: the concept id is the
	 * query's main filter, and a helper that minted its own would make every
	 * lookup return an empty list — which looks exactly like a query that matches
	 * nothing, and is the kind of bug these tests exist to catch rather than
	 * cause.
	 */
	private PaymentRate rate(UUID programId, AcademicLevel level, String amount) {
		return new PaymentRate(conceptId, programId, level, new BigDecimal(amount), null, LocalDate.of(2026, 1, 1));
	}

	@BeforeEach
	void setUp() {
		conceptId = UUID.randomUUID();
		tsuProgramId = programAtLevel(AcademicLevel.TSU);
		engineeringProgramId = programAtLevel(AcademicLevel.INGENIERIA);
	}

	/** A real {@code academic_program} row, so the level subquery has something to find. */
	private UUID programAtLevel(AcademicLevel level) {
		int n = programSeq.incrementAndGet();
		// Division codes and program codes are both unique, and several tests need
		// a second program, so nothing here may repeat.
		AcademicDivision division = divisionRepository.save(new AcademicDivision("Division " + n, "DIV" + n, "d", null));
		return programRepository
				.save(new AcademicProgram(division.getId(), "Prog " + n, "Oferta " + n, "C" + n, level,
						ProgramModality.PRESENCIAL, null, null, null))
				.getId();
	}

	// ── the three rungs ────────────────────────────────────────────────────────

	@Test
	void aRateBoundToTheProgramIsACandidate() {
		repository.save(rate(tsuProgramId, null, "1578.00"));

		assertThat(candidatesFor(tsuProgramId)).extracting(PaymentRate::getAmount)
				.containsExactly(new BigDecimal("1578.00"));
	}

	@Test
	void aRateBoundToTheProgramsLevelIsACandidate() {
		repository.save(rate(null, AcademicLevel.TSU, "1200.00"));

		assertThat(priceFor(tsuProgramId)).contains(new BigDecimal("1200.00"));
	}

	@Test
	void aGeneralRateIsACandidate() {
		repository.save(rate(null, null, "1000.00"));

		assertThat(priceFor(tsuProgramId)).contains(new BigDecimal("1000.00"));
	}

	/**
	 * A level rate only ever prices programs of that level — which is the whole
	 * reason the level is correlated in the query rather than passed in as a
	 * parameter the caller could get wrong.
	 */
	@Test
	void aLevelRateForADifferentLevelIsNotACandidate() {
		repository.save(rate(null, AcademicLevel.INGENIERIA, "1200.00"));

		assertThat(candidatesFor(tsuProgramId)).isEmpty();
		assertThat(candidatesFor(engineeringProgramId))
				.extracting(PaymentRate::getAmount).containsExactly(new BigDecimal("1200.00"));
	}

	// ── precedence, end to end ─────────────────────────────────────────────────

	@Test
	void theProgramsOwnRateWinsOverItsLevelAndTheGeneralOne() {
		repository.save(rate(null, null, "1000.00"));
		repository.save(rate(null, AcademicLevel.TSU, "1200.00"));
		repository.save(rate(tsuProgramId, null, "1578.00"));

		assertThat(priceFor(tsuProgramId)).contains(new BigDecimal("1578.00"));
	}

	@Test
	void theLevelRateWinsOverTheGeneralOne() {
		repository.save(rate(null, null, "1000.00"));
		repository.save(rate(null, AcademicLevel.TSU, "1200.00"));

		assertThat(priceFor(tsuProgramId)).contains(new BigDecimal("1200.00"));
	}

	@Test
	void twoProgramsOfTheSameLevelShareTheLevelRate() {
		UUID anotherTsu = programAtLevel(AcademicLevel.TSU);
		repository.save(rate(null, AcademicLevel.TSU, "1200.00"));

		assertThat(priceFor(tsuProgramId)).contains(new BigDecimal("1200.00"));
		assertThat(priceFor(anotherTsu)).contains(new BigDecimal("1200.00"));
	}

	// ── what must NOT be a candidate ───────────────────────────────────────────

	@Test
	void aRateOfAnotherConceptIsNotACandidate() {
		repository.save(new PaymentRate(UUID.randomUUID(), tsuProgramId, null, new BigDecimal("1578.00"), null,
				LocalDate.of(2026, 1, 1)));

		assertThat(candidatesFor(tsuProgramId)).isEmpty();
	}

	@Test
	void aClosedRateIsNotACandidate() {
		// Superseded by a later rate for the same combination, so its date range
		// no longer contains ON_DATE.
		PaymentRate superseded = repository.save(rate(tsuProgramId, null, "1500.00"));
		repository.save(rate(tsuProgramId, null, "1578.00"));
		superseded.close(LocalDate.of(2026, 9, 1));
		repository.save(superseded);

		assertThat(priceFor(tsuProgramId)).contains(new BigDecimal("1578.00"));
	}

	@Test
	void aRateThatHasNotOpenedYetIsNotACandidate() {
		repository.save(new PaymentRate(UUID.randomUUID(), tsuProgramId, null, new BigDecimal("1578.00"), null,
				ON_DATE.plusDays(1)));

		assertThat(candidatesFor(tsuProgramId)).isEmpty();
	}

	@Test
	void aPeriodScopedRateIsNotACandidate() {
		// A period rate prices a period, not an admission ticket. If it were
		// allowed in, registering a ficha would pick up a tuition-period price.
		repository.save(new PaymentRate(UUID.randomUUID(), tsuProgramId, null, new BigDecimal("1578.00"),
				UUID.randomUUID(), LocalDate.of(2026, 1, 1)));

		assertThat(candidatesFor(tsuProgramId)).isEmpty();
	}

	@Test
	void bothRateEdgesAreInclusive() {
		// A range of exactly one day that IS the day being priced. `validTo` is set
		// to the day before a replacement opens, so a range ending on ON_DATE still
		// covers it — otherwise a superseded price would silently keep charging for
		// one extra day, and a rate starting today would not price today.
		repository.save(new PaymentRate(conceptId, tsuProgramId, null, new BigDecimal("1578.00"), null, ON_DATE));
		PaymentRate endingToday = new PaymentRate(conceptId, tsuProgramId, null, new BigDecimal("1500.00"), null,
				ON_DATE.minusDays(1));
		endingToday.close(ON_DATE.plusDays(1));
		repository.save(endingToday);

		// Both rows contain ON_DATE, so the tiebreak decides: the most recently
		// opened one is the one that had the later say.
		assertThat(priceFor(tsuProgramId)).contains(new BigDecimal("1578.00"));
	}

	@Test
	void aRateWhoseOnlyDayIsTheOneBeingPricedIsACandidate() {
		repository.save(new PaymentRate(conceptId, tsuProgramId, null, new BigDecimal("1578.00"), null, ON_DATE));

		assertThat(priceFor(tsuProgramId)).contains(new BigDecimal("1578.00"));
	}

	// ── no price at all ────────────────────────────────────────────────────────

	@Test
	void aProgramWithNoRatesAtAllHasNoPrice() {
		repository.save(rate(engineeringProgramId, null, "9999.00"));

		assertThat(priceFor(tsuProgramId)).isEmpty();
	}
}
