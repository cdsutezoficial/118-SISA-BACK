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
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

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
 * <p>{@code conceptType} is a denormalized copy of
 * {@link PaymentConceptType#name()} kept only to filter without joining the
 * catalog; the enum class itself is reused rather than duplicated so the two
 * sides cannot drift into different vocabularies.
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

	@Enumerated(EnumType.STRING)
	@Column(name = "concept_type", nullable = false)
	private PaymentConceptType conceptType;

	@Column(name = "period_id")
	private UUID periodId;

	@Column(name = "generation_id")
	private UUID generationId;

	@Column(name = "program_id")
	private UUID programId;

	@Enumerated(EnumType.STRING)
	private AcademicLevel level;

	@Column(name = "original_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal originalAmount;

	@Column(name = "amount_due", nullable = false, precision = 12, scale = 2)
	private BigDecimal amountDue;

	@Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
	private BigDecimal amountPaid;

	@Column(name = "benefit_id")
	private UUID benefitId;

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

	@Column(name = "created_by")
	private UUID createdBy;

	protected Payment() {
		// JPA
	}

	/**
	 * Freezes a quotation into an adeudo (R3/R1). {@code status} and
	 * {@code amountDue} are supplied already decided — the FULL-benefit/exempt
	 * case is {@link PaymentStatus#EXEMPT} with a zero {@code amountDue}, and
	 * every other case is {@link PaymentStatus#QUOTED} with the quoted amount.
	 * {@code amountPaid} always starts at zero.
	 */
	public Payment(UUID candidateId, UUID studentId, String conceptCode, PaymentConceptType conceptType, UUID periodId,
			UUID generationId, UUID programId, AcademicLevel level, BigDecimal originalAmount, BigDecimal amountDue,
			UUID benefitId, PaymentStatus status, PaymentOrigin origin, Instant dueAt, Instant createdAt,
			UUID createdBy) {
		requireSingleAnchor(candidateId, studentId);
		this.candidateId = candidateId;
		this.studentId = studentId;
		this.conceptCode = conceptCode;
		this.conceptType = conceptType;
		this.periodId = periodId;
		this.generationId = generationId;
		this.programId = programId;
		this.level = level;
		this.originalAmount = originalAmount;
		this.amountDue = amountDue;
		this.amountPaid = BigDecimal.ZERO.setScale(2);
		this.benefitId = benefitId;
		this.status = status;
		this.origin = origin;
		this.dueAt = dueAt;
		this.createdAt = createdAt;
		this.createdBy = createdBy;
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

	public UUID getCandidateId() {
		return candidateId;
	}

	public UUID getStudentId() {
		return studentId;
	}

	public String getConceptCode() {
		return conceptCode;
	}

	public PaymentConceptType getConceptType() {
		return conceptType;
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

	public AcademicLevel getLevel() {
		return level;
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

	public UUID getCreatedBy() {
		return createdBy;
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
