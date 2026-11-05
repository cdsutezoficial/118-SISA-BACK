package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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

	@Embedded
	private HealthData data;

	protected HealthProfile() {
		// JPA
	}

	public HealthProfile(boolean hasPreexistingCondition, String conditionDescription, boolean hasDisability,
			String disabilityDescription, BloodType bloodType) {
		this.data = new HealthData(hasPreexistingCondition, conditionDescription, hasDisability,
				disabilityDescription, bloodType);
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
		return data != null && data.isHasPreexistingCondition();
	}

	public void setHasPreexistingCondition(boolean hasPreexistingCondition) {
		data().setHasPreexistingCondition(hasPreexistingCondition);
	}

	public String getConditionDescription() {
		return data == null ? null : data.getConditionDescription();
	}

	public void setConditionDescription(String conditionDescription) {
		data().setConditionDescription(conditionDescription);
	}

	public boolean isHasDisability() {
		return data != null && data.isHasDisability();
	}

	public void setHasDisability(boolean hasDisability) {
		data().setHasDisability(hasDisability);
	}

	public String getDisabilityDescription() {
		return data == null ? null : data.getDisabilityDescription();
	}

	public void setDisabilityDescription(String disabilityDescription) {
		data().setDisabilityDescription(disabilityDescription);
	}

	public BloodType getBloodType() {
		return data == null ? null : data.getBloodType();
	}

	public void setBloodType(BloodType bloodType) {
		data().setBloodType(bloodType);
	}

	private HealthData data() {
		if (data == null) {
			data = new HealthData();
		}
		return data;
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
