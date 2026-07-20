package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidGradeScaleEntriesException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A grade scale (nomenclatura de letras, e.g. "CO"/"Competente") owned by an
 * {@link AcademicPlan} for one {@code classificationId}
 * (docs/plans/2026-07-20-grade-scale.md §1-2 — "GradeScale está 'dentro de
 * AcademicPlan' en el dominio, igual que PlanLevel/Subject"). There is
 * deliberately no {@code GradeScaleRepository} out-port and no independent
 * controller — every mutation goes through {@code AcademicPlan}'s
 * {@code setGradeScale}/{@code updateGradeScale}/{@code removeGradeScale}
 * mutators. Package-private constructor/mutators keep that boundary: only
 * {@link AcademicPlan} (same package) can create or change a
 * {@code GradeScale} — mirrors {@link PlanLevel}'s relationship to
 * {@link AcademicPlan}.
 *
 * <p>
 * {@code numericMin}/{@code numericMax}/{@code GradeScaleEntry.fromValue}/
 * {@code toValue} are persisted as whole-number {@link BigDecimal}
 * ({@code scale = 0}) — a design decision made here (not specified by
 * {@code 02-config-academica.md}, which only says "Decimal") because the
 * PO-confirmed coverage/gap/overlap rule (see {@link #validateEntries}) needs
 * a concrete "next value" step to tell a legitimate boundary (e.g.
 * {@code [0,69]} then {@code [70,100]}) apart from a real gap. If a future
 * requirement needs fractional letter-grade boundaries, both the column scale
 * and {@link #STEP} must change together.
 */
@Entity
@Table(name = "grade_scale")
public class GradeScale {

	/**
	 * The smallest representable increment between two adjacent entries, given
	 * the {@code scale = 0} column definition — see the class javadoc.
	 */
	private static final BigDecimal STEP = BigDecimal.ONE;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plan_id", nullable = false)
	private AcademicPlan plan;

	@Column(name = "classification_id", nullable = false)
	private UUID classificationId;

	@Column(name = "numeric_min", nullable = false, precision = 5, scale = 0)
	private BigDecimal numericMin;

	@Column(name = "numeric_max", nullable = false, precision = 5, scale = 0)
	private BigDecimal numericMax;

	@OneToMany(mappedBy = "scale", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("fromValue ASC")
	private List<GradeScaleEntry> entries = new ArrayList<>();

	protected GradeScale() {
		// JPA
	}

	GradeScale(AcademicPlan plan, UUID classificationId, BigDecimal numericMin, BigDecimal numericMax,
			List<GradeScaleEntryData> entries) {
		validateEntries(numericMin, numericMax, entries);
		this.plan = plan;
		this.classificationId = classificationId;
		this.numericMin = numericMin;
		this.numericMax = numericMax;
		entries.forEach(this::addEntryInternal);
	}

	void updateDetails(UUID classificationId, BigDecimal numericMin, BigDecimal numericMax,
			List<GradeScaleEntryData> entries) {
		validateEntries(numericMin, numericMax, entries);
		this.classificationId = classificationId;
		this.numericMin = numericMin;
		this.numericMax = numericMax;
		this.entries.clear();
		entries.forEach(this::addEntryInternal);
	}

	private void addEntryInternal(GradeScaleEntryData data) {
		entries.add(new GradeScaleEntry(this, data.fromValue(), data.toValue(), data.letter(), data.description(),
				data.passed()));
	}

	/**
	 * PO-confirmed rule (2026-07-20): the union of all entry ranges MUST
	 * exactly cover {@code [numericMin, numericMax]} — no gaps, no overlaps.
	 * Algorithm: sort entries by {@code fromValue}, then walk them checking
	 * (1) the first entry starts exactly at {@code numericMin}, (2) each
	 * entry's {@code toValue + STEP} equals the next entry's {@code
	 * fromValue} (a smaller sum means a gap, a larger sum means an overlap),
	 * and (3) the last entry ends exactly at {@code numericMax}.
	 */
	private static void validateEntries(BigDecimal numericMin, BigDecimal numericMax,
			List<GradeScaleEntryData> entries) {
		if (entries == null || entries.isEmpty()) {
			throw new InvalidGradeScaleEntriesException(
					"At least one entry is required to cover the scale range [" + numericMin + ", " + numericMax
							+ "]");
		}
		List<GradeScaleEntryData> sorted = entries.stream().sorted(Comparator.comparing(GradeScaleEntryData::fromValue))
				.toList();
		for (GradeScaleEntryData entry : sorted) {
			if (entry.fromValue().compareTo(entry.toValue()) > 0) {
				throw new InvalidGradeScaleEntriesException(
						"Entry fromValue must be <= toValue: [" + entry.fromValue() + ", " + entry.toValue() + "]");
			}
		}

		GradeScaleEntryData first = sorted.get(0);
		if (first.fromValue().compareTo(numericMin) != 0) {
			throw new InvalidGradeScaleEntriesException("Gap detected: entries must start at numericMin="
					+ numericMin + " but the first entry starts at " + first.fromValue());
		}

		for (int i = 0; i < sorted.size() - 1; i++) {
			BigDecimal currentTo = sorted.get(i).toValue();
			BigDecimal nextFrom = sorted.get(i + 1).fromValue();
			BigDecimal expectedNextFrom = currentTo.add(STEP);
			int comparison = expectedNextFrom.compareTo(nextFrom);
			if (comparison < 0) {
				throw new InvalidGradeScaleEntriesException(
						"Gap detected between entry ending at " + currentTo + " and next entry starting at "
								+ nextFrom);
			}
			if (comparison > 0) {
				throw new InvalidGradeScaleEntriesException(
						"Overlap detected between entry ending at " + currentTo + " and next entry starting at "
								+ nextFrom);
			}
		}

		GradeScaleEntryData last = sorted.get(sorted.size() - 1);
		if (last.toValue().compareTo(numericMax) != 0) {
			throw new InvalidGradeScaleEntriesException("Gap detected: entries must end at numericMax=" + numericMax
					+ " but the last entry ends at " + last.toValue());
		}
	}

	public UUID getId() {
		return id;
	}

	public AcademicPlan getPlan() {
		return plan;
	}

	public UUID getClassificationId() {
		return classificationId;
	}

	public BigDecimal getNumericMin() {
		return numericMin;
	}

	public BigDecimal getNumericMax() {
		return numericMax;
	}

	public List<GradeScaleEntry> getEntries() {
		return Collections.unmodifiableList(entries);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GradeScale that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
