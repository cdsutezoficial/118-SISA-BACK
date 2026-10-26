package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure cotizador rules (R3): rounding to MXN cents, the three benefit modes,
 * and the FULL/AMOUNT cap that keeps {@code amountDue} from going negative.
 */
class PaymentQuoterTest {

	private final PaymentQuoter quoter = new PaymentQuoter();

	@Test
	void noBenefit_returnsRateAsDue() {
		PaymentQuoter.Quotation q = quoter.quote(new BigDecimal("1000.00"), null);

		assertThat(q.original()).isEqualByComparingTo("1000.00");
		assertThat(q.discount()).isEqualByComparingTo("0.00");
		assertThat(q.due()).isEqualByComparingTo("1000.00");
		assertThat(q.benefit()).isNull();
		assertThat(q.exempt()).isFalse();
	}

	@Test
	void percent_halvesTheRate() {
		PaymentQuoter.Quotation q = quoter.quote(new BigDecimal("1000.00"), percent("50.00"));

		assertThat(q.discount()).isEqualByComparingTo("500.00");
		assertThat(q.due()).isEqualByComparingTo("500.00");
		assertThat(q.exempt()).isFalse();
	}

	@Test
	void percent_roundsHalfUpToCents() {
		PaymentQuoter.Quotation q = quoter.quote(new BigDecimal("100.00"), percent("33.33"));

		assertThat(q.discount()).isEqualByComparingTo("33.33");
		assertThat(q.due()).isEqualByComparingTo("66.67");
	}

	@Test
	void amount_appliesFixedDiscount() {
		PaymentQuoter.Quotation q = quoter.quote(new BigDecimal("1000.00"), amount("300.00"));

		assertThat(q.discount()).isEqualByComparingTo("300.00");
		assertThat(q.due()).isEqualByComparingTo("700.00");
	}

	@Test
	void amount_largerThanRateIsCappedAndExempt() {
		PaymentQuoter.Quotation q = quoter.quote(new BigDecimal("1000.00"), amount("5000.00"));

		assertThat(q.discount()).isEqualByComparingTo("1000.00");
		assertThat(q.due()).isEqualByComparingTo("0.00");
		assertThat(q.exempt()).isTrue();
	}

	@Test
	void full_zerosTheRateAndIsExempt() {
		PaymentQuoter.Quotation q = quoter.quote(new BigDecimal("1234.56"), full());

		assertThat(q.discount()).isEqualByComparingTo("1234.56");
		assertThat(q.due()).isEqualByComparingTo("0.00");
		assertThat(q.exempt()).isTrue();
	}

	private static PaymentBenefit percent(String value) {
		return new PaymentBenefit(UUID.randomUUID(), null, "CONCEPT", null, null, BenefitMode.PERCENT,
				new BigDecimal(value), null, LocalDate.of(2026, 1, 1), null, "reason", UUID.randomUUID(), "Finanzas",
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	private static PaymentBenefit amount(String value) {
		return new PaymentBenefit(UUID.randomUUID(), null, "CONCEPT", null, null, BenefitMode.AMOUNT, null,
				new BigDecimal(value), LocalDate.of(2026, 1, 1), null, "reason", UUID.randomUUID(), "Finanzas",
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	private static PaymentBenefit full() {
		return new PaymentBenefit(UUID.randomUUID(), null, "CONCEPT", null, null, BenefitMode.FULL, null, null,
				LocalDate.of(2026, 1, 1), null, "reason", UUID.randomUUID(), "Finanzas",
				Instant.parse("2026-01-01T00:00:00Z"));
	}
}
