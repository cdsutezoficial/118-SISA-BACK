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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Descuento ({@code er-08-pagos.md} — {@code payment_benefit}). Since the
 * 2026-10-14 design pass this entity is <b>solo descuento</b>: the deferral
 * left for a separate mechanism, and since the 2026-10-19 unification that
 * mechanism is a {@code payment_installment} with N = 1, not its own table.
 *
 * <p>Scoped to {@code (anchor, conceptCode)} with an optional
 * {@code periodId}/{@code generationId} anchor and a
 * {@code validFrom/validTo} window (R5), where {@code anchor} is
 * {@code candidateId} OR {@code studentId} — exactly one non-null (2026-10-21):
 * a discount points at a concept <b>of</b> a specific candidato/estudiante.
 * {@code conceptCode} is the catalog
 * {@code code}, free-text on purpose: a discount may point at a concept owned
 * by another bounded context ({@code ADMISSION_FICHA}).
 *
 * <p>{@code mode} = {@link BenefitMode#FULL} is the 100 % / exemption case —
 * there is no separate EXEMPTION type (decisión 2026-10-09). A payment quoted
 * against a FULL benefit lands with {@code amountDue = $0} and
 * {@link PaymentStatus#EXEMPT}, keeping the rastro of who granted it.
 *
 * <p>Abonos never mutate this row (R8): the benefit is re-evaluated at
 * quoting and at payment time, and an expired benefit simply stops resolving
 * (R6).
 */
@Entity
@Table(name = "payment_benefit", indexes = @Index(name = "ix_benefit_anchor_concept", columnList = "candidate_id, student_id, scope_concept_code"))
public class PaymentBenefit {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "candidate_id")
	private UUID candidateId;

	@Column(name = "student_id")
	private UUID studentId;

	/**
	 * The charge (adeudo) the staff selected when granting this discount.
	 *
	 * <p>Staff grant a discount on a specific debt of the person — they look the
	 * person up, list their charges and pick one — so the discount is tied to
	 * that {@code payment} row. The anchor and {@link #conceptCode} are copied
	 * from it. {@code null} only for discounts granted before this link
	 * existed, which still resolve by {@code (anchor, conceptCode)}.
	 */
	@Column(name = "payment_id")
	private UUID paymentId;

	@Column(name = "scope_concept_code", nullable = false, length = 50)
	private String conceptCode;

	@Column(name = "period_id")
	private UUID periodId;

	@Column(name = "generation_id")
	private UUID generationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BenefitMode mode;

	@Column(precision = 5, scale = 2)
	private BigDecimal percent;

	@Column(precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "valid_from", nullable = false)
	private LocalDate validFrom;

	@Column(name = "valid_to")
	private LocalDate validTo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BenefitStatus status;

	@Column(nullable = false, length = 250)
	private String reason;

	@Column(name = "granted_by", nullable = false)
	private UUID grantedById;

	@Column(name = "granted_by_name", nullable = false, length = 150)
	private String grantedByName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PaymentBenefit() {
		// JPA
	}

	/**
	 * Validates the mode-specific pairing up front — {@code PERCENT} needs a
	 * percent in (0, 100], {@code AMOUNT} a positive amount, {@code FULL}
	 * neither — so an invalid row cannot be built by any caller, including
	 * tests and future flows. The same split
	 * {@code PaymentConcept#validateLevelNumber} makes.
	 */
	public PaymentBenefit(UUID candidateId, UUID studentId, String conceptCode, UUID periodId, UUID generationId,
			BenefitMode mode, BigDecimal percent, BigDecimal amount, LocalDate validFrom, LocalDate validTo, String reason,
			UUID grantedById, String grantedByName, Instant createdAt) {
		this(null, candidateId, studentId, conceptCode, periodId, generationId, mode, percent, amount, validFrom,
				validTo, reason, grantedById, grantedByName, createdAt);
	}

	/**
	 * A discount granted on a specific charge ({@code paymentId}); anchor and
	 * concept are the charge's.
	 */
	public PaymentBenefit(UUID paymentId, UUID candidateId, UUID studentId, String conceptCode, UUID periodId,
			UUID generationId, BenefitMode mode, BigDecimal percent, BigDecimal amount, LocalDate validFrom,
			LocalDate validTo, String reason, UUID grantedById, String grantedByName, Instant createdAt) {
		validateMode(mode, percent, amount);
		this.paymentId = paymentId;
		Payment.requireSingleAnchor(candidateId, studentId);
		this.candidateId = candidateId;
		this.studentId = studentId;
		this.conceptCode = conceptCode;
		this.periodId = periodId;
		this.generationId = generationId;
		this.mode = mode;
		this.percent = percent;
		this.amount = amount;
		this.validFrom = validFrom;
		this.validTo = validTo;
		this.status = BenefitStatus.ACTIVE;
		this.reason = reason;
		this.grantedById = grantedById;
		this.grantedByName = grantedByName;
		this.createdAt = createdAt;
	}

	private static void validateMode(BenefitMode mode, BigDecimal percent, BigDecimal amount) {
		if (mode == null) {
			throw new IllegalArgumentException("mode is required");
		}
		switch (mode) {
			case PERCENT -> {
				if (percent == null || percent.signum() <= 0 || percent.compareTo(new BigDecimal("100")) > 0) {
					throw new IllegalArgumentException("percent must be in (0, 100] for mode PERCENT");
				}
			}
			case AMOUNT -> {
				if (amount == null || amount.signum() <= 0) {
					throw new IllegalArgumentException("amount must be positive for mode AMOUNT");
				}
			}
			case FULL -> {
				if (percent != null || amount != null) {
					throw new IllegalArgumentException("mode FULL carries neither percent nor amount");
				}
			}
		}
	}

	/**
	 * Whether this discount is in force on {@code onDate} and anchored to the
	 * quoted {@code periodId}/{@code generationId}. A {@code null} anchor on
	 * the benefit means "any"; a {@code null} on the quote matches only a
	 * benefit that is also unanchored on that axis.
	 */
	public boolean appliesTo(LocalDate onDate, UUID quotePeriodId, UUID quoteGenerationId) {
		if (status != BenefitStatus.ACTIVE) {
			return false;
		}
		if (onDate.isBefore(validFrom)) {
			return false;
		}
		if (validTo != null && onDate.isAfter(validTo)) {
			return false;
		}
		return anchoredMatches(periodId, quotePeriodId) && anchoredMatches(generationId, quoteGenerationId);
	}

	private static boolean anchoredMatches(UUID anchor, UUID quote) {
		return anchor == null || anchor.equals(quote);
	}

	/**
	 * Applies an edit to the discount (Fase 2 CRUD). Only the editable fields
	 * are touched — the subject ({@code candidateId}/{@code studentId}/
	 * {@code conceptCode}) and the
	 * grant audit stay put. The mode pairing is re-validated exactly as in the
	 * constructor, so an edit can never turn a well-formed discount into a
	 * malformed one. The status guard (an applied/USED discount is immutable)
	 * lives in {@code UpdateBenefitUseCase}, not here, so the entity stays a
	 * plain data holder.
	 */
	public void applyUpdate(UUID periodId, UUID generationId, BenefitMode mode, BigDecimal percent, BigDecimal amount,
			LocalDate validFrom, LocalDate validTo, String reason) {
		validateMode(mode, percent, amount);
		this.periodId = periodId;
		this.generationId = generationId;
		this.mode = mode;
		this.percent = percent;
		this.amount = amount;
		this.validFrom = validFrom;
		this.validTo = validTo;
		this.reason = reason;
	}

	/**
	 * Voids the discount. The status guard (cancel only while {@link
	 * BenefitStatus#ACTIVE}, never once {@code USED}) is enforced by
	 * {@code CancelBenefitUseCase}; cancelling affects only future quotes — a
	 * payment that already froze this discount keeps its amount.
	 */
	public void cancel() {
		this.status = BenefitStatus.CANCELLED;
	}

	/** Marks the discount as consumed by a payment ({@link BenefitStatus#USED}). */
	public void markUsed() {
		this.status = BenefitStatus.USED;
	}

	public boolean isActive() {
		return status == BenefitStatus.ACTIVE;
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

	/**
	 * Ties a discount granted for the concept (no charge) to a charge, so it
	 * keeps resolving when the charge moves to another concept (career change
	 * before paying). A discount already tied to a charge is left alone.
	 */
	public void linkToPayment(UUID paymentId) {
		if (this.paymentId == null) {
			this.paymentId = paymentId;
		}
	}

	public UUID getPaymentId() {
		return paymentId;
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

	public BenefitMode getMode() {
		return mode;
	}

	public BigDecimal getPercent() {
		return percent;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getValidFrom() {
		return validFrom;
	}

	public LocalDate getValidTo() {
		return validTo;
	}

	public BenefitStatus getStatus() {
		return status;
	}

	public String getReason() {
		return reason;
	}

	public UUID getGrantedById() {
		return grantedById;
	}

	public String getGrantedByName() {
		return grantedByName;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PaymentBenefit that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
