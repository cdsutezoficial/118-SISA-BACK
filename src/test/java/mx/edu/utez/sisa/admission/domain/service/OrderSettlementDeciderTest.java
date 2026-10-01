package mx.edu.utez.sisa.admission.domain.service;

import java.math.BigDecimal;
import java.util.List;

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

	// ── §6bis: the fold, which is what the quota is actually allowed to act on ──

	@Test
	void aFichaWithNothingToInspectIsHeldRatherThanReleased() {
		assertThat(OrderSettlementDecider.decideFicha(List.of()))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
	}

	@Test
	void aSingleVerdictIsItsOwnFold() {
		assertThat(OrderSettlementDecider.decideFicha(List.of(OrderSettlementDecider.Verdict.CAPTURED)))
				.isEqualTo(OrderSettlementDecider.Verdict.CAPTURED);
		assertThat(OrderSettlementDecider.decideFicha(List.of(OrderSettlementDecider.Verdict.RELEASEABLE)))
				.isEqualTo(OrderSettlementDecider.Verdict.RELEASEABLE);
		assertThat(OrderSettlementDecider.decideFicha(List.of(OrderSettlementDecider.Verdict.HELD_UNKNOWN)))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
	}

	/**
	 * The defect this fold exists for, reduced to three words.
	 *
	 * <p>An attempt the bank refused and a sibling attempt that captured, same ficha, same
	 * night. Decided in the order the sweep happened to find them, the refusal released the
	 * place and the capture then paid for it: a sold place given away and a paid applicant
	 * left without one. Read as a group it is {@code CAPTURED}, which is the whole truth.
	 */
	@Test
	void aCaptureAmongRefusedSiblingsIsCaptured() {
		assertThat(OrderSettlementDecider.decideFicha(List.of(OrderSettlementDecider.Verdict.RELEASEABLE,
				OrderSettlementDecider.Verdict.CAPTURED, OrderSettlementDecider.Verdict.RELEASEABLE)))
				.isEqualTo(OrderSettlementDecider.Verdict.CAPTURED);
	}

	/**
	 * The mirror image, and the one that used to be the sweep's normal answer: an order
	 * nobody could rule on makes the whole group undecidable, so a refusal we did
	 * understand stays unacted on. A place held a day is the cheap direction.
	 */
	@Test
	void anUnansweredSiblingHoldsTheWholeFicha() {
		assertThat(OrderSettlementDecider.decideFicha(List.of(OrderSettlementDecider.Verdict.RELEASEABLE,
				OrderSettlementDecider.Verdict.HELD_UNKNOWN)))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
	}

	@Test
	void onlyWhenEverySiblingIsRefusedDoesThePlaceGoBack() {
		assertThat(OrderSettlementDecider.decideFicha(List.of(OrderSettlementDecider.Verdict.RELEASEABLE,
				OrderSettlementDecider.Verdict.RELEASEABLE, OrderSettlementDecider.Verdict.RELEASEABLE)))
				.isEqualTo(OrderSettlementDecider.Verdict.RELEASEABLE);
	}

	/** Order in the list is an accident of query order, never a fact about the money. */
	@Test
	void theFoldDoesNotDependOnTheOrderOfTheAnswers() {
		List<OrderSettlementDecider.Verdict> asked = List.of(OrderSettlementDecider.Verdict.HELD_UNKNOWN,
				OrderSettlementDecider.Verdict.RELEASEABLE);
		List<OrderSettlementDecider.Verdict> reversed = List.of(OrderSettlementDecider.Verdict.RELEASEABLE,
				OrderSettlementDecider.Verdict.HELD_UNKNOWN);

		assertThat(OrderSettlementDecider.decideFicha(asked))
				.isEqualTo(OrderSettlementDecider.decideFicha(reversed))
				.isEqualTo(OrderSettlementDecider.Verdict.HELD_UNKNOWN);
	}
}