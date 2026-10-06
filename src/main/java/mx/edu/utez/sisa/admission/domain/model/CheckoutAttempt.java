package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One attempt to pay a ficha at EVO, per
 * {@code decision-cupo-proceso-admision.md} §3.7 — the row that lets the system
 * answer "what happened to this payment?" without guessing.
 *
 * <p>It exists because {@code AdmissionPayment#orderId} cannot answer that. That
 * column is overwritten on every retry, so after a second attempt the first
 * order's id is simply gone; if that first order had captured, the money would
 * sit at the bank with no trace here. Attempts are therefore <b>appended</b>:
 * {@code OrderIdBuilder} regenerates the order id with a
 * {@code SecureRandom} suffix on every attempt, so no row can be overwritten
 * (§3.6) and the candidate's whole payment history stays queryable.
 *
 * <p>The row is opened <b>before</b> the gateway is called (§3.6), which is what
 * makes the ordering safe. Once Evo has an order, the money may be captured at
 * any moment, so a slot held without a recorded order id is a slot that can only
 * be released by guessing — the "no order number" hole that the old
 * "release after 30 minutes" rule existed to paper over. Open the row first and
 * that hole closes on its own: if the process dies at any step, the sweep finds
 * the attempt, asks the bank about a known {@code orderId}, and settles it.
 *
 * <p>Not an aggregate of {@link AdmissionPayment}: nothing here changes the
 * payment's status or its quota claim. It is the audit trail of an attempt, and
 * the two are written in the same short pre-gateway transaction by
 * {@code CheckoutSlotClaimer} — together so that a claim is never held without
 * its attempt row, nor the reverse.
 */
@Entity
@Table(name = "checkout_attempt")
public class CheckoutAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/**
	 * EVO {@code order.id} this attempt was opened with, unique because it is the
	 * only handle the bank accepts when the sweep goes looking for the money.
	 *
	 * <p>Unique is a real constraint rather than a convention: the id carries a
	 * random suffix precisely so retries differ, and a duplicate would mean two
	 * rows claiming to be the same order at the bank.
	 */
	@Column(name = "order_id", nullable = false, length = 64, unique = true)
	private String orderId;

	/** Bare FK to {@code Candidate}, the convention throughout this schema. */
	@Column(name = "candidate_id", nullable = false)
	private UUID candidateId;

	/**
	 * The live tariff this attempt asked for, snapshotted here at open time.
	 *
	 * <p>Copied from what was claimed rather than re-read later on purpose: the
	 * sweep compares the bank's capture against <em>this</em> number, which is
	 * the amount that was actually put in front of the applicant. Re-reading the
	 * ficha would compare a capture against a tariff that may have been edited
	 * in between.
	 */
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/**
	 * {@link CheckoutAttemptCloseReason#STARTED} while undecided, then the reason
	 * it closed — never null, so "still open" is a value that can be queried
	 * rather than the absence of one.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "close_reason", nullable = false, length = 32)
	private CheckoutAttemptCloseReason closeReason;

	/** When it closed; {@code null} while {@link #isOpen()}. */
	@Column(name = "closed_at")
	private Instant closedAt;

	protected CheckoutAttempt() {
		// JPA
	}

	/**
	 * @param orderId     the EVO order id this attempt will be known by; required
	 *                    non-blank because an attempt without one cannot be
	 *                    reconciled with the bank
	 * @param candidateId whose ficha is being paid
	 * @param amount      the live tariff quoted for this checkout
	 * @param createdAt   when the attempt was opened, from the injected
	 *                    {@code Clock} — a timestamp the caller decides, not one
	 *                    the model reaches for on its own
	 */
	public CheckoutAttempt(String orderId, UUID candidateId, BigDecimal amount, Instant createdAt) {
		this.orderId = Objects.requireNonNull(orderId, "El intento necesita su orderId");
		this.candidateId = Objects.requireNonNull(candidateId, "El intento necesita su candidato");
		this.amount = Objects.requireNonNull(amount, "El intento necesita su monto");
		this.createdAt = Objects.requireNonNull(createdAt, "El intento necesita su fecha de creación");
		this.closeReason = CheckoutAttemptCloseReason.STARTED;
	}

	/**
	 * Settles the attempt, once. Re-closing is a no-op on purpose: the sweep and
	 * the browser-reported timeout can both reach the same attempt, and the first
	 * answer is the one that was true.
	 *
	 * <p>That guard is also what stops a late timeout from overwriting a
	 * {@link CheckoutAttemptCloseReason#CAPTURED} that the applicant earned by
	 * paying. Losing the race must never cost them the slot.
	 *
	 * @param reason why the attempt is over; refusing {@link
	 *               CheckoutAttemptCloseReason#STARTED} here would claim to close
	 *               an attempt while leaving it open
	 * @param at     when, from the same injected {@code Clock}
	 */
	public void close(CheckoutAttemptCloseReason reason, Instant at) {
		Objects.requireNonNull(reason, "El intento necesita una razón de cierre");
		if (reason == CheckoutAttemptCloseReason.STARTED || !isOpen()) {
			return;
		}
		this.closeReason = reason;
		this.closedAt = Objects.requireNonNull(at, "El intento necesita su fecha de cierre");
	}

	/** Whether anything is still owed an answer from this attempt. */
	public boolean isOpen() {
		return this.closeReason == CheckoutAttemptCloseReason.STARTED;
	}

	public UUID getId() {
		return id;
	}

	public String getOrderId() {
		return orderId;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public CheckoutAttemptCloseReason getCloseReason() {
		return closeReason;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CheckoutAttempt that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}