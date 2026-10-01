package mx.edu.utez.sisa.admission.domain.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code §6} table, branch by branch.
 *
 * <p>This is the one place in the payment flow where a wrong answer costs something real,
 * and it is a pure function of what the bank said — no clock, no database, no
 * transaction. That is deliberate: the table used to be a private method behind a gateway
 * call, so its branches were only reachable by wiring mocks around a network-shaped
 * collaborator, and once the nightly sweep needed the same answers it was going to be
 * copied. Every branch is pinned here instead.
 */
class OrderSettlementDeciderTest {

	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001";

	private static final BigDecimal QUOTED = new BigDecimal("500.00");

	private EvoPaymentsGatewayPort.EvoOrderStatus status(String result, BigDecimal captured,
			String error) {
		return new EvoPaymentsGatewayPort.EvoOrderStatus(ORDER_ID, result, QUOTED, null, captured, null, null, null,
				null, error);
	}

	@Test
	void capturedMoneyIsCaptured() {
		assertThat(OrderSettlementDecider.decide(status("SUCCESS", QUOTED, null)))
				.isEqualTo(OrderSettlementDecider.Verdict.CAPTURED);
	}

	/**
	 * The rule the release endpoint and the confirmation both depend on, and the reason they
	 * are not allowed to disagree: money that arrived is a fact, {@code result} is a label.
	 * An order reporting {@code FAILURE} with a capture is a settled payment, and reading
	 * the label would hand out a place that was paid for.
	 */
	@Test
	void captureOutranksAFailureVerdict() {
		assertThat(OrderSettlementDecider.decide(status("FAILURE", QUOTED, "algo salió mal")))
				.isEqualTo(OrderSettlementDecider.Verdict.CAPTURED);
	}

	@Test
	void aFailureWithoutMoneyIsReleasable() {
		assertThat(OrderSettlementDecider.decide(status("FAILURE", null, null)))
				.isEqualTo(OrderSettlementDecider.Verdict.RELEASEABLE);
		assertThat(OrderSettlementDecider.decide(status("FAILURE", BigDecimal.ZERO, null)))
				.isEqualTo(OrderSettlementDecider.Verdict.RELEASEABLE);
	}

	/**
	 * An error node is a refusal whatever the verdict claims, which is how a bank that
	 * answers {@code PENDING} plus "rechazado" still gets its slot released.
	 */
	@Test
	void anErrorNodeWithoutMoneyIsReleasable() {
		assertThat(OrderSettlementDecider.decide(status("PENDING", null, "Pago rechazado")))
				.isEqualTo(OrderSettlementDecider.Verdict.RELEASEABLE);
	}

	/**
	 * {@code SUCCESS} with nothing captured is the case §6 declines to diagnose.
	 *
	 * <p>It is not a payment, but it is not a refusal either, and guessing which it is
	 * would either release a place whose money may still arrive or hold one that the bank
	 * will never pay. It is reported as its own answer so the sweep asks again tomorrow.
	 */
	@Test
	void aSuccessWithNothingCapturedIsHeld() {
		assertThat(OrderSettlementDecider.decide(status("SUCCESS", null, null)))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
		assertThat(OrderSettlementDecider.decide(status("SUCCESS", BigDecimal.ZERO, null)))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
	}

	@Test
	void aPendingOrderIsHeld() {
		assertThat(OrderSettlementDecider.decide(status("PENDING", null, null)))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
	}

	/**
	 * A verdict this code has never heard of is held, never released.
	 *
	 * <p>The table is a whitelist, and that asymmetry is the whole safety property: an
	 * unknown answer can cost a career one place for a day, while treating it as a refusal
	 * can cost it a real oversell.
	 */
	@Test
	void anUnrecognisedVerdictIsHeldRatherThanReleased() {
		assertThat(OrderSettlementDecider.decide(status("ALGO_NUEVO", null, null)))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
		assertThat(OrderSettlementDecider.decide(status(null, null, null)))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
	}

	/**
	 * A blank error string is not an error. {@code hasError()} already checks for it, and
	 * the table depends on that: treating "" as a refusal would release places on orders
	 * that merely omitted the field.
	 */
	@Test
	void aBlankErrorIsNotARefusal() {
		assertThat(OrderSettlementDecider.decide(status("PENDING", null, "   ")))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
	}
}