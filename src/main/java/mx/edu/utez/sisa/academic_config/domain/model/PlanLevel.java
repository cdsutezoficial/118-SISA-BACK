package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A level (cuatrimestre) owned by an {@link AcademicPlan} (design.md —
 * Decision: "Child persistence — JPA composition, not separate tables
 * reachable outside the aggregate"). There is deliberately no
 * {@code PlanLevelRepository} out-port and no independent controller — every
 * mutation goes through {@code AcademicPlan}'s mutator methods.
 * Package-private constructor/mutators keep that boundary: only
 * {@link AcademicPlan} (same package) can create or change a
 * {@code PlanLevel}.
 */
@Entity
@Table(name = "plan_level")
public class PlanLevel {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plan_id", nullable = false)
	private AcademicPlan plan;

	@Column(name = "level_number", nullable = false)
	private int levelNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PlanLevelType type;

	@Column
	private String description;

	@OneToMany(mappedBy = "level", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("displayOrder ASC")
	private List<Subject> subjects = new ArrayList<>();

	protected PlanLevel() {
		// JPA
	}

	PlanLevel(AcademicPlan plan, int levelNumber, PlanLevelType type, String description) {
		this.plan = plan;
		this.levelNumber = levelNumber;
		this.type = type;
		this.description = description;
	}

	void updateDetails(int levelNumber, PlanLevelType type, String description) {
		this.levelNumber = levelNumber;
		this.type = type;
		this.description = description;
	}

	void addSubject(Subject subject) {
		subjects.add(subject);
	}

	void removeSubject(Subject subject) {
		subjects.remove(subject);
	}

	public UUID getId() {
		return id;
	}

	public AcademicPlan getPlan() {
		return plan;
	}

	public int getLevelNumber() {
		return levelNumber;
	}

	public PlanLevelType getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}

	public List<Subject> getSubjects() {
		return Collections.unmodifiableList(subjects);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PlanLevel that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
