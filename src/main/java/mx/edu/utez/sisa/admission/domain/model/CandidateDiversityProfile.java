package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.shared.model.DiversityData;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "candidate_diversity_profile")
public class CandidateDiversityProfile {

	@Id
	@Column(name = "candidate_id")
	private UUID candidateId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "candidate_id")
	private Candidate candidate;

	@Embedded
	private DiversityData data;

	protected CandidateDiversityProfile() {
		// JPA
	}

	public CandidateDiversityProfile(boolean parentsSpeakIndigenousLanguage, boolean speaksIndigenousLanguage,
			boolean selfIdentifiesIndigenous, boolean selfIdentifiesNonBinary, boolean belongsToLgbttiqCommunity,
			boolean isAfrodescendant, boolean selfIdentifiesAfrodescendant, String parentsIndigenousLanguage,
			String indigenousLanguage) {
		this.data = new DiversityData(parentsSpeakIndigenousLanguage, speaksIndigenousLanguage,
				selfIdentifiesIndigenous, selfIdentifiesNonBinary, belongsToLgbttiqCommunity, isAfrodescendant,
				selfIdentifiesAfrodescendant, parentsIndigenousLanguage, indigenousLanguage);
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

	public boolean isParentsSpeakIndigenousLanguage() {
		return data != null && data.isParentsSpeakIndigenousLanguage();
	}

	public void setParentsSpeakIndigenousLanguage(boolean parentsSpeakIndigenousLanguage) {
		data().setParentsSpeakIndigenousLanguage(parentsSpeakIndigenousLanguage);
	}

	public boolean isSpeaksIndigenousLanguage() {
		return data != null && data.isSpeaksIndigenousLanguage();
	}

	public void setSpeaksIndigenousLanguage(boolean speaksIndigenousLanguage) {
		data().setSpeaksIndigenousLanguage(speaksIndigenousLanguage);
	}

	public boolean isSelfIdentifiesIndigenous() {
		return data != null && data.isSelfIdentifiesIndigenous();
	}

	public void setSelfIdentifiesIndigenous(boolean selfIdentifiesIndigenous) {
		data().setSelfIdentifiesIndigenous(selfIdentifiesIndigenous);
	}

	public boolean isSelfIdentifiesNonBinary() {
		return data != null && data.isSelfIdentifiesNonBinary();
	}

	public void setSelfIdentifiesNonBinary(boolean selfIdentifiesNonBinary) {
		data().setSelfIdentifiesNonBinary(selfIdentifiesNonBinary);
	}

	public boolean isBelongsToLgbttiqCommunity() {
		return data != null && data.isBelongsToLgbttiqCommunity();
	}

	public void setBelongsToLgbttiqCommunity(boolean belongsToLgbttiqCommunity) {
		data().setBelongsToLgbttiqCommunity(belongsToLgbttiqCommunity);
	}

	public boolean isAfrodescendant() {
		return data != null && data.isAfrodescendant();
	}

	public void setAfrodescendant(boolean afrodescendant) {
		data().setAfrodescendant(afrodescendant);
	}

	public boolean isSelfIdentifiesAfrodescendant() {
		return data != null && data.isSelfIdentifiesAfrodescendant();
	}

	public void setSelfIdentifiesAfrodescendant(boolean selfIdentifiesAfrodescendant) {
		data().setSelfIdentifiesAfrodescendant(selfIdentifiesAfrodescendant);
	}

	public String getParentsIndigenousLanguage() {
		return data == null ? null : data.getParentsIndigenousLanguage();
	}

	public void setParentsIndigenousLanguage(String parentsIndigenousLanguage) {
		data().setParentsIndigenousLanguage(parentsIndigenousLanguage);
	}

	public String getIndigenousLanguage() {
		return data == null ? null : data.getIndigenousLanguage();
	}

	public void setIndigenousLanguage(String indigenousLanguage) {
		data().setIndigenousLanguage(indigenousLanguage);
	}

	private DiversityData data() {
		if (data == null) {
			data = new DiversityData();
		}
		return data;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CandidateDiversityProfile that)) {
			return false;
		}
		return candidateId != null && candidateId.equals(that.candidateId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(candidateId);
	}
}
