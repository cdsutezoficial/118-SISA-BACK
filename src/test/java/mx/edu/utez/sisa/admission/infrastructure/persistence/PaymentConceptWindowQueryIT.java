package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB coverage for the two window lookups in
 * {@link PaymentConceptLookupJpaRepository}, in the style of
 * {@code ProgramAdmissionConfigOptionsQueryIT}.
 *
 * <p>These are the queries block 3 of the plan hinges on, and both are pure JPQL
 * over {@code payment_concept}: a unit test with a mocked repository returns
 * whatever the mock was told to return and would pass even if the query
 * filtered on the wrong column. The distinction that matters — the date-filtered
 * query excludes a closed concept, the window-ignoring one still finds it — is
 * only observable when the predicates are actually parsed and executed.
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
		PaymentConcept closed = repository.save(tuition("Ficha 2026", ON_DATE.minusDays(30), ON_DATE.minusDays(1),
				PROGRAM));

		assertThat(dateFiltered(ON_DATE)).isEmpty();
		assertThat(withoutWindow()).extracting(PaymentConcept::getName).containsExactly(closed.getName());
	}

	@Test
	void aConceptThatHasNotOpenedYetIsAlsoFoundByTheWindowIgnoringQuery() {
		repository.save(tuition("Ficha 2027", ON_DATE.plusDays(1), ON_DATE.plusDays(60), PROGRAM));

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
		repository.save(tuition("Ficha 2026", ON_DATE, ON_DATE, PROGRAM));

		assertThat(dateFiltered(ON_DATE)).hasSize(1);
		assertThat(dateFiltered(ON_DATE.minusDays(1))).isEmpty();
		assertThat(dateFiltered(ON_DATE.plusDays(1))).isEmpty();
	}

	/** A null bound means "no limit on that side", not "never payable". */
	@Test
	void aConceptWithoutDatesIsAlwaysPayable() {
		repository.save(tuition("Ficha sin fechas", null, null, PROGRAM));

		assertThat(dateFiltered(ON_DATE)).hasSize(1);
		assertThat(dateFiltered(ON_DATE.plusYears(5))).hasSize(1);
		assertThat(withoutWindow()).hasSize(1);
	}

	@Test
	void aHalfOpenWindowOnlyBoundsTheSideItSets() {
		repository.save(tuition("Desde 2026", ON_DATE, null, PROGRAM));
		repository.save(tuition("Hasta 2026", null, ON_DATE, PROGRAM));

		assertThat(dateFiltered(ON_DATE)).hasSize(2);
		assertThat(dateFiltered(ON_DATE.minusDays(1))).extracting(PaymentConcept::getName)
				.containsExactly("Hasta 2026");
		assertThat(dateFiltered(ON_DATE.plusDays(1))).extracting(PaymentConcept::getName)
				.containsExactly("Desde 2026");
	}

	// ── the filters both queries share ──

	/**
	 * {@code isTuition} is what keeps a program's other active enrollment
	 * concepts (campus fee, materials) out of the ficha price. Without it a
	 * single extra concept would make the ficha ambiguous, and the applicant
	 * would be told to contact support.
	 */
	@Test
	void anActiveEnrollmentConceptThatIsNotTheTuitionIsExcluded() {
		repository.save(newConcept("Materiales", PaymentConceptType.ENROLLMENT, false, false, null, null, PROGRAM));
		repository.save(tuition("Inscripción", ON_DATE.minusDays(10), ON_DATE.plusDays(10), PROGRAM));

		assertThat(withoutWindow()).extracting(PaymentConcept::getName).containsExactly("Inscripción");
	}

	@Test
	void aTuitionOfAnotherTypeIsExcluded() {
		repository.save(newConcept("Reinscripción", PaymentConceptType.REINSCRIPTION, true, false, null, null,
				PROGRAM));

		assertThat(withoutWindow()).isEmpty();
	}

	@Test
	void anInactiveTuitionIsExcluded() {
		PaymentConcept deactivated = repository.save(tuition("Inscripción", null, null, PROGRAM));
		deactivated.deactivate();
		repository.save(deactivated);

		assertThat(withoutWindow()).isEmpty();
	}

	@Test
	void aTuitionBelongingToAnotherProgramIsExcluded() {
		repository.save(tuition("Inscripción", null, null, OTHER_PROGRAM));

		assertThat(withoutWindow()).isEmpty();
	}

	@Test
	void aProgramCanCarrySeveralTuitionConceptsAndBothQueriesSeeThemAll() {
		repository.save(tuition("Inscripción", null, null, PROGRAM));
		repository.save(tuition("Reinscripción 2026", null, null, PROGRAM));

		// The count is deliberately not asserted to be 1: the resolver turns >1
		// into a 409, and the repository's job is only to report faithfully.
		assertThat(withoutWindow()).hasSize(2);
		assertThat(dateFiltered(ON_DATE)).hasSize(2);
	}

	@Test
	void aProgramWithoutAnyTuitionConceptYieldsNothing() {
		assertThat(withoutWindow()).isEmpty();
		assertThat(dateFiltered(ON_DATE)).isEmpty();
	}

	private List<PaymentConcept> withoutWindow() {
		return repository.findActiveTuitionForProgramIgnoringWindow(PaymentConceptStatus.ACTIVE,
				PaymentConceptType.ENROLLMENT, PROGRAM);
	}

	private List<PaymentConcept> dateFiltered(LocalDate onDate) {
		return repository.findActiveTuitionForProgram(PaymentConceptStatus.ACTIVE, PaymentConceptType.ENROLLMENT,
				PROGRAM, onDate);
	}

	private static PaymentConcept tuition(String name, LocalDate from, LocalDate until, UUID... programIds) {
		return newConcept(name, PaymentConceptType.ENROLLMENT, true, false, from, until, programIds);
	}

	private static PaymentConcept newConcept(String name, PaymentConceptType type, boolean isTuition,
			boolean isStandalone, LocalDate from, LocalDate until, UUID... programIds) {
		return new PaymentConcept(name, "Descripcion", "Politicas", type, isTuition, isStandalone, 1, 2, true, from,
				until, null, null, false, null, false, false, null, List.of(), List.of(programIds));
	}
}
