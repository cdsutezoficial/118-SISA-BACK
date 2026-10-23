package mx.edu.utez.sisa.finance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One attempt to pay a {@link Payment} online (EVO Hosted Checkout).
 *
 * <p><b>Generic, owned by finance</b> (2026-10-23). It used to live in
 * {@code admission} keyed by the candidate; it now hangs from the charge it
 * tries to collect ({@code payment_id}), so any online charge — a ficha, an
 * induction course, a student's colegiatura — leaves the same trail. The payer
 * anchor ({@code candidate_id} / {@code student_id}) is copied so a flow can
 * list "my attempts" without joining.
 *
 * <p>Rules that make it safe:
 * <ul>
 * <li><b>Append-only.</b> {@code order_id} carries a random suffix on every
 * retry, so no attempt overwrites another and the whole history of a charge
 * stays queryable.</li>
 * <li><b>Opened before the gateway is called.</b> Once EVO holds an order its
 * money may be captured at any second; the row with the {@code order_id} the
 * bank will answer to already exists, so the nightly sweep can always ask.</li>
 * <li><b>Carries its own price snapshot</b> (tariff, discount, rate row,
 * benefit). Whichever attempt the bank captures is booked with the numbers it
 * charged, not with whatever the charge was re-frozen to afterwards.</li>
 * <li><b>Holds a quota slot only while it is young.</b> An open
 * ({@code STARTED}) attempt created less than the checkout hold ago (15 min)
 * counts as an occupied place; after that it counts for nothing, without any
 * job having to release it.</li>
 * </ul>
 */
@Entity
@Table(name = "checkout_attempt", indexes = {
		@Index(name = "ix_checkout_attempt_payment", columnList = "payment_id, close_reason"),
		@Index(name = "ix_checkout_attempt_candidate", columnList = "candidate_id, close_reason") })
public class CheckoutAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/** EVO {@code order.id}; unique because it is the only handle the bank accepts. */
	@Column(name = "order_id", nullable = false, length = 64, unique = true)
	private String orderId;

	/** The charge ({@code payment.id}) this attempt tries to collect. */
	@Column(name = "payment_id", nullable = false)
	private UUID paymentId;

	@Column(name = "candidate_id")
	private UUID candidateId;

	@Column(name = "student_id")
	private UUID studentId;

	/** The hosted-checkout session id EVO returned, once it did. */
	@Column(name = "session_id", length = 64)
	private String sessionId;

	/** What this attempt asked the payer for. */
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "rate_id")
	private UUID rateId;

	@Column(name = "original_amount", precision = 12, scale = 2)
	private BigDecimal originalAmount;

	@Column(name = "discount_amount", precision = 12, scale = 2)
	private BigDecimal discountAmount;

	@Column(name = "benefit_id")
	private UUID benefitId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "close_reason", nullable = false, length = 32)
	private CheckoutAttemptCloseReason closeReason;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected CheckoutAttempt() {
		// JPA
	}

	/**
	 * @param orderId        the EVO order id
	 * @param paymentId      the charge being collected
	 * @param candidateId    payer anchor (exactly one of candidate/student)
	 * @param studentId      payer anchor (exactly one of candidate/student)
	 * @param amount         what the attempt charges
	 * @param createdAt      when it was opened
	 * @param rateId         snapshot: the rate row that priced it
	 * @param originalAmount snapshot: the tariff
	 * @param discountAmount snapshot: the discount
	 * @param benefitId      snapshot: the discount applied, if any
	 */
	public CheckoutAttempt(String orderId, UUID paymentId, UUID candidateId, UUID studentId, BigDecimal amount,
			Instant createdAt, UUID rateId, BigDecimal originalAmount, BigDecimal discountAmount, UUID benefitId) {
		this.orderId = Objects.requireNonNull(orderId, "El intento necesita su orderId");
		this.paymentId = Objects.requireNonNull(paymentId, "El intento necesita el cargo que cobra");
		Payment.requireSingleAnchor(candidateId, studentId);
		this.candidateId = candidateId;
		this.studentId = studentId;
		this.amount = Objects.requireNonNull(amount, "El intento necesita su monto");
		this.createdAt = Objects.requireNonNull(createdAt, "El intento necesita su fecha de creación");
		this.rateId = rateId;
		this.originalAmount = originalAmount;
		this.discountAmount = discountAmount;
		this.benefitId = benefitId;
		this.closeReason = CheckoutAttemptCloseReason.STARTED;
	}

	/**
	 * Closes an open attempt. A closed attempt is history and is never
	 * rewritten; {@code STARTED} is not a closing reason.
	 */
	public void close(CheckoutAttemptCloseReason reason, Instant at) {
		Objects.requireNonNull(reason, "El intento necesita una razón de cierre");
		if (reason == CheckoutAttemptCloseReason.STARTED || !isOpen()) {
			return;
		}
		this.closeReason = reason;
		this.closedAt = Objects.requireNonNull(at, "El intento necesita su fecha de cierre");
	}

	/** Records the hosted-checkout session EVO opened for this order. */
	public void registerSession(String sessionId) {
		this.sessionId = sessionId;
	}

	public boolean isOpen() {
		return this.closeReason == CheckoutAttemptCloseReason.STARTED;
	}

	public boolean hasSnapshot() {
		return originalAmount != null;
	}

	public UUID getId() {
		return id;
	}

	public String getOrderId() {
		return orderId;
	}

	public UUID getPaymentId() {
		return paymentId;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public UUID getRateId() {
		return rateId;
	}

	public BigDecimal getOriginalAmount() {
		return originalAmount;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public UUID getBenefitId() {
		return benefitId;
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
