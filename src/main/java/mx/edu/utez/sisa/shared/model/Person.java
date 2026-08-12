package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared-kernel {@code Person}. The original 5 fields (curp, firstName,
 * lastName1, lastName2, institutionalEmail) back the Identity module's
 * staff-onboarding flow ({@code CreatePersonUseCase}) and are untouched here.
 *
 * <p>Fase B (plan: {@code docs/plans/2026-07-29-person-extension.md}) adds
 * the remaining shared-kernel scalar fields plus 5 normalized child profiles
 * ({@link Address}, {@link HealthProfile}, {@link DiversityProfile},
 * {@link EmploymentInfo}, {@link HighSchoolBackground}) needed by the future
 * candidate-registration flow (Fase C, {@code RegisterCandidateUseCase}).
 * All new columns are nullable at the JPA level — "no nulo" for a candidate
 * is a Fase-C business rule enforced by that use case, not a schema
 * constraint on {@code Person} in general, since Identity's staff flow never
 * populates them.
 *
 * <p>Person creation is out of scope for Identity (see design.md — "Person
 * creation is out of scope: load-only reference") only in the historical
 * sense that motivated that note; {@code CreatePersonUseCase} is now the
 * real staff-onboarding entry point, and rows are also seeded by
 * {@code AdminSeedRunner}/test fixtures.
 */
@Entity
@Table(name = "person")
public class Person {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 18)
	private String curp;

	@Column(nullable = false)
	private String firstName;

	@Column(nullable = false)
	private String lastName1;

	@Column
	private String lastName2;

	@Column(unique = true)
	private String institutionalEmail;

	@Column(name = "personal_email")
	private String personalEmail;

	@Column(name = "mobile_phone")
	private String mobilePhone;

	@Column(name = "home_phone")
	private String homePhone;

	@Column
	private String nss;

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

	@OneToOne(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private Address address;

	@OneToOne(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private HealthProfile healthProfile;

	@OneToOne(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private DiversityProfile diversityProfile;

	@OneToOne(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private EmploymentInfo employmentInfo;

	@OneToOne(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY, optional = true)
	private HighSchoolBackground highSchoolBackground;

	protected Person() {
		// JPA
	}

	public Person(String curp, String firstName, String lastName1, String lastName2, String institutionalEmail) {
		this.curp = curp;
		this.firstName = firstName;
		this.lastName1 = lastName1;
		this.lastName2 = lastName2;
		this.institutionalEmail = institutionalEmail;
	}

	public UUID getId() {
		return id;
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

	public String getInstitutionalEmail() {
		return institutionalEmail;
	}

	public void setInstitutionalEmail(String institutionalEmail) {
		this.institutionalEmail = institutionalEmail;
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

	public String getNss() {
		return nss;
	}

	public void setNss(String nss) {
		this.nss = nss;
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

	public Address getAddress() {
		return address;
	}

	public void setAddress(Address address) {
		if (address != null) {
			address.setPerson(this);
		}
		this.address = address;
	}

	public HealthProfile getHealthProfile() {
		return healthProfile;
	}

	public void setHealthProfile(HealthProfile healthProfile) {
		if (healthProfile != null) {
			healthProfile.setPerson(this);
		}
		this.healthProfile = healthProfile;
	}

	public DiversityProfile getDiversityProfile() {
		return diversityProfile;
	}

	public void setDiversityProfile(DiversityProfile diversityProfile) {
		if (diversityProfile != null) {
			diversityProfile.setPerson(this);
		}
		this.diversityProfile = diversityProfile;
	}

	public EmploymentInfo getEmploymentInfo() {
		return employmentInfo;
	}

	public void setEmploymentInfo(EmploymentInfo employmentInfo) {
		if (employmentInfo != null) {
			employmentInfo.setPerson(this);
		}
		this.employmentInfo = employmentInfo;
	}

	public HighSchoolBackground getHighSchoolBackground() {
		return highSchoolBackground;
	}

	public void setHighSchoolBackground(HighSchoolBackground highSchoolBackground) {
		if (highSchoolBackground != null) {
			highSchoolBackground.setPerson(this);
		}
		this.highSchoolBackground = highSchoolBackground;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Person person)) {
			return false;
		}
		return id != null && id.equals(person.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
