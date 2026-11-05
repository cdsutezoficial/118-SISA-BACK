package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.shared.model.BloodType;
import mx.edu.utez.sisa.shared.model.HealthData;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "candidate_health_profile")
public class CandidateHealthProfile {

	@Id
	@Column(name = "candidate_id")
	private UUID candidateId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "candidate_id")
	private Candidate candidate;

	@Embedded
	private HealthData data;

	protected CandidateHealthProfile() {
		// JPA
	}

	public CandidateHealthProfile(boolean hasPreexistingCondition, String conditionDescription, boolean hasDisability,
			String disabilityDescription, BloodType bloodType) {
		this.data = new HealthData(hasPreexistingCondition, conditionDescription, hasDisability, disabilityDescription,
				bloodType);
	}

	public UUID getCandidateId() {
		return candidateId;
	}

	public Candidate getCandidate() {
		return candidate;
	}

	public void setCandidate(Candidate candidate) {
		this.candidate = candidate;
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
		if (!(o instanceof CandidateHealthProfile that)) {
			return false;
		}
		return candidateId != null && candidateId.equals(that.candidateId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(candidateId);
	}
}
