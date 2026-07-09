package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * A subject (materia) owned by a {@link PlanLevel}, in turn owned by an
 * {@link AcademicPlan} (design.md — Decision: "Child persistence — JPA
 * composition, not separate tables reachable outside the aggregate"). There
 * is deliberately no {@code SubjectRepository} out-port and no independent
 * controller — every mutation goes through {@code AcademicPlan}'s mutator
 * methods. Package-private constructor/mutators keep that boundary: only
 * {@link AcademicPlan} (same package) can create or change a {@code Subject}.
 * {@code classificationId} is a schema-only {@code UUID} with no existence
 * validation in this slice (spec: "classificationId is accepted without
 * existence validation").
 */
@Entity
@Table(name = "subject")
public class Subject {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plan_level_id", nullable = false)
	private PlanLevel level;

	/**
	 * Denormalized plain UUID column (kernel spec: "planId | UUID | FK →
	 * AcademicPlan") kept for future flat queries — no JPA relation, mirrors
	 * {@code AcademicProgram.continuityProgramId}'s schema-only precedent.
	 */
	@Column(name = "plan_id", nullable = false)
	private UUID planId;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int credits;

	@Column(name = "weekly_hours", nullable = false)
	private int weeklyHours;

	@Column(name = "evaluation_units", nullable = false)
	private int evaluationUnits;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SubjectType type;

	@Column(name = "is_retakeable", nullable = false)
	private boolean isRetakeable;

	@Column(name = "classification_id", nullable = false)
	private UUID classificationId;

	protected Subject() {
		// JPA
	}

	Subject(PlanLevel level, UUID planId, String code, String name, int credits, int weeklyHours,
			int evaluationUnits, int displayOrder, SubjectType type, boolean isRetakeable, UUID classificationId) {
		this.level = level;
		this.planId = planId;
		this.code = code;
		this.name = name;
		this.credits = credits;
		this.weeklyHours = weeklyHours;
		this.evaluationUnits = evaluationUnits;
		this.displayOrder = displayOrder;
		this.type = type;
		this.isRetakeable = isRetakeable;
		this.classificationId = classificationId;
	}

	void updateDetails(String code, String name, int credits, int weeklyHours, int evaluationUnits, int displayOrder,
			SubjectType type, boolean isRetakeable, UUID classificationId) {
		this.code = code;
		this.name = name;
		this.credits = credits;
		this.weeklyHours = weeklyHours;
		this.evaluationUnits = evaluationUnits;
		this.displayOrder = displayOrder;
		this.type = type;
		this.isRetakeable = isRetakeable;
		this.classificationId = classificationId;
	}

	public UUID getId() {
		return id;
	}

	public PlanLevel getLevel() {
		return level;
	}

	public UUID getPlanId() {
		return planId;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public int getCredits() {
		return credits;
	}

	public int getWeeklyHours() {
		return weeklyHours;
	}

	public int getEvaluationUnits() {
		return evaluationUnits;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public SubjectType getType() {
		return type;
	}

	public boolean isRetakeable() {
		return isRetakeable;
	}

	public UUID getClassificationId() {
		return classificationId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Subject that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
