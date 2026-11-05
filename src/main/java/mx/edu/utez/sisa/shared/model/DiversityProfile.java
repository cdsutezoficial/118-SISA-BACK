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
 * Diversity/self-identification profile for a {@link Person}, one of 5
 * normalized child tables added in Fase B (see {@link Address} javadoc for
 * the full design rationale — shared-primary-key {@code @OneToOne}, no
 * repository, reached only via {@code Person#getDiversityProfile()}).
 */
@Entity
@Table(name = "person_diversity_profile")
public class DiversityProfile {

	@Id
	@Column(name = "person_id")
	private UUID personId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "person_id")
	private Person person;

	@Embedded
	private DiversityData data;

	protected DiversityProfile() {
		// JPA
	}

	public DiversityProfile(boolean parentsSpeakIndigenousLanguage, boolean speaksIndigenousLanguage,
			boolean selfIdentifiesIndigenous, boolean selfIdentifiesNonBinary, boolean belongsToLgbttiqCommunity,
			boolean isAfrodescendant, boolean selfIdentifiesAfrodescendant, String parentsIndigenousLanguage,
			String indigenousLanguage) {
		this.data = new DiversityData(parentsSpeakIndigenousLanguage, speaksIndigenousLanguage,
				selfIdentifiesIndigenous, selfIdentifiesNonBinary, belongsToLgbttiqCommunity, isAfrodescendant,
				selfIdentifiesAfrodescendant, parentsIndigenousLanguage, indigenousLanguage);
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
		if (!(o instanceof DiversityProfile that)) {
			return false;
		}
		return personId != null && personId.equals(that.personId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(personId);
	}
}
