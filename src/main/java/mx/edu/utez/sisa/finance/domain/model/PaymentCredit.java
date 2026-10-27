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
 * Saldo a favor ({@code er-08-pagos.md} — {@code payment_credit}, R12). It is
 * <b>of the candidato/estudiante</b> (one anchor non-null, 2026-10-21), not of
 * the payment that produced it: a discount that
 * arrives after a payment was already made leaves the overpayment as a credit
 * the person (or SE/Finanzas, with consent) can apply to any concept, with no
 * supervisor and no automatic cash refund.
 *
 * <p>{@code originPaymentId} records the paid adeudo that produced the credit
 * and {@code benefitId} the retroactive discount that caused it — both
 * optional, because a credit may also come from other flows later.
 * {@code appliedToPaymentId} is filled when the credit is consumed
 * ({@link #applyTo(UUID)}), linking the credit to the adeudo it settled.
 *
 * <p>The row is never hard-deleted; a voided credit becomes
 * {@link CreditStatus#CANCELLED}.
 */
@Entity
@Table(name = "payment_credit", indexes = @Index(name = "ix_credit_anchor", columnList = "candidate_id, student_id"))
public class PaymentCredit {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "candidate_id")
	private UUID candidateId;

	@Column(name = "student_id")
	private UUID studentId;

	@Column(name = "origin_payment_id")
	private UUID originPaymentId;

	@Column(name = "benefit_id")
	private UUID benefitId;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CreditStatus status;

	@Column(name = "applied_to_payment_id")
	private UUID appliedToPaymentId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PaymentCredit() {
		// JPA
	}

	public PaymentCredit(UUID candidateId, UUID studentId, UUID originPaymentId, UUID benefitId, BigDecimal amount,
			Instant createdAt) {
		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
		Payment.requireSingleAnchor(candidateId, studentId);
		this.candidateId = candidateId;
		this.studentId = studentId;
		this.originPaymentId = originPaymentId;
		this.benefitId = benefitId;
		this.amount = amount;
		this.status = CreditStatus.AVAILABLE;
		this.createdAt = createdAt;
	}

	/**
	 * Consumes the credit against an adeudo. The status guard (only
	 * {@code AVAILABLE} credits can be applied) is enforced by
	 * {@code ApplyCreditToPaymentUseCase}, so the entity stays a plain data
	 * holder.
	 */
	public void applyTo(UUID paymentId) {
		this.status = CreditStatus.APPLIED;
		this.appliedToPaymentId = paymentId;
	}

	public void cancel() {
		this.status = CreditStatus.CANCELLED;
	}

	/**
	 * Consumes part of the credit without fully spending it: the amount drops
	 * by {@code taken} and the credit stays {@code AVAILABLE} for the rest,
	 * unless that empties it, in which case it becomes {@code APPLIED}. Used
	 * when the remaining balance of an adeudo is smaller than the credit.
	 */
	public void reduceAmount(BigDecimal taken) {
		this.amount = this.amount.subtract(taken);
		if (this.amount.signum() <= 0) {
			this.amount = BigDecimal.ZERO.setScale(2);
			this.status = CreditStatus.APPLIED;
		}
	}

	public boolean isAvailable() {
		return status == CreditStatus.AVAILABLE;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public UUID getOriginPaymentId() {
		return originPaymentId;
	}

	public UUID getBenefitId() {
		return benefitId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public CreditStatus getStatus() {
		return status;
	}

	public UUID getAppliedToPaymentId() {
		return appliedToPaymentId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PaymentCredit that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
