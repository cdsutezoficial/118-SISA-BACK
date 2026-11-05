package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.shared.model.HighSchoolData;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "candidate_high_school_background")
public class CandidateHighSchoolBackground {

	@Id
	@Column(name = "candidate_id")
	private UUID candidateId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "candidate_id")
	private Candidate candidate;

	@Embedded
	private HighSchoolData data;

	protected CandidateHighSchoolBackground() {
		// JPA
	}

	public CandidateHighSchoolBackground(String schoolName, String schoolCity, UUID schoolTypeId, BigDecimal gpa,
			boolean studiedInMexico, UUID schoolStateId, UUID schoolMunicipalityId, String cct, Boolean cctConfirmed,
			String foreignCountry, String schoolForeignState, String academicArea, String studyPeriod) {
		this.data = new HighSchoolData(schoolName, schoolCity, schoolTypeId, gpa, studiedInMexico, schoolStateId,
				schoolMunicipalityId, cct, cctConfirmed, foreignCountry, schoolForeignState, academicArea, studyPeriod);
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
		if (!(o instanceof CandidateHighSchoolBackground that)) {
			return false;
		}
		return candidateId != null && candidateId.equals(that.candidateId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(candidateId);
	}
}
