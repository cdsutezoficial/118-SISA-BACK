package mx.edu.utez.sisa.admission.domain.service;

import java.util.List;

import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;

/**
 * The {@code §6} table, written once.
 *
 * <p>Two callers need it and they need it to agree: the browser's give-up path
 * ({@link ReleaseFichaPaymentSlotUseCaseImpl}) and the nightly sweep
 * ({@link ReconcileFichaPaymentsUseCaseImpl}). Before it was a private method in the
 * release use case, which is fine right up to the moment a second caller appears and
 * the table has to be copied — and a copy is where "the sweep releases on SUCCESS"
 * would eventually creep in.
 *
 * <p>It is a pure function of what the bank said. No clock, no repositories, no
 * transaction, so the part of the system where money can be lost is testable without
 * a database and every branch is reachable in a test rather than at 00:05.
 *
 * <p>Three answers, because the callers act differently and the differences matter:
 * <ul>
 * <li>{@link Verdict#CAPTURED} — money moved. Never release, never doubt. The ficha
 * becomes {@code PAID} and the attempt closes as {@code CAPTURED}.</li>
 * <li>{@link Verdict#RELEASEABLE} — the bank definitively refused and took nothing.
 * The slot goes back and the attempt closes as {@code REJECTED}.</li>
 * <li>{@link Verdict#HELD_UNKNOWN} — {@code SUCCESS} with nothing captured, or
 * {@code PENDING}, or an unrecognised verdict. The slot stays and the attempt stays
 * <b>open</b>, because closing it on "we don't know yet" is the single way to lose a
 * capture that landed a second later.</li>
 * </ul>
 *
 * <p>Capture outranks {@code result}, and deliberately so: money that arrived is a
 * fact, {@code SUCCESS} is only a label. An order that reports {@code FAILURE} and
 * still captured is a settled payment, and treating the label as the truth would hand
 * out a place that was paid for.
 *
 * <p>Those three answers are about <em>one order</em>, and one ficha can own several
 * open orders at once, so they are not yet a quota decision.
 * {@link #decideFicha(List)} folds them into the one answer the ficha's place is allowed
 * to act on, and both callers now write only from that fold.
 *
 * <p>{@code status} itself is read but {@code status} is a port type, so this stays a
 * pure decision — nothing here writes, and nothing here decides what a "SUCCESS with no
 * capture" <em>means</em>, only that we refuse to act on it.
 */
public final class OrderSettlementDecider {

	private OrderSettlementDecider() {
		// Utility: the table has no state and must not acquire any.
	}

	/**
	 * @param status what the bank answered for one order; never null (the adapter
	 *               turns an unusable answer into an exception before it gets here)
	 * @return what may be done with this order
	 */
	public static Verdict decide(EvoPaymentsGatewayPort.EvoOrderStatus status) {
		if (status.capturedAny()) {
			return Verdict.CAPTURED;
		}
		if (status.hasError() || "FAILURE".equals(status.result())) {
			return Verdict.RELEASEABLE;
		}
		if ("SUCCESS".equals(status.result())) {
			return Verdict.HELD_UNKNOWN;
		}
		return Verdict.HELD_UNKNOWN;
	}

	/**
	 * What one <em>ficha's</em> attempts mean for the quota, folded.
	 *
	 * <p>The per-order table above is not a quota decision on its own, and this is why.
	 * {@code AdmissionPayment#orderId} is overwritten on every retry, so one ficha can
	 * own several attempts at once — the applicant opened a checkout, went back, opened
	 * another — and the money belongs to the <em>ficha</em>, not to any single attempt.
	 * Deciding attempt by attempt let an older {@code FAILURE} hand the place back while a
	 * sibling attempt was capturing, which produced a paid ficha holding no slot: the
	 * exact oversell the whole mechanism exists to prevent, rebuilt one layer up.
	 *
	 * <p>So every caller that can write to the quota asks the bank about <em>all</em> the
	 * open attempts of a ficha first, and only then acts on the fold:
	 * <ul>
	 * <li>any {@link Verdict#CAPTURED} → {@link Verdict#CAPTURED}. Money arrived. The
	 * place is hers, and no amount of refusals elsewhere may take it.</li>
	 * <li>otherwise any {@link Verdict#HELD_UNKNOWN} → {@link Verdict#HELD_UNKNOWN}. One
	 * unanswered order makes the whole picture incomplete, and a refusal we understood
	 * must not be acted on while a sibling may yet capture.</li>
	 * <li>all {@link Verdict#RELEASEABLE} → {@link Verdict#RELEASEABLE}. Nothing is in
	 * flight for this ficha, and the place goes back exactly once.</li>
	 * </ul>
	 *
	 * <p>Capture outranks uncertainty outranks refusal, which is the same asymmetry as
	 * {@link #decide} one level up: each step refuses to trade an irreversible mistake for
	 * a slot held a little longer.
	 *
	 * <p>An empty group is {@link Verdict#HELD_UNKNOWN}, not {@link Verdict#RELEASEABLE}.
	 * "Nothing to inspect" and "nothing in flight" are different facts, and reading the
	 * first as the second is how a place goes back that nobody ever asked about.
	 *
	 * @param verdicts one verdict per open attempt of the ficha, in any order
	 * @return what may be done with this ficha's quota as a whole
	 */
	public static Verdict decideFicha(List<Verdict> verdicts) {
		if (verdicts.isEmpty()) {
			return Verdict.HELD_UNKNOWN;
		}
		if (verdicts.contains(Verdict.CAPTURED)) {
			return Verdict.CAPTURED;
		}
		if (verdicts.contains(Verdict.HELD_UNKNOWN)) {
			return Verdict.HELD_UNKNOWN;
		}
		return Verdict.RELEASEABLE;
	}

	/**
	 * What one {@code Retrieve Order} means for the quota.
	 *
	 * <p>{@link #RELEASEABLE} and {@link #CAPTURED} are terminal for the attempt. {@link
	 * #HELD_UNKNOWN} is the honest third option and the reason this enum exists: it is
	 * not a failure, it is an absence of an answer, and it has to be
	 * distinguishable from a refusal so neither caller treats it as one.
	 */
	public enum Verdict {

		/** Money moved: the ficha is paid and its place is hers for good. */
		CAPTURED,

		/** The bank refused and took nothing: the place goes back. */
		RELEASEABLE,

		/** Nothing conclusive. Hold the place, leave the attempt open, ask again later. */
		HELD_UNKNOWN
	}
}