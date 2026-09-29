package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rate ladder: given every rate of a concept that could price a program,
 * the one that wins is the one bound to that program, else the one bound to its
 * level, else the general one.
 *
 * <p>Pure unit test on purpose. The SQL that FETCHES the candidates is a single
 * OR over the three rungs, and the choice between the rows it returns is the
 * rule these tests exist to pin — encoding it as a {@code ORDER BY} would make
 * it invisible to a unit test and only observable against a real database. The
 * comparator is therefore the seam.
 *
 * <p>The query is not tested here at all. What the ORs match — specifically
 * that a program with no level matches no level rung — is
 * {@code PaymentRateLookupJpaRepositoryIT}'s job.
 */
class PaymentConceptQueryAdapterTest {

	private static final UUID PROGRAM_ID = UUID.randomUUID();

	private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

	/** Bound to the program. */
	private static PaymentRate forProgram(String amount) {
		return new PaymentRate(UUID.randomUUID(), PROGRAM_ID, null, new BigDecimal(amount), null, FROM);
	}

	/** Bound to the program's level, not to the program itself. */
	private static PaymentRate forLevel(AcademicLevel level, String amount) {
		return new PaymentRate(UUID.randomUUID(), null, level, new BigDecimal(amount), null, FROM);
	}

	/** Bound to neither. */
	private static PaymentRate general(String amount) {
		return new PaymentRate(UUID.randomUUID(), null, null, new BigDecimal(amount), null, FROM);
	}

	@Test
	void theRateForTheProgramBeatsEverythingElse() {
		Optional<BigDecimal> amount = PaymentConceptQueryAdapter.pickAmount(List.of(
				general("1000.00"),
				forLevel(AcademicLevel.TSU, "1200.00"),
				forProgram("1578.00")));

		assertThat(amount).contains(new BigDecimal("1578.00"));
	}

	@Test
	void theLevelRateBeatsTheGeneralOne() {
		Optional<BigDecimal> amount = PaymentConceptQueryAdapter
				.pickAmount(List.of(general("1000.00"), forLevel(AcademicLevel.TSU, "1200.00")));

		assertThat(amount).contains(new BigDecimal("1200.00"));
	}

	@Test
	void theGeneralRateIsUsedWhenItIsTheOnlyCandidate() {
		assertThat(PaymentConceptQueryAdapter.pickAmount(List.of(general("1000.00"))))
				.contains(new BigDecimal("1000.00"));
	}

	@Test
	void precedenceDoesNotDependOnTheOrderTheCandidatesArriveIn() {
		// The query has no ORDER BY, so the same three rows can come back in any
		// order. A comparator that only worked on one of them would be a bug that
		// only shows up under whatever plan MySQL happened to pick.
		List<PaymentRate> candidates = List.of(
				forLevel(AcademicLevel.TSU, "1200.00"),
				forProgram("1578.00"),
				general("1000.00"));

		assertThat(PaymentConceptQueryAdapter.pickAmount(candidates)).contains(new BigDecimal("1578.00"));
		assertThat(PaymentConceptQueryAdapter.pickAmount(
				List.of(candidates.get(0), candidates.get(2), candidates.get(1))))
						.contains(new BigDecimal("1578.00"));
		assertThat(PaymentConceptQueryAdapter.pickAmount(
				List.of(candidates.get(2), candidates.get(1), candidates.get(0))))
						.contains(new BigDecimal("1578.00"));
	}

	@Test
	void withinOneRungTheMostRecentlyOpenedRateWins() {
		// SetPaymentRateUseCase closes the previous row when it opens a new one, so
		// a clean catalog never has two open rows on the same rung. This pins what
		// happens when it does: the later author had the last word, and picking
		// arbitrarily between two open prices is not an option.
		PaymentRate older = new PaymentRate(UUID.randomUUID(), PROGRAM_ID, null, new BigDecimal("1500.00"), null,
				LocalDate.of(2026, 1, 1));
		PaymentRate newer = new PaymentRate(UUID.randomUUID(), PROGRAM_ID, null, new BigDecimal("1578.00"), null,
				LocalDate.of(2026, 9, 1));

		assertThat(PaymentConceptQueryAdapter.pickAmount(List.of(older, newer)))
				.contains(new BigDecimal("1578.00"));
		assertThat(PaymentConceptQueryAdapter.pickAmount(List.of(newer, older)))
				.contains(new BigDecimal("1578.00"));
	}

	@Test
	void aMoreSpecificRungWinsEvenIfItIsOlder() {
		// Specificity is about WHO the price is for, not WHEN it was set. A
		// program-specific rate from January must not lose to a general rate
		// written yesterday.
		PaymentRate olderSpecific = new PaymentRate(UUID.randomUUID(), PROGRAM_ID, null, new BigDecimal("1500.00"),
				null, LocalDate.of(2026, 1, 1));
		PaymentRate newerGeneral = new PaymentRate(UUID.randomUUID(), null, null, new BigDecimal("2000.00"), null,
				LocalDate.of(2026, 9, 1));

		assertThat(PaymentConceptQueryAdapter.pickAmount(List.of(newerGeneral, olderSpecific)))
				.contains(new BigDecimal("1500.00"));
	}

	@Test
	void noCandidatesMeansNoPriceAndNoGuess() {
		// Empty has to stay empty all the way to the caller: this is the signal
		// that makes FichaAmountResolver fail instead of pricing from anywhere else.
		assertThat(PaymentConceptQueryAdapter.pickAmount(List.of())).isEmpty();
	}
}
