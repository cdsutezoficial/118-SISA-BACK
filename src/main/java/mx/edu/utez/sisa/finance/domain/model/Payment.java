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
 * Adeudo / cuenta por cobrar ({@code er-08-pagos.md} — {@code payment}).
 * Aggregate root of the {@code finance} bounded context (Fase 1 — núcleo de
 * cobro).
 *
 * <p>The charge key is {@code (anchor, conceptCode, periodId, generationId)}
 * (R1/R2), where {@code anchor} is {@code candidateId} OR {@code studentId} —
 * exactly one non-null (2026-10-21). The charge knows who is paying directly;
 * the {@code Person} is reached by navigating ({@code candidate.personId} /
 * {@code student.personId}). The concept is referenced by its catalog
 * {@code code} as a free-text foreign key — same deliberate choice as
 * {@code payment_benefit.scope_concept_code} — so {@code finance} can price
 * concepts owned by other contexts ({@code ADMISSION_FICHA},
 * {@code INDUCTION_COURSE}) without a FK. {@code periodId}/{@code generationId}
 * are frozen when the adeudo is created and never change when it is paid later.
 *
 * <p>{@code originalAmount} is the catalog rate and {@code amountDue} the
 * quoted amount after the discount in force at quoting time (R3) — the only
 * amount shown and collected. {@code amountPaid} is the sum of the abonos
 * ({@link PaymentEntry}); it is recomputed from the entries inside the
 * transaction rather than trusted from the column, so two concurrent abonos
 * cannot both read the same stale total.
 *
 * <p>The concept's type and the program's level are not copied here: both
 * come from the catalog ({@code concept_code}, {@code program_id}) when needed.
 *
 * <p>The row is never hard-deleted: a voided adeudo becomes
 * {@link PaymentStatus#CANCELLED}.
 */
@Entity
@Table(name = "payment", indexes = @Index(name = "ix_payment_charge_key", columnList = "candidate_id, student_id, concept_code, period_id, generation_id"))
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "candidate_id")
	private UUID candidateId;

	@Column(name = "student_id")
	private UUID studentId;

	@Column(name = "concept_code", nullable = false, length = 50)
	private String conceptCode;


	@Column(name = "period_id")
	private UUID periodId;

	@Column(name = "generation_id")
	private UUID generationId;

	@Column(name = "program_id")
	private UUID programId;


	/**
	 * {@code null} while the debt is only a header. The ficha flow raises the
	 * debt at registration and prices it at checkout, so the tariff and the
	 * amount due are deliberately absent until then.
	 */
	@Column(name = "original_amount", precision = 12, scale = 2)
	private BigDecimal originalAmount;

	@Column(name = "amount_due", precision = 12, scale = 2)
	private BigDecimal amountDue;

	@Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
	private BigDecimal amountPaid;

	@Column(name = "benefit_id")
	private UUID benefitId;

	/**
	 * The {@code payment_rate} row the frozen {@link #originalAmount} came from.
	 * Written together with the amounts at the moment the payer acts (checkout,
	 * caja), so "why did they pay X" can be traced back to the catalog rate.
	 * {@code null} while the adeudo is only a header.
	 */
	@Column(name = "rate_id")
	private UUID rateId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentOrigin origin;

	@Column(name = "due_at")
	private Instant dueAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;


	/**
	 * The reference the payer quotes for this charge (e.g. the ficha's
	 * {@code REF-ADM-2026-000002}); {@code null} when the flow that raised it
	 * has none.
	 */
	@Column(name = "reference_number", length = 40)
	private String referenceNumber;

	protected Payment() {
		// JPA
	}

	/**
	 * Freezes a quotation into an adeudo (R3/R1). {@code status} and
	 * {@code amountDue} are supplied already decided — the FULL-benefit/exempt
	 * case is {@link PaymentStatus#EXEMPT} with a zero {@code amountDue}, and
	 * every other case is {@link PaymentStatus#PENDING} with the quoted amount.
	 * {@code amountPaid} always starts at zero.
	 */
	public Payment(UUID candidateId, UUID studentId, String conceptCode, UUID periodId, UUID generationId,
			UUID programId, BigDecimal originalAmount, BigDecimal amountDue, UUID benefitId, PaymentStatus status,
			PaymentOrigin origin, Instant dueAt, Instant createdAt) {
		requireSingleAnchor(candidateId, studentId);
		this.candidateId = candidateId;
		this.studentId = studentId;
		this.conceptCode = conceptCode;
		this.periodId = periodId;
		this.generationId = generationId;
		this.programId = programId;
		this.originalAmount = originalAmount;
		this.amountDue = amountDue;
		this.amountPaid = BigDecimal.ZERO.setScale(2);
		this.benefitId = benefitId;
		this.status = status;
		this.origin = origin;
		this.dueAt = dueAt;
		this.createdAt = createdAt;
	}

	/**
	 * Adopts the recomputed abono total and the status the settlement decider
	 * derived from it. The total is assigned, never incremented: the caller
	 * (RegisterPaymentEntryUseCase) recomputes it as the SUM of the persisted
	 * entries inside the same transaction, under a pessimistic lock on this
	 * row, so two concurrent abonos cannot both add to the same stale value.
	 */
	public void settle(BigDecimal amountPaid, PaymentStatus newStatus) {
		this.amountPaid = amountPaid;
		this.status = newStatus;
	}

	/**
	 * Transitions to {@link PaymentStatus#CANCELLED}. The adeudo itself is
	 * kept for audit; the entries already registered against it are NOT
	 * reversed here — a refund is a new entry (or a credit in Fase 2), never a
	 * silent rewrite of history.
	 */
	public void cancel() {
		this.status = PaymentStatus.CANCELLED;
	}

	/**
	 * Re-quotes the adeudo with the numbers in force right now (R3).
	 *
	 * <p>Used by the flows that price at the moment the payer acts — the ficha
	 * checkout re-prices the debt that was created at registration, so a
	 * discount granted (or a tariff edited) in between reaches the payer
	 * instead of being frozen at registration.
	 *
	 * <p>Two guards, and both are about never rewriting money that already
	 * happened:
	 * <ul>
	 * <li>the debt must still be quotable — a {@code PAID}, {@code PARTIAL},
	 * {@code CANCELLED} or {@code OVERDUE} adeudo is not a quote any more, and
	 * re-pricing it would change what the person owes after the fact;</li>
	 * <li>nothing may have been paid against it — an abono is money that
	 * arrived, and a new quote cannot un-arrive it.</li>
	 * </ul>
	 *
	 * <p>The benefit is <em>re-resolved</em> by the caller and handed in, so the
	 * discount rules stay in one place; this method only freezes the outcome.
	 *
	 * @param originalAmount the tariff before the discount
	 * @param amountDue      the tariff after the discount
	 * @param benefitId      the discount applied, or {@code null} when none
	 * @param rateId         the catalog rate row the tariff came from, or
	 *                       {@code null} when unknown
	 * @param status         {@link PaymentStatus#PENDING}, or
	 *                       {@link PaymentStatus#EXEMPT} when the discount
	 *                       covered the whole tariff
	 * @throws IllegalStateException if the adeudo is no longer quotable or
	 *                               already carries abonos
	 */
	public void reprice(BigDecimal originalAmount, BigDecimal amountDue, UUID benefitId, UUID rateId,
			PaymentStatus status) {
		if (this.status != PaymentStatus.PENDING && this.status != PaymentStatus.EXEMPT) {
			throw new IllegalStateException(
					"El adeudo " + id + " no se puede re-cotizar en estado " + this.status);
		}
		if (amountPaid.signum() != 0) {
			throw new IllegalStateException("El adeudo " + id + " ya tiene abonos y no se puede re-cotizar.");
		}
		if (status != PaymentStatus.PENDING && status != PaymentStatus.EXEMPT) {
			throw new IllegalStateException("Un adeudo re-cotizado solo puede quedar PENDING o EXEMPT.");
		}
		this.originalAmount = originalAmount;
		this.amountDue = amountDue;
		this.benefitId = benefitId;
		this.rateId = rateId;
		this.status = status;
	}

	/**
	 * Whether the adeudo still accepts a new quote: it is still {@code PENDING}
	 * (open, priced live) and nothing has been paid against it. A header-only
	 * debt (no amounts yet) is always repriceable. {@code EXEMPT} is terminal:
	 * it means the $0 exemption was already booked.
	 */
	public boolean isRepriceable() {
		return status == PaymentStatus.PENDING && amountPaid.signum() == 0;
	}

	/**
	 * Closes a charge whose frozen quote is $0 (a FULL discount) as
	 * {@link PaymentStatus#EXEMPT}: it is settled without money moving, and the
	 * $0 entry with its receipt is the rastro.
	 */
	public void markExempt() {
		if (amountDue == null || amountDue.signum() != 0) {
			throw new IllegalStateException("Solo un adeudo cotizado en $0 puede quedar exento: " + id);
		}
		this.status = PaymentStatus.EXEMPT;
	}

	/** {@code true} once the amounts have been frozen at least once. */
	public boolean isPriced() {
		return originalAmount != null && amountDue != null;
	}

	/**
	 * Exactly one of {@code candidateId}/{@code studentId} must be present: a
	 * charge belongs either to an aspirante (ficha, inducción) or a student
	 * (inscripción, colegiatura…), never both and never neither.
	 */
	public static void requireSingleAnchor(UUID candidateId, UUID studentId) {
		if ((candidateId == null) == (studentId == null)) {
			throw new IllegalArgumentException("exactly one of candidateId/studentId is required");
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getRateId() {
		return rateId;
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public String getConceptCode() {
		return conceptCode;
	}


	public UUID getPeriodId() {
		return periodId;
	}

	public UUID getGenerationId() {
		return generationId;
	}

	public UUID getProgramId() {
		return programId;
	}


	public BigDecimal getOriginalAmount() {
		return originalAmount;
	}

	public BigDecimal getAmountDue() {
		return amountDue;
	}

	public BigDecimal getAmountPaid() {
		return amountPaid;
	}

	public UUID getBenefitId() {
		return benefitId;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public PaymentOrigin getOrigin() {
		return origin;
	}

	public Instant getDueAt() {
		return dueAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Sets the payer-facing reference once; a different second value is refused.
	 */
	public void assignReference(String referenceNumber) {
		if (this.referenceNumber != null && !this.referenceNumber.equals(referenceNumber)) {
			throw new IllegalStateException("El adeudo " + id + " ya tiene referencia: " + this.referenceNumber);
		}
		this.referenceNumber = referenceNumber;
	}

	public String getReferenceNumber() {
		return referenceNumber;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Payment that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
