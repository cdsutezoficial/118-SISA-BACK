package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class HealthData {

	@Column(name = "has_preexisting_condition", nullable = false)
	private boolean hasPreexistingCondition;

	@Column(name = "condition_description")
	private String conditionDescription;

	@Column(name = "has_disability", nullable = false)
	private boolean hasDisability;

	@Column(name = "disability_description")
	private String disabilityDescription;

	@Enumerated(EnumType.STRING)
	@Column(name = "blood_type")
	private BloodType bloodType;

	public HealthData() {
		// JPA
	}

	public HealthData(boolean hasPreexistingCondition, String conditionDescription, boolean hasDisability,
			String disabilityDescription, BloodType bloodType) {
		this.hasPreexistingCondition = hasPreexistingCondition;
		this.conditionDescription = conditionDescription;
		this.hasDisability = hasDisability;
		this.disabilityDescription = disabilityDescription;
		this.bloodType = bloodType;
	}

	public boolean isHasPreexistingCondition() {
		return hasPreexistingCondition;
	}

	public void setHasPreexistingCondition(boolean hasPreexistingCondition) {
		this.hasPreexistingCondition = hasPreexistingCondition;
	}

	public String getConditionDescription() {
		return conditionDescription;
	}

	public void setConditionDescription(String conditionDescription) {
		this.conditionDescription = conditionDescription;
	}

	public boolean isHasDisability() {
		return hasDisability;
	}

	public void setHasDisability(boolean hasDisability) {
		this.hasDisability = hasDisability;
	}

	public String getDisabilityDescription() {
		return disabilityDescription;
	}

	public void setDisabilityDescription(String disabilityDescription) {
		this.disabilityDescription = disabilityDescription;
	}

	public BloodType getBloodType() {
		return bloodType;
	}

	public void setBloodType(BloodType bloodType) {
		this.bloodType = bloodType;
	}
}
