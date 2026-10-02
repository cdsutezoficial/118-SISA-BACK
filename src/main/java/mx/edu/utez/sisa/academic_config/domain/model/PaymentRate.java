package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.shared.model.AcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment rate — one row in the pricing history of a {@link PaymentConcept} for
 * a single {@code (conceptId, programId, level)} combination. {@code programId}
 * and {@code level} are both nullable and, when null, are their OWN value in
 * that combination, never a wildcard: the three-rung ladder the pricing lookups
 * walk (exact program → the program's level → neither) reads that shape
 * directly.
 *
 * <p>
 * Like {@code PlanLevel}, {@code Subject} and {@code GradeScale} — and unlike
 * the catalog root aggregates — this row is never edited nor deleted. It is
 * either the {@link PaymentRateStatus#ACTIVE} row for its combination or
 * history. A changed amount does not overwrite: it {@link #deactivate()}s the
 * row in force and inserts a new {@code ACTIVE} one, so the price a program
 * was charged last year is still answerable.
 *
 * <p>
 * The row carries no validity window of its own — {@code availableFrom} /
 * {@code availableUntil} live on {@link PaymentConcept} and are the only
 * time-bounding in the catalog. The earlier {@code validFrom}/{@code validTo}
 * pair on this table was removed because it duplicated that question in a
 * second place with a second answer, and reconciling a whole set of rates
 * against a moving date is a problem a status does not have.
 *
 * <p>
 * {@code periodId} stays for the rate that prices one concrete
 * {@link AcademicPeriod}, and MUST be {@code null} for a
 * {@link PaymentConceptType#PERIODIC_QUOTA} concept: a recurring quota is
 * priced per level and applies to every period of the cycle, so pinning one to
 * a period would make it unreachable by the pricing lookups, which require
 * {@code periodId IS NULL}.
 *
 * <p>
 * Owns its own JPA repository rather than being encapsulated inside
 * {@link PaymentConcept} like {@code PlanLevel} is inside {@code AcademicPlan} —
 * the pricing history grows indefinitely and is never deleted, so loading it
 * through the aggregate root on every read would not scale the way
 * {@code PlanLevel} (bounded by {@code totalLevels}) does.
 */
@Entity
@Table(name = "payment_rate")
public class PaymentRate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "concept_id", nullable = false)
	private UUID conceptId;

	@Column(name = "program_id")
	private UUID programId;

	@Enumerated(EnumType.STRING)
	private AcademicLevel level;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "period_id")
	private UUID periodId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentRateStatus status;

	/**
	 * When this amount entered the history. Audit and ordering only — it is
	 * never compared against a business date, so a price correction does not
	 * need a plausible {@code validFrom} invented for it.
	 */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected PaymentRate() {
		// JPA
	}

	/**
	 * @param conceptId required — MUST reference an existing {@code PaymentConcept}, validated by
	 *                  {@code ReconcilePaymentRatesUseCaseImpl}, not here
	 * @param programId nullable — MUST reference an existing {@code AcademicProgram} when provided,
	 *                  validated by the use case, not here
	 * @param level     nullable — part of the combination key, treated as its own value when null
	 * @param amount    MUST be greater than zero, validated by the use case, not here
	 * @param periodId  nullable — MUST reference an existing {@code AcademicPeriod} when provided,
	 *                  validated by the use case, not here; MUST be {@code null} for a
	 *                  {@code PERIODIC_QUOTA} concept
	 * @param createdAt required — audit timestamp, server-supplied
	 */
	public PaymentRate(UUID conceptId, UUID programId, AcademicLevel level, BigDecimal amount, UUID periodId,
			LocalDateTime createdAt) {
		this.conceptId = conceptId;
		this.programId = programId;
		this.level = level;
		this.amount = amount;
		this.periodId = periodId;
		this.status = PaymentRateStatus.ACTIVE;
		this.createdAt = createdAt;
	}

	/**
	 * Takes this row out of force. Idempotent — deactivating an
	 * already-{@code INACTIVE} row is a no-op, same convention as every binary
	 * ACTIVE/INACTIVE toggle in this module. Never deletes anything: the row
	 * remains queryable so a reactivated program recovers its last price.
	 */
	public void deactivate() {
		this.status = PaymentRateStatus.INACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public UUID getConceptId() {
		return conceptId;
	}

	public UUID getProgramId() {
		return programId;
	}

	public AcademicLevel getLevel() {
		return level;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public UUID getPeriodId() {
		return periodId;
	}

	public PaymentRateStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PaymentRate that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
