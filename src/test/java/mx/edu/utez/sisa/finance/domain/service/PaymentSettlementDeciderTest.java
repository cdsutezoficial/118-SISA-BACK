package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settlement rule (R7): the abono total against the discounted amount due maps
 * to QUOTED / PARTIAL / PAID. EXEMPT is never an output — it is decided at
 * creation, not by an abono.
 */
class PaymentSettlementDeciderTest {

	private final PaymentSettlementDecider decider = new PaymentSettlementDecider();

	@Test
	void nothingPaid_isQuoted() {
		assertThat(decider.decide(new BigDecimal("1000.00"), new BigDecimal("0.00")))
				.isEqualTo(PaymentStatus.QUOTED);
	}

	@Test
	void partialPaid_isPartial() {
		assertThat(decider.decide(new BigDecimal("1000.00"), new BigDecimal("300.00")))
				.isEqualTo(PaymentStatus.PARTIAL);
	}

	@Test
	void fullyPaid_isPaid() {
		assertThat(decider.decide(new BigDecimal("1000.00"), new BigDecimal("1000.00")))
				.isEqualTo(PaymentStatus.PAID);
	}

	@Test
	void overpaid_isPaid() {
		assertThat(decider.decide(new BigDecimal("1000.00"), new BigDecimal("1200.00")))
				.isEqualTo(PaymentStatus.PAID);
	}
}
