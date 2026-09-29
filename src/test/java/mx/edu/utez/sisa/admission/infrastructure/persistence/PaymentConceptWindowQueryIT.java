package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB coverage for the two window lookups in
 * {@link PaymentConceptLookupJpaRepository}, in the style of
 * {@code ProgramAdmissionConfigOptionsQueryIT}.
 *
 * <p>These are the queries block 3 of the plan hinges on, and both are JPQL that
 * spans {@code payment_concept} and {@code payment_rate}: a unit test with a
 * mocked repository returns whatever the mock was told to return and would pass
 * even if the query filtered on the wrong column. The distinction that matters —
 * the date-filtered query excludes a closed concept, the window-ignoring one
 * still finds it — is only observable when the predicates are actually parsed and
 * executed.
 *
 * <h2>Warning: this class recreates the schema it points at</h2>
 * {@code ddl-auto=create-drop} means whatever database {@code DB_URL} names is
 * dropped and rebuilt from the entities, and the data in it is not recovered
 * from a dump. Point {@code DB_URL} at a throwaway database when running this
 * locally:
 *
 * <pre>
 * mvn verify -Dit.test=PaymentConceptWindowQueryIT \
 *     -DDB_URL='jdbc:mysql://localhost:3306/sisa_it?createDatabaseIfNotExist=true&amp;serverTimezone=UTC'
 * </pre>
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PaymentConceptWindowQueryIT {

	private static final LocalDate ON_DATE = LocalDate.of(2026, 9, 25);

	private static final UUID PROGRAM = UUID.randomUUID();

	private static final UUID OTHER_PROGRAM = UUID.randomUUID();

	@Autowired
	private PaymentConceptLookupJpaRepository repository;

	@Autowired
	private PaymentRateLookupJpaRepository rates;

	// ── the window-ignoring query: what makes "closed" distinguishable from "absent" ──

	/**
	 * The whole reason {@code findActiveTuitionForProgramIgnoringWindow} exists.
	 * The date-filtered query correctly returns nothing for a period that has
	 * closed — but an empty list alone cannot tell the caller whether the
	 * concept is gone or merely out of its window, and those two deserve
	 * different messages. This query is the one that keeps them apart.
	 */
	@Test
	void theWindowIgnoringQueryFindsAConceptTheDateFilteredOneHides() {
		PaymentConcept closed = repository.save(admission("Ficha 2026", ON_DATE.minusDays(30), ON_DATE.minusDays(1)));

		assertThat(dateFiltered(ON_DATE)).isEmpty();
		assertThat(withoutWindow()).extracting(PaymentConcept::getName).containsExactly(closed.getName());
	}

	@Test
	void aConceptThatHasNotOpenedYetIsAlsoFoundByTheWindowIgnoringQuery() {
		repository.save(admission("Ficha 2027", ON_DATE.plusDays(1), ON_DATE.plusDays(60)));

		assertThat(dateFiltered(ON_DATE)).isEmpty();
		assertThat(withoutWindow()).hasSize(1);
	}

	/**
	 * The two bounds are inclusive, so the first and last day of the window are
	 * both payable. Getting this wrong by a day would reject applicants on the
	 * closing date, which is the day most of them actually show up.
	 */
	@Test
	void theWindowBoundsAreInclusiveOnBothEnds() {
		repository.save(admission("Ficha 2026", ON_DATE, ON_DATE));

		assertThat(dateFiltered(ON_DATE)).hasSize(1);
		assertThat(dateFiltered(ON_DATE.minusDays(1))).isEmpty();
		assertThat(dateFiltered(ON_DATE.plusDays(1))).isEmpty();
	}

	/** A null bound means "no limit on that side", not "never payable". */
	@Test
	void aConceptWithoutDatesIsAlwaysPayable() {
		repository.save(admission("Ficha sin fechas", null, null));

		assertThat(dateFiltered(ON_DATE)).hasSize(1);
		assertThat(dateFiltered(ON_DATE.plusYears(5))).hasSize(1);
		assertThat(withoutWindow()).hasSize(1);
	}

	@Test
	void aHalfOpenWindowOnlyBoundsTheSideItSets() {
		repository.save(admission("Desde 2026", ON_DATE, null));
		repository.save(admission("Hasta 2026", null, ON_DATE));

		assertThat(dateFiltered(ON_DATE)).hasSize(2);
		assertThat(dateFiltered(ON_DATE.minusDays(1))).extracting(PaymentConcept::getName)
				.containsExactly("Hasta 2026");
		assertThat(dateFiltered(ON_DATE.plusDays(1))).extracting(PaymentConcept::getName)
				.containsExactly("Desde 2026");
	}

	// ── the filters both queries share ──

	/**
	 * The behaviour change, pinned deliberately. The lookup used to require
	 * {@code is_tuition}, so a program's other ACTIVE ADMISSION concepts (campus
	 * fee, materials) stayed out of the ficha price and one extra concept could not
	 * make it ambiguous. The flag is gone: an ADMISSION concept belonging to the
	 * program is a candidate whatever the flag says, and "does this look like the
	 * admission ticket" is now the TYPE's job alone.
	 */
	@Test
	void anAdmissionConceptIsIncludedWhateverTheTuitionFlagSays() {
		repository.save(pricedConcept("Materiales", PaymentConceptType.ADMISSION, false, null, null));
		repository.save(pricedConcept("Inscripción", PaymentConceptType.ADMISSION, true, null, null));

		assertThat(withoutWindow()).extracting(PaymentConcept::getName)
				.containsExactlyInAnyOrder("Materiales", "Inscripción");
		assertThat(dateFiltered(ON_DATE)).hasSize(2);
	}

	/**
	 * The regression guard for wiring the admission flow to its own type, now the
	 * ONLY thing separating the admission fee from the semester quota. An
	 * {@code ENROLLMENT} concept must never be priced as the ficha even when it is
	 * active and attached to the same program. This is what would break silently
	 * if the lookups were ever pointed back at {@code ENROLLMENT} — and with the
	 * tuition predicate gone, nothing else stands between the two.
	 */
	@Test
	void aTuitionOfTheEnrollmentTypeIsNotTheAdmissionFee() {
		repository.save(pricedConcept("Inscripción semestre", PaymentConceptType.ENROLLMENT, true, null, null));

		assertThat(withoutWindow()).isEmpty();
		assertThat(dateFiltered(ON_DATE)).isEmpty();
	}

	@Test
	void aTuitionOfAnotherTypeIsExcluded() {
		repository.save(pricedConcept("Reinscripción", PaymentConceptType.REINSCRIPTION, true, null, null));

		assertThat(withoutWindow()).isEmpty();
	}

	@Test
	void anInactiveConceptIsExcluded() {
		PaymentConcept deactivated = repository.save(admission("Inscripción", null, null));
		deactivated.deactivate();
		repository.save(deactivated);

		assertThat(withoutWindow()).isEmpty();
	}

	/**
	 * The scope rule, now the only one. There is no list of programs on the
	 * concept any more: a rate bound to another program, or to a level this
	 * program does not have, does not make the concept apply here. A general
	 * rate is added afterwards to prove the exclusion was about scope and not
	 * about the concept having no rates at all.
	 */
	@Test
	void aConceptWhoseOnlyRatesPointElsewhereDoesNotApply() {
		PaymentConcept concept = repository.save(new PaymentConcept("Inscripción", "Descripcion", "Politicas",
				PaymentConceptType.ADMISSION, true, false, 1, 2, true, null, null, null, null, false, null, false,
				false, null, List.of()));
		rateFor(concept, OTHER_PROGRAM, null, LocalDate.of(2000, 1, 1));
		rateFor(concept, null, AcademicLevel.POSGRADO, LocalDate.of(2000, 1, 1));

		assertThat(withoutWindow()).isEmpty();
		assertThat(dateFiltered(ON_DATE)).isEmpty();

		// the same concept, now with a rate that does reach this program
		rateFor(concept, PROGRAM, null, LocalDate.of(2000, 1, 1));
		assertThat(withoutWindow()).hasSize(1);
	}

	/**
	 * A rate that has expired, or not opened yet, does not make the concept
	 * apply on that date. The window-ignoring query drops the date predicates
	 * too, so it still sees the concept — which is what lets the resolver say
	 * "the ficha exists but its price is not valid today" instead of the vaguer
	 * "there is no ficha for this career".
	 */
	@Test
	void aRateOutOfItsOwnDateRangeDoesNotApplyOnThatDate() {
		PaymentConcept concept = repository.save(new PaymentConcept("Inscripción", "Descripcion", "Politicas",
				PaymentConceptType.ADMISSION, true, false, 1, 2, true, null, null, null, null, false, null, false,
				false, null, List.of()));
		rateFor(concept, PROGRAM, null, ON_DATE.plusDays(10));

		assertThat(dateFiltered(ON_DATE)).isEmpty();
		assertThat(withoutWindow()).hasSize(1);

		// the bounds are inclusive, so the first day of the rate's own range counts
		assertThat(dateFiltered(ON_DATE.plusDays(10))).hasSize(1);
	}

	/**
	 * Both come back, and that is now the documented consequence rather than an
	 * accident: the queries report faithfully and the resolver turns more than one
	 * into 409. A catalog carrying two ACTIVE ADMISSION concepts for one program
	 * used to have the tuition flag pick between them silently; it now has to
	 * retire one of them.
	 */
	@Test
	void twoAdmissionConceptsForOneProgramBothComeBackAndAreTheOnesThatBecome409() {
		repository.save(admission("Inscripción", null, null));
		repository.save(admission("Reinscripción 2026", null, null));

		// The count is deliberately not asserted to be 1: the repository's job is
		// to report faithfully, and FichaAmountResolver is what refuses.
		assertThat(withoutWindow()).hasSize(2);
		assertThat(dateFiltered(ON_DATE)).hasSize(2);
	}

	@Test
	void aProgramWithoutAnyAdmissionConceptYieldsNothing() {
		assertThat(withoutWindow()).isEmpty();
		assertThat(dateFiltered(ON_DATE)).isEmpty();
	}

	private List<PaymentConcept> withoutWindow() {
		return repository.findActiveTuitionForProgramIgnoringWindow(PaymentConceptStatus.ACTIVE,
				PaymentConceptType.ADMISSION, PROGRAM);
	}

	private List<PaymentConcept> dateFiltered(LocalDate onDate) {
		return repository.findActiveTuitionForProgram(PaymentConceptStatus.ACTIVE, PaymentConceptType.ADMISSION,
				PROGRAM, onDate);
	}

	/**
	 * An ADMISSION concept with the tuition flag on, saved together with the
	 * general rate that makes it apply to {@link #PROGRAM}. The flag is left on
	 * deliberately in most tests: it must not change the answer.
	 */
	private PaymentConcept admission(String name, LocalDate from, LocalDate until) {
		return pricedConcept(name, PaymentConceptType.ADMISSION, true, from, until);
	}

	/**
	 * Saves the concept and a general (neither program nor level) continuous
	 * rate covering every date, which is the minimum for a concept to apply to
	 * a program. Tests that care about scope use {@code rateFor} directly.
	 */
	private PaymentConcept pricedConcept(String name, PaymentConceptType type, boolean isTuition, LocalDate from,
			LocalDate until) {
		PaymentConcept concept = repository.save(new PaymentConcept(name, "Descripcion", "Politicas", type, isTuition,
				false, 1, 2, true, from, until, null, null, false, null, false, false, null, List.of()));
		rateFor(concept, null, null, LocalDate.of(2000, 1, 1));
		return concept;
	}

	/** A continuous rate, as the ladder requires: {@code periodId} stays null. */
	private void rateFor(PaymentConcept concept, UUID programId, AcademicLevel level, LocalDate validFrom) {
		rates.save(new PaymentRate(concept.getId(), programId, level, new BigDecimal("500.00"), null, validFrom));
	}
}
