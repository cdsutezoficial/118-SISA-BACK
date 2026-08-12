package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Health profile for a {@link Person}, one of 5 normalized child tables added
 * in Fase B (see {@link Address} javadoc for the full design rationale —
 * shared-primary-key {@code @OneToOne}, no repository, reached only via
 * {@code Person#getHealthProfile()}).
 */
@Entity
@Table(name = "person_health_profile")
public class HealthProfile {

	@Id
	@Column(name = "person_id")
	private UUID personId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "person_id")
	private Person person;

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

	protected HealthProfile() {
		// JPA
	}

	public HealthProfile(boolean hasPreexistingCondition, String conditionDescription, boolean hasDisability,
			String disabilityDescription, BloodType bloodType) {
		this.hasPreexistingCondition = hasPreexistingCondition;
		this.conditionDescription = conditionDescription;
		this.hasDisability = hasDisability;
		this.disabilityDescription = disabilityDescription;
		this.bloodType = bloodType;
	}

	public UUID getPersonId() {
		return personId;
	}

	public Person getPerson() {
		return person;
	}

	public void setPerson(Person person) {
		this.person = person;
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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof HealthProfile that)) {
			return false;
		}
		return personId != null && personId.equals(that.personId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(personId);
	}
}
