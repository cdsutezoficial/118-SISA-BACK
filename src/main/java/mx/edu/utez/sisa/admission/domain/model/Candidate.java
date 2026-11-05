package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "candidate", indexes = @Index(name = "ix_candidate_curp", columnList = "curp"))
public class Candidate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "admission_config_id", nullable = false)
	private UUID admissionConfigId;

	@Column(nullable = false, unique = true)
	private String folio;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CandidateStatus status;

	@Column(name = "llave_mx_verified", nullable = false)
	private boolean llaveMxVerified;

	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	@Column(name = "selected_by")
	private UUID selectedBy;

	@Column(name = "selected_at")
	private Instant selectedAt;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "is_first_choice", nullable = false)
	private boolean isFirstChoice;

	@Column(name = "outreach_channel_id")
	private UUID outreachChannelId;

	@Column(name = "is_enabled_for_induction", nullable = false)
	private boolean isEnabledForInduction;

	@Column(name = "ficha_payment_id")
	private UUID fichaPaymentId;

	@Column(name = "program_change_payment_id")
	private UUID programChangePaymentId;

	@Column(name = "valid_period_id")
	private UUID validPeriodId;

	@Column(nullable = false, length = 18)
	private String curp;

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name1", nullable = false)
	private String lastName1;

	@Column(name = "last_name2")
	private String lastName2;

	@Column(name = "personal_email")
	private String personalEmail;

	@Column(name = "mobile_phone")
	private String mobilePhone;

	@Column(name = "home_phone")
	private String homePhone;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Enumerated(EnumType.STRING)
	@Column
	private Gender gender;

	@Column
	private String nationality;

	@Column(name = "birth_state_id")
	private UUID birthStateId;

	@Column(name = "birth_municipality_id")
	private UUID birthMunicipalityId;

	@Column(name = "birth_country", length = 100)
	private String birthCountry;

	@Column(name = "birth_foreign_state")
	private String birthForeignState;

	@Column(name = "birth_foreign_municipality")
	private String birthForeignMunicipality;

	@Enumerated(EnumType.STRING)
	@Column(name = "marital_status")
	private MaritalStatus maritalStatus;

	@Column(name = "native_language")
	private String nativeLanguage;

	@Column(name = "has_children")
	private Boolean hasChildren;

	@Column(name = "monthly_family_income", precision = 12, scale = 2)
	private BigDecimal monthlyFamilyIncome;

	@Column(name = "guardian_name")
	private String guardianName;

	@Column(name = "guardian_email")
	private String guardianEmail;

	@Column(name = "emergency_contact_phone")
	private String emergencyContactPhone;

	@OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private CandidateAddress address;

	@OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private CandidateHealthProfile healthProfile;

	@OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private CandidateDiversityProfile diversityProfile;

	@OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private CandidateEmploymentInfo employmentInfo;

	@OneToOne(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private CandidateHighSchoolBackground highSchoolBackground;

	protected Candidate() {
		// JPA
	}

	public Candidate(UUID admissionConfigId, String folio, boolean llaveMxVerified, boolean isFirstChoice,
			UUID outreachChannelId) {
		this.admissionConfigId = admissionConfigId;
		this.folio = folio;
		this.status = CandidateStatus.REGISTERED;
		this.llaveMxVerified = llaveMxVerified;
		this.registeredAt = Instant.now();
		this.isFirstChoice = isFirstChoice;
		this.outreachChannelId = outreachChannelId;
		this.isEnabledForInduction = false;
	}

	public UUID getId() {
		return id;
	}

	public UUID getAdmissionConfigId() {
		return admissionConfigId;
	}

	public String getFolio() {
		return folio;
	}

	public CandidateStatus getStatus() {
		return status;
	}

	public boolean isLlaveMxVerified() {
		return llaveMxVerified;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

	public UUID getSelectedBy() {
		return selectedBy;
	}

	public Instant getSelectedAt() {
		return selectedAt;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public boolean isFirstChoice() {
		return isFirstChoice;
	}

	public UUID getOutreachChannelId() {
		return outreachChannelId;
	}

	public boolean isEnabledForInduction() {
		return isEnabledForInduction;
	}

	public UUID getFichaPaymentId() {
		return fichaPaymentId;
	}

	public UUID getProgramChangePaymentId() {
		return programChangePaymentId;
	}

	public UUID getValidPeriodId() {
		return validPeriodId;
	}

	public String getCurp() {
		return curp;
	}

	public void setCurp(String curp) {
		this.curp = curp;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName1() {
		return lastName1;
	}

	public void setLastName1(String lastName1) {
		this.lastName1 = lastName1;
	}

	public String getLastName2() {
		return lastName2;
	}

	public void setLastName2(String lastName2) {
		this.lastName2 = lastName2;
	}

	public String getPersonalEmail() {
		return personalEmail;
	}

	public void setPersonalEmail(String personalEmail) {
		this.personalEmail = personalEmail;
	}

	public String getMobilePhone() {
		return mobilePhone;
	}

	public void setMobilePhone(String mobilePhone) {
		this.mobilePhone = mobilePhone;
	}

	public String getHomePhone() {
		return homePhone;
	}

	public void setHomePhone(String homePhone) {
		this.homePhone = homePhone;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public void setBirthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
	}

	public Gender getGender() {
		return gender;
	}

	public void setGender(Gender gender) {
		this.gender = gender;
	}

	public String getNationality() {
		return nationality;
	}

	public void setNationality(String nationality) {
		this.nationality = nationality;
	}

	public UUID getBirthStateId() {
		return birthStateId;
	}

	public void setBirthStateId(UUID birthStateId) {
		this.birthStateId = birthStateId;
	}

	public UUID getBirthMunicipalityId() {
		return birthMunicipalityId;
	}

	public void setBirthMunicipalityId(UUID birthMunicipalityId) {
		this.birthMunicipalityId = birthMunicipalityId;
	}

	public String getBirthCountry() {
		return birthCountry;
	}

	public void setBirthCountry(String birthCountry) {
		this.birthCountry = birthCountry;
	}

	public String getBirthForeignState() {
		return birthForeignState;
	}

	public void setBirthForeignState(String birthForeignState) {
		this.birthForeignState = birthForeignState;
	}

	public String getBirthForeignMunicipality() {
		return birthForeignMunicipality;
	}

	public void setBirthForeignMunicipality(String birthForeignMunicipality) {
		this.birthForeignMunicipality = birthForeignMunicipality;
	}

	public MaritalStatus getMaritalStatus() {
		return maritalStatus;
	}

	public void setMaritalStatus(MaritalStatus maritalStatus) {
		this.maritalStatus = maritalStatus;
	}

	public String getNativeLanguage() {
		return nativeLanguage;
	}

	public void setNativeLanguage(String nativeLanguage) {
		this.nativeLanguage = nativeLanguage;
	}

	public Boolean getHasChildren() {
		return hasChildren;
	}

	public void setHasChildren(Boolean hasChildren) {
		this.hasChildren = hasChildren;
	}

	public BigDecimal getMonthlyFamilyIncome() {
		return monthlyFamilyIncome;
	}

	public void setMonthlyFamilyIncome(BigDecimal monthlyFamilyIncome) {
		this.monthlyFamilyIncome = monthlyFamilyIncome;
	}

	public String getGuardianName() {
		return guardianName;
	}

	public void setGuardianName(String guardianName) {
		this.guardianName = guardianName;
	}

	public String getGuardianEmail() {
		return guardianEmail;
	}

	public void setGuardianEmail(String guardianEmail) {
		this.guardianEmail = guardianEmail;
	}

	public String getEmergencyContactPhone() {
		return emergencyContactPhone;
	}

	public void setEmergencyContactPhone(String emergencyContactPhone) {
		this.emergencyContactPhone = emergencyContactPhone;
	}

	public CandidateAddress getAddress() {
		return address;
	}

	public void setAddress(CandidateAddress address) {
		if (address != null) {
			address.setCandidate(this);
		}
		this.address = address;
	}

	public CandidateHealthProfile getHealthProfile() {
		return healthProfile;
	}

	public void setHealthProfile(CandidateHealthProfile healthProfile) {
		if (healthProfile != null) {
			healthProfile.setCandidate(this);
		}
		this.healthProfile = healthProfile;
	}

	public CandidateDiversityProfile getDiversityProfile() {
		return diversityProfile;
	}

	public void setDiversityProfile(CandidateDiversityProfile diversityProfile) {
		if (diversityProfile != null) {
			diversityProfile.setCandidate(this);
		}
		this.diversityProfile = diversityProfile;
	}

	public CandidateEmploymentInfo getEmploymentInfo() {
		return employmentInfo;
	}

	public void setEmploymentInfo(CandidateEmploymentInfo employmentInfo) {
		if (employmentInfo != null) {
			employmentInfo.setCandidate(this);
		}
		this.employmentInfo = employmentInfo;
	}

	public CandidateHighSchoolBackground getHighSchoolBackground() {
		return highSchoolBackground;
	}

	public void setHighSchoolBackground(CandidateHighSchoolBackground highSchoolBackground) {
		if (highSchoolBackground != null) {
			highSchoolBackground.setCandidate(this);
		}
		this.highSchoolBackground = highSchoolBackground;
	}

	public void updateSelection(UUID outreachChannelId, boolean isFirstChoice) {
		this.outreachChannelId = outreachChannelId;
		this.isFirstChoice = isFirstChoice;
	}

	public void changeAdmissionConfig(UUID admissionConfigId) {
		this.admissionConfigId = admissionConfigId;
	}

	public UUID collectingPaymentId() {
		return programChangePaymentId != null ? programChangePaymentId : fichaPaymentId;
	}

	public void awaitProgramChangePayment(UUID paymentId) {
		this.programChangePaymentId = paymentId;
	}

	public void adoptFichaPayment(UUID paymentId) {
		this.fichaPaymentId = paymentId;
		this.programChangePaymentId = null;
	}

	public void linkFichaPayment(UUID paymentId) {
		if (this.fichaPaymentId != null && !this.fichaPaymentId.equals(paymentId)) {
			throw new IllegalStateException("La ficha ya está ligada a otro cargo: " + this.fichaPaymentId);
		}
		this.fichaPaymentId = paymentId;
	}

	public void markPaid(UUID periodId) {
		if (this.status != CandidateStatus.PAID) {
			this.status = CandidateStatus.PAID;
			this.paidAt = Instant.now();
		}
		this.validPeriodId = periodId;
	}

	public void markPaid() {
		markPaid(null);
	}

	public void setValidPeriodId(UUID validPeriodId) {
		this.validPeriodId = validPeriodId;
	}

	public LocalDate registeredOn(ZoneId zone) {
		return registeredAt.atZone(zone).toLocalDate();
	}

	public LocalDate paymentDeadline(ZoneId zone, int deadlineDays) {
		return registeredOn(zone).plusDays(deadlineDays);
	}

	public boolean markPaymentExpired() {
		if (this.status != CandidateStatus.REGISTERED) {
			return false;
		}
		this.status = CandidateStatus.PAYMENT_EXPIRED;
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Candidate candidate)) {
			return false;
		}
		return id != null && id.equals(candidate.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
