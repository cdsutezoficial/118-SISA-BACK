package mx.edu.utez.sisa.finance.domain.model;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity invariant: an adeudo is born with zero paid and only settles through
 * the provided transitions — the abono total is assigned, never incremented.
 */
class PaymentTest {

	@Test
	void newPayment_startsWithZeroPaid() {
		Payment payment = payment(PaymentStatus.QUOTED);

		assertThat(payment.getAmountPaid()).isEqualByComparingTo("0.00");
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.QUOTED);
	}

	@Test
	void settle_adoptsTotalAndStatus() {
		Payment payment = payment(PaymentStatus.QUOTED);

		payment.settle(new BigDecimal("500.00"), PaymentStatus.PARTIAL);

		assertThat(payment.getAmountPaid()).isEqualByComparingTo("500.00");
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL);
	}

	@Test
	void cancel_transitionsToCancelled() {
		Payment payment = payment(PaymentStatus.QUOTED);

		payment.cancel();

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
	}

	@Test
	void exemptDoesNotAcceptEntries_quotedDoes() {
		assertThat(payment(PaymentStatus.EXEMPT).getStatus().acceptsEntries()).isFalse();
		assertThat(payment(PaymentStatus.QUOTED).getStatus().acceptsEntries()).isTrue();
	}

	private static Payment payment(PaymentStatus status) {
		return new Payment(UUID.randomUUID(), null, "TUITION", PaymentConceptType.PERIODIC_QUOTA, null, null,
				UUID.randomUUID(), null, new BigDecimal("1000.00"), new BigDecimal("1000.00"), null, status,
				PaymentOrigin.CASHIER, null, Instant.parse("2026-10-01T00:00:00Z"), UUID.randomUUID());
	}
}
