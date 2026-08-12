package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Employment info for a {@link Person}, one of 5 normalized child tables
 * added in Fase B (see {@link Address} javadoc for the full design rationale
 * — shared-primary-key {@code @OneToOne}, no repository, reached only via
 * {@code Person#getEmploymentInfo()}).
 */
@Entity
@Table(name = "person_employment_info")
public class EmploymentInfo {

	@Id
	@Column(name = "person_id")
	private UUID personId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "person_id")
	private Person person;

	@Column(name = "is_employed", nullable = false)
	private boolean isEmployed;

	@Enumerated(EnumType.STRING)
	@Column(name = "employment_type")
	private EmploymentType employmentType;

	@Column(name = "company_name")
	private String companyName;

	@Column(name = "job_title")
	private String jobTitle;

	@Column(name = "work_phone")
	private String workPhone;

	@Column(name = "monthly_income", precision = 12, scale = 2)
	private BigDecimal monthlyIncome;

	@Column(name = "work_start_time")
	private LocalTime workStartTime;

	@Column(name = "work_end_time")
	private LocalTime workEndTime;

	protected EmploymentInfo() {
		// JPA
	}

	public EmploymentInfo(boolean isEmployed, EmploymentType employmentType, String companyName, String jobTitle,
			String workPhone, BigDecimal monthlyIncome, LocalTime workStartTime, LocalTime workEndTime) {
		this.isEmployed = isEmployed;
		this.employmentType = employmentType;
		this.companyName = companyName;
		this.jobTitle = jobTitle;
		this.workPhone = workPhone;
		this.monthlyIncome = monthlyIncome;
		this.workStartTime = workStartTime;
		this.workEndTime = workEndTime;
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

	public boolean isEmployed() {
		return isEmployed;
	}

	public void setEmployed(boolean employed) {
		isEmployed = employed;
	}

	public EmploymentType getEmploymentType() {
		return employmentType;
	}

	public void setEmploymentType(EmploymentType employmentType) {
		this.employmentType = employmentType;
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public String getJobTitle() {
		return jobTitle;
	}

	public void setJobTitle(String jobTitle) {
		this.jobTitle = jobTitle;
	}

	public String getWorkPhone() {
		return workPhone;
	}

	public void setWorkPhone(String workPhone) {
		this.workPhone = workPhone;
	}

	public BigDecimal getMonthlyIncome() {
		return monthlyIncome;
	}

	public void setMonthlyIncome(BigDecimal monthlyIncome) {
		this.monthlyIncome = monthlyIncome;
	}

	public LocalTime getWorkStartTime() {
		return workStartTime;
	}

	public void setWorkStartTime(LocalTime workStartTime) {
		this.workStartTime = workStartTime;
	}

	public LocalTime getWorkEndTime() {
		return workEndTime;
	}

	public void setWorkEndTime(LocalTime workEndTime) {
		this.workEndTime = workEndTime;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof EmploymentInfo that)) {
			return false;
		}
		return personId != null && personId.equals(that.personId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(personId);
	}
}
