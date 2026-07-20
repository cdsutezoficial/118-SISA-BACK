package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A numeric-range-to-letter mapping owned by a {@link GradeScale}, in turn
 * owned by an {@link AcademicPlan} (docs/plans/2026-07-20-grade-scale.md §2 —
 * "el patrón de PlanLevel/Subject"). There is deliberately no
 * {@code GradeScaleEntryRepository} out-port and no independent controller —
 * every mutation goes through {@code AcademicPlan}'s {@code setGradeScale}/
 * {@code updateGradeScale} mutators. Package-private constructor keeps that
 * boundary: only {@link GradeScale} (same package) can create a
 * {@code GradeScaleEntry} — mirrors {@link Subject}'s relationship to
 * {@link PlanLevel}.
 */
@Entity
@Table(name = "grade_scale_entry")
public class GradeScaleEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "scale_id", nullable = false)
	private GradeScale scale;

	@Column(name = "from_value", nullable = false, precision = 5, scale = 1)
	private BigDecimal fromValue;

	@Column(name = "to_value", nullable = false, precision = 5, scale = 1)
	private BigDecimal toValue;

	@Column(nullable = false)
	private String letter;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private boolean passed;

	protected GradeScaleEntry() {
		// JPA
	}

	GradeScaleEntry(GradeScale scale, BigDecimal fromValue, BigDecimal toValue, String letter, String description,
			boolean passed) {
		this.scale = scale;
		this.fromValue = fromValue;
		this.toValue = toValue;
		this.letter = letter;
		this.description = description;
		this.passed = passed;
	}

	public UUID getId() {
		return id;
	}

	public GradeScale getScale() {
		return scale;
	}

	public BigDecimal getFromValue() {
		return fromValue;
	}

	public BigDecimal getToValue() {
		return toValue;
	}

	public String getLetter() {
		return letter;
	}

	public String getDescription() {
		return description;
	}

	public boolean isPassed() {
		return passed;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GradeScaleEntry that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
