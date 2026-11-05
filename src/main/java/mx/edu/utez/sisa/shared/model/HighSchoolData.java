package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.UUID;

@Embeddable
public class HighSchoolData {

	@Column(name = "school_name")
	private String schoolName;

	@Column(name = "school_city", nullable = false)
	private String schoolCity;

	@Column(name = "school_type_id")
	private UUID schoolTypeId;

	@Column(precision = 4, scale = 2)
	private BigDecimal gpa;

	@Column(name = "studied_in_mexico", nullable = false)
	private boolean studiedInMexico;

	@Column(name = "school_state_id")
	private UUID schoolStateId;

	@Column(name = "school_municipality_id")
	private UUID schoolMunicipalityId;

	@Column
	private String cct;

	@Column(name = "cct_confirmed")
	private Boolean cctConfirmed;

	@Column(name = "foreign_country")
	private String foreignCountry;

	@Column(name = "school_foreign_state")
	private String schoolForeignState;

	@Column(name = "academic_area")
	private String academicArea;

	@Column(name = "study_period")
	private String studyPeriod;

	public HighSchoolData() {
		// JPA
	}

	public HighSchoolData(String schoolName, String schoolCity, UUID schoolTypeId, BigDecimal gpa,
			boolean studiedInMexico, UUID schoolStateId, UUID schoolMunicipalityId, String cct, Boolean cctConfirmed,
			String foreignCountry, String schoolForeignState, String academicArea, String studyPeriod) {
		this.schoolName = schoolName;
		this.schoolCity = normalizeSchoolCity(schoolCity);
		this.schoolTypeId = schoolTypeId;
		this.gpa = gpa;
		this.studiedInMexico = studiedInMexico;
		this.schoolStateId = schoolStateId;
		this.schoolMunicipalityId = schoolMunicipalityId;
		this.cct = cct;
		this.cctConfirmed = cctConfirmed;
		this.foreignCountry = foreignCountry;
		this.schoolForeignState = schoolForeignState;
		this.academicArea = academicArea;
		this.studyPeriod = studyPeriod;
	}

	public String getSchoolName() {
		return schoolName;
	}

	public void setSchoolName(String schoolName) {
		this.schoolName = schoolName;
	}

	public String getSchoolCity() {
		return schoolCity;
	}

	public void setSchoolCity(String schoolCity) {
		this.schoolCity = normalizeSchoolCity(schoolCity);
	}

	public UUID getSchoolTypeId() {
		return schoolTypeId;
	}

	public void setSchoolTypeId(UUID schoolTypeId) {
		this.schoolTypeId = schoolTypeId;
	}

	public BigDecimal getGpa() {
		return gpa;
	}

	public void setGpa(BigDecimal gpa) {
		this.gpa = gpa;
	}

	public boolean isStudiedInMexico() {
		return studiedInMexico;
	}

	public void setStudiedInMexico(boolean studiedInMexico) {
		this.studiedInMexico = studiedInMexico;
	}

	public UUID getSchoolStateId() {
		return schoolStateId;
	}

	public void setSchoolStateId(UUID schoolStateId) {
		this.schoolStateId = schoolStateId;
	}

	public UUID getSchoolMunicipalityId() {
		return schoolMunicipalityId;
	}

	public void setSchoolMunicipalityId(UUID schoolMunicipalityId) {
		this.schoolMunicipalityId = schoolMunicipalityId;
	}

	public String getCct() {
		return cct;
	}

	public void setCct(String cct) {
		this.cct = cct;
	}

	public Boolean getCctConfirmed() {
		return cctConfirmed;
	}

	public void setCctConfirmed(Boolean cctConfirmed) {
		this.cctConfirmed = cctConfirmed;
	}

	public String getForeignCountry() {
		return foreignCountry;
	}

	public void setForeignCountry(String foreignCountry) {
		this.foreignCountry = foreignCountry;
	}

	public String getSchoolForeignState() {
		return schoolForeignState;
	}

	public void setSchoolForeignState(String schoolForeignState) {
		this.schoolForeignState = schoolForeignState;
	}

	public String getAcademicArea() {
		return academicArea;
	}

	public void setAcademicArea(String academicArea) {
		this.academicArea = academicArea;
	}

	public String getStudyPeriod() {
		return studyPeriod;
	}

	public void setStudyPeriod(String studyPeriod) {
		this.studyPeriod = studyPeriod;
	}

	private static String normalizeSchoolCity(String schoolCity) {
		return schoolCity == null ? "" : schoolCity;
	}
}
