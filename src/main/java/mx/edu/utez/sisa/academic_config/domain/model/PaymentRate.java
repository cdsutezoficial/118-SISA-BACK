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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment rate — Fase 2 of 4 of "Conceptos de Pago" (source:
 * {@code 02-config-academica.md} lines 254-267, plan:
 * {@code docs/plans/2026-07-28-payment-rate.md}). A single row in the
 * append-only pricing history of a {@link PaymentConcept} for one
 * {@code (conceptId, programId, level)} combination — {@code programId} and
 * {@code level} are both nullable and, when null, are treated as their OWN
 * value in that combination, never as a wildcard (plan section 2/4).
 *
 * <p>
 * Unlike every other child entity in this module ({@code PlanLevel},
 * {@code Subject}, {@code GradeScale}), this row is never edited nor deleted
 * — it is either the currently-active row for its combination or a
 * historical one closed by {@link #close(LocalDate)}. Two independent shapes
 * exist, both enforced by {@code SetPaymentRateUseCaseImpl}, not here (plan
 * section 2):
 * <ul>
 * <li><b>Continuous rate</b> ({@code periodId == null}): forms a
 * non-overlapping date-range chain per combination — creating a new one
 * closes the previous active row for the SAME combination.
 * <li><b>Period-scoped rate</b> ({@code periodId != null}): independent of
 * the continuous chain and of every other {@code periodId} — never closed by
 * anything, unique per exact
 * {@code (conceptId, programId, level, periodId)} combination.
 * </ul>
 *
 * <p>
 * Owns its own JPA repository (plan section 5) rather than being encapsulated
 * inside {@link PaymentConcept} like {@code PlanLevel} is inside
 * {@code AcademicPlan} — the pricing history grows indefinitely over time
 * (never deleted), so loading the full collection through the aggregate root
 * on every read would not scale the way {@code PlanLevel} (bounded by
 * {@code totalLevels}) does.
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

	@Column(name = "valid_from", nullable = false)
	private LocalDate validFrom;

	@Column(name = "valid_to")
	private LocalDate validTo;

	protected PaymentRate() {
		// JPA
	}

	/**
	 * @param conceptId required — MUST reference an existing {@code PaymentConcept}, validated by
	 *                  {@code SetPaymentRateUseCaseImpl}, not here
	 * @param programId nullable — MUST reference an existing {@code AcademicProgram} when provided,
	 *                  validated by {@code SetPaymentRateUseCaseImpl}, not here
	 * @param level     nullable — part of the combination key, treated as its own value when null
	 * @param amount    MUST be greater than zero, validated by {@code SetPaymentRateUseCaseImpl},
	 *                  not here
	 * @param periodId  nullable — MUST reference an existing {@code AcademicPeriod} when provided,
	 *                  validated by {@code SetPaymentRateUseCaseImpl}, not here; {@code null} means
	 *                  this is a continuous rate, non-null means it is period-scoped
	 * @param validFrom required, caller-supplied
	 */
	public PaymentRate(UUID conceptId, UUID programId, AcademicLevel level, BigDecimal amount, UUID periodId,
			LocalDate validFrom) {
		this.conceptId = conceptId;
		this.programId = programId;
		this.level = level;
		this.amount = amount;
		this.periodId = periodId;
		this.validFrom = validFrom;
		this.validTo = null;
	}

	/**
	 * Closes this row — sets {@link #validTo} to the day immediately before
	 * {@code newValidFrom} (plan section 2/4: "validTo = nuevaValidFrom.minusDays(1)"),
	 * so date ranges never overlap. Only ever invoked by
	 * {@code SetPaymentRateUseCaseImpl} on the currently-active continuous row
	 * ({@code periodId == null}) of the same combination as the new rate being
	 * created — period-scoped rows are never closed.
	 */
	public void close(LocalDate newValidFrom) {
		this.validTo = newValidFrom.minusDays(1);
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

	public LocalDate getValidFrom() {
		return validFrom;
	}

	public LocalDate getValidTo() {
		return validTo;
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
