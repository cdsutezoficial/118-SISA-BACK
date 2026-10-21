package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Discount selection (R5/R6): window, anchor, and the "most anchored then
 * newest" tie-break.
 */
class BenefitResolverTest {

	private final BenefitResolver resolver = new BenefitResolver();

	private static final LocalDate ON_DATE = LocalDate.of(2026, 10, 19);

	@Test
	void emptyCandidates_resolveToEmpty() {
		assertThat(resolver.resolve(List.of(), ON_DATE, null, null)).isEmpty();
	}

	@Test
	void benefitNotYetValid_isIgnored() {
		PaymentBenefit future = benefit(null, null, LocalDate.of(2027, 1, 1), null, "2026-01-01T00:00:00Z");

		assertThat(resolver.resolve(List.of(future), ON_DATE, null, null)).isEmpty();
	}

	@Test
	void expiredBenefit_isIgnored() {
		PaymentBenefit expired = benefit(null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30),
				"2026-01-01T00:00:00Z");

		assertThat(resolver.resolve(List.of(expired), ON_DATE, null, null)).isEmpty();
	}

	@Test
	void mostAnchoredBenefitWins() {
		UUID periodId = UUID.randomUUID();
		UUID generationId = UUID.randomUUID();
		PaymentBenefit unanchored = benefit(null, null, LocalDate.of(2026, 1, 1), null, "2026-01-01T00:00:00Z");
		PaymentBenefit pinned = benefit(periodId, generationId, LocalDate.of(2026, 1, 1), null,
				"2026-02-01T00:00:00Z");

		Optional<PaymentBenefit> resolved = resolver.resolve(List.of(unanchored, pinned), ON_DATE, periodId,
				generationId);

		assertThat(resolved).contains(pinned);
	}

	@Test
	void equalAnchoring_breaksTieByNewestCreatedAt() {
		PaymentBenefit older = benefit(null, null, LocalDate.of(2026, 1, 1), null, "2026-01-01T00:00:00Z");
		PaymentBenefit newer = benefit(null, null, LocalDate.of(2026, 1, 1), null, "2026-05-01T00:00:00Z");

		Optional<PaymentBenefit> resolved = resolver.resolve(List.of(older, newer), ON_DATE, null, null);

		assertThat(resolved).contains(newer);
	}

	@Test
	void anchoredBenefitDoesNotMatchAnUnanchoredQuote() {
		UUID periodId = UUID.randomUUID();
		PaymentBenefit pinned = benefit(periodId, null, LocalDate.of(2026, 1, 1), null, "2026-01-01T00:00:00Z");

		assertThat(resolver.resolve(List.of(pinned), ON_DATE, null, null)).isEmpty();
	}

	private static PaymentBenefit benefit(UUID periodId, UUID generationId, LocalDate from, LocalDate to,
			String createdAt) {
		return new PaymentBenefit(UUID.randomUUID(), null, "CONCEPT", periodId, generationId, BenefitMode.PERCENT,
				new java.math.BigDecimal("50.00"), null, from, to, "reason", UUID.randomUUID(), "Finanzas",
				Instant.parse(createdAt));
	}
}
