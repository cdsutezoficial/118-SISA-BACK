package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * High-school background for a {@link Person}, one of 5 normalized child
 * tables added in Fase B (see {@link Address} javadoc for the full design
 * rationale — shared-primary-key {@code @OneToOne}, no repository, reached
 * only via {@code Person#getHighSchoolBackground()}).
 *
 * <p>{@code schoolTypeId} is a plain {@code UUID} column referencing
 * {@code admission.HighSchoolType} — no JPA relationship, both because it is
 * a cross-bounded-context reference and per this codebase's general
 * no-cross-aggregate-object-reference convention.
 *
 * <p>{@code academicArea}/{@code studyPeriod} are captured by Inscripciones,
 * not Admisión — always {@code null} for now (plan §3).
 */
@Entity
@Table(name = "person_high_school_background")
public class HighSchoolBackground {

	@Id
	@Column(name = "person_id")
	private UUID personId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "person_id")
	private Person person;

	@Embedded
	private HighSchoolData data;

	protected HighSchoolBackground() {
		// JPA
	}

	public HighSchoolBackground(String schoolName, String schoolCity, UUID schoolTypeId, BigDecimal gpa,
			boolean studiedInMexico, UUID schoolStateId, UUID schoolMunicipalityId, String cct, Boolean cctConfirmed,
			String foreignCountry, String schoolForeignState, String academicArea, String studyPeriod) {
		this.data = new HighSchoolData(schoolName, schoolCity, schoolTypeId, gpa, studiedInMexico, schoolStateId,
				schoolMunicipalityId, cct, cctConfirmed, foreignCountry, schoolForeignState, academicArea, studyPeriod);
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

	public String getSchoolName() {
		return data == null ? null : data.getSchoolName();
	}

	public void setSchoolName(String schoolName) {
		data().setSchoolName(schoolName);
	}

	public String getSchoolCity() {
		return data == null ? null : data.getSchoolCity();
	}

	public void setSchoolCity(String schoolCity) {
		data().setSchoolCity(schoolCity);
	}

	public UUID getSchoolTypeId() {
		return data == null ? null : data.getSchoolTypeId();
	}

	public void setSchoolTypeId(UUID schoolTypeId) {
		data().setSchoolTypeId(schoolTypeId);
	}

	public BigDecimal getGpa() {
		return data == null ? null : data.getGpa();
	}

	public void setGpa(BigDecimal gpa) {
		data().setGpa(gpa);
	}

	public boolean isStudiedInMexico() {
		return data != null && data.isStudiedInMexico();
	}

	public void setStudiedInMexico(boolean studiedInMexico) {
		data().setStudiedInMexico(studiedInMexico);
	}

	public UUID getSchoolStateId() {
		return data == null ? null : data.getSchoolStateId();
	}

	public void setSchoolStateId(UUID schoolStateId) {
		data().setSchoolStateId(schoolStateId);
	}

	public UUID getSchoolMunicipalityId() {
		return data == null ? null : data.getSchoolMunicipalityId();
	}

	public void setSchoolMunicipalityId(UUID schoolMunicipalityId) {
		data().setSchoolMunicipalityId(schoolMunicipalityId);
	}

	public String getCct() {
		return data == null ? null : data.getCct();
	}

	public void setCct(String cct) {
		data().setCct(cct);
	}

	public Boolean getCctConfirmed() {
		return data == null ? null : data.getCctConfirmed();
	}

	public void setCctConfirmed(Boolean cctConfirmed) {
		data().setCctConfirmed(cctConfirmed);
	}

	public String getForeignCountry() {
		return data == null ? null : data.getForeignCountry();
	}

	public void setForeignCountry(String foreignCountry) {
		data().setForeignCountry(foreignCountry);
	}

	public String getSchoolForeignState() {
		return data == null ? null : data.getSchoolForeignState();
	}

	public void setSchoolForeignState(String schoolForeignState) {
		data().setSchoolForeignState(schoolForeignState);
	}

	public String getAcademicArea() {
		return data == null ? null : data.getAcademicArea();
	}

	public void setAcademicArea(String academicArea) {
		data().setAcademicArea(academicArea);
	}

	public String getStudyPeriod() {
		return data == null ? null : data.getStudyPeriod();
	}

	public void setStudyPeriod(String studyPeriod) {
		data().setStudyPeriod(studyPeriod);
	}

	private HighSchoolData data() {
		if (data == null) {
			data = new HighSchoolData();
		}
		return data;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof HighSchoolBackground that)) {
			return false;
		}
		return personId != null && personId.equals(that.personId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(personId);
	}
}
