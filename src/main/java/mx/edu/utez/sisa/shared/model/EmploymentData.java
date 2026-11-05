package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.time.LocalTime;

@Embeddable
public class EmploymentData {

	@Column(name = "is_employed", nullable = false)
	private boolean isEmployed;

	@Column(name = "employment_type")
	private String employmentType;

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

	public EmploymentData() {
		// JPA
	}

	public EmploymentData(boolean isEmployed, String employmentType, String companyName, String jobTitle,
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

	public boolean isEmployed() {
		return isEmployed;
	}

	public void setEmployed(boolean employed) {
		isEmployed = employed;
	}

	public String getEmploymentType() {
		return employmentType;
	}

	public void setEmploymentType(String employmentType) {
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
}
