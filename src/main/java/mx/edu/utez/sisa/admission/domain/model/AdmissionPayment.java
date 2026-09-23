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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Admission-ticket payment ("pago de ficha de admisión"), per
 * {@code 118-SISA-CLAUDE/docs/design/dominio/03-admision.md}. Created as
 * {@code PENDING} together with its {@link Candidate} by
 * {@code RegisterCandidateUseCase} (the ticket generates a payment reference,
 * amount and deadline the moment the ficha is created); transitioned to
 * {@code PAID} by {@code ConfirmAdmissionPaymentUseCase}.
 *
 * <p>One {@code AdmissionPayment} per {@code Candidate} for the
 * {@code ADMISSION_FICHA} concept (identity: {@code candidateId}). The
 * {@code candidateId} FK is a plain {@code UUID} column — {@code Candidate}
 * is the owning aggregate (same bare-FK convention as {@code Candidate#personId}).
 *
 * <p>TEMPORARY DEVIATION: payment confirmation is triggered directly by the
 * public portal's "Pagar en línea" button (no EVO webhook yet) — the EVO
 * Hosted-Checkout integration lands later and will replace this trigger; the
 * aggregate shape already matches the design.
 */
@Entity
@Table(name = "admission_payment")
public class AdmissionPayment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "candidate_id", nullable = false)
	private UUID candidateId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AdmissionPaymentConcept concept;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "reference_number", nullable = false, length = 40)
	private String referenceNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false)
	private AdmissionPaymentStatus paymentStatus;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "receipt_number", length = 40)
	private String receiptNumber;

	@Column(name = "payment_deadline", nullable = false)
	private LocalDate paymentDeadline;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AdmissionPayment() {
		// JPA
	}

	public AdmissionPayment(UUID candidateId, AdmissionPaymentConcept concept, BigDecimal amount,
			String referenceNumber, LocalDate paymentDeadline) {
		this.candidateId = candidateId;
		this.concept = concept;
		this.amount = amount;
		this.referenceNumber = referenceNumber;
		this.paymentStatus = AdmissionPaymentStatus.PENDING;
		this.paymentDeadline = paymentDeadline;
		this.createdAt = Instant.now();
	}

	/**
	 * Marks this ficha payment as {@code PAID}, stamping when it was paid and
	 * the receipt number. Guards against double-payment: re-invoking after the
	 * first successful payment is a no-op (returns {@code false}).
	 *
	 * @param receiptNumber transaction receipt issued by the payment
	 *                      confirmation ("compra" / manual capture).
	 * @return {@code true} if the transition PENDING → PAID actually happened.
	 */
	public boolean markPaid(String receiptNumber) {
		if (this.paymentStatus == AdmissionPaymentStatus.PAID) {
			return false;
		}
		this.paymentStatus = AdmissionPaymentStatus.PAID;
		this.paidAt = Instant.now();
		this.receiptNumber = receiptNumber;
		return true;
	}

	public UUID getId() {
		return id;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public AdmissionPaymentConcept getConcept() {
		return concept;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getReferenceNumber() {
		return referenceNumber;
	}

	public AdmissionPaymentStatus getPaymentStatus() {
		return paymentStatus;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public String getReceiptNumber() {
		return receiptNumber;
	}

	public LocalDate getPaymentDeadline() {
		return paymentDeadline;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AdmissionPayment that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}