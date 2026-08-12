package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
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

	@Column(name = "parents_speak_indigenous_language", nullable = false)
	private boolean parentsSpeakIndigenousLanguage;

	@Column(name = "speaks_indigenous_language", nullable = false)
	private boolean speaksIndigenousLanguage;

	@Column(name = "self_identifies_indigenous", nullable = false)
	private boolean selfIdentifiesIndigenous;

	@Column(name = "self_identifies_non_binary", nullable = false)
	private boolean selfIdentifiesNonBinary;

	@Column(name = "belongs_to_lgbttiq_community", nullable = false)
	private boolean belongsToLgbttiqCommunity;

	@Column(name = "is_afrodescendant", nullable = false)
	private boolean isAfrodescendant;

	@Column(name = "self_identifies_afrodescendant", nullable = false)
	private boolean selfIdentifiesAfrodescendant;

	@Column(name = "parents_indigenous_language")
	private String parentsIndigenousLanguage;

	@Column(name = "indigenous_language")
	private String indigenousLanguage;

	protected DiversityProfile() {
		// JPA
	}

	public DiversityProfile(boolean parentsSpeakIndigenousLanguage, boolean speaksIndigenousLanguage,
			boolean selfIdentifiesIndigenous, boolean selfIdentifiesNonBinary, boolean belongsToLgbttiqCommunity,
			boolean isAfrodescendant, boolean selfIdentifiesAfrodescendant, String parentsIndigenousLanguage,
			String indigenousLanguage) {
		this.parentsSpeakIndigenousLanguage = parentsSpeakIndigenousLanguage;
		this.speaksIndigenousLanguage = speaksIndigenousLanguage;
		this.selfIdentifiesIndigenous = selfIdentifiesIndigenous;
		this.selfIdentifiesNonBinary = selfIdentifiesNonBinary;
		this.belongsToLgbttiqCommunity = belongsToLgbttiqCommunity;
		this.isAfrodescendant = isAfrodescendant;
		this.selfIdentifiesAfrodescendant = selfIdentifiesAfrodescendant;
		this.parentsIndigenousLanguage = parentsIndigenousLanguage;
		this.indigenousLanguage = indigenousLanguage;
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
		return parentsSpeakIndigenousLanguage;
	}

	public void setParentsSpeakIndigenousLanguage(boolean parentsSpeakIndigenousLanguage) {
		this.parentsSpeakIndigenousLanguage = parentsSpeakIndigenousLanguage;
	}

	public boolean isSpeaksIndigenousLanguage() {
		return speaksIndigenousLanguage;
	}

	public void setSpeaksIndigenousLanguage(boolean speaksIndigenousLanguage) {
		this.speaksIndigenousLanguage = speaksIndigenousLanguage;
	}

	public boolean isSelfIdentifiesIndigenous() {
		return selfIdentifiesIndigenous;
	}

	public void setSelfIdentifiesIndigenous(boolean selfIdentifiesIndigenous) {
		this.selfIdentifiesIndigenous = selfIdentifiesIndigenous;
	}

	public boolean isSelfIdentifiesNonBinary() {
		return selfIdentifiesNonBinary;
	}

	public void setSelfIdentifiesNonBinary(boolean selfIdentifiesNonBinary) {
		this.selfIdentifiesNonBinary = selfIdentifiesNonBinary;
	}

	public boolean isBelongsToLgbttiqCommunity() {
		return belongsToLgbttiqCommunity;
	}

	public void setBelongsToLgbttiqCommunity(boolean belongsToLgbttiqCommunity) {
		this.belongsToLgbttiqCommunity = belongsToLgbttiqCommunity;
	}

	public boolean isAfrodescendant() {
		return isAfrodescendant;
	}

	public void setAfrodescendant(boolean afrodescendant) {
		isAfrodescendant = afrodescendant;
	}

	public boolean isSelfIdentifiesAfrodescendant() {
		return selfIdentifiesAfrodescendant;
	}

	public void setSelfIdentifiesAfrodescendant(boolean selfIdentifiesAfrodescendant) {
		this.selfIdentifiesAfrodescendant = selfIdentifiesAfrodescendant;
	}

	public String getParentsIndigenousLanguage() {
		return parentsIndigenousLanguage;
	}

	public void setParentsIndigenousLanguage(String parentsIndigenousLanguage) {
		this.parentsIndigenousLanguage = parentsIndigenousLanguage;
	}

	public String getIndigenousLanguage() {
		return indigenousLanguage;
	}

	public void setIndigenousLanguage(String indigenousLanguage) {
		this.indigenousLanguage = indigenousLanguage;
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
