package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.shared.model.EmploymentData;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "candidate_employment_info")
public class CandidateEmploymentInfo {

	@Id
	@Column(name = "candidate_id")
	private UUID candidateId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "candidate_id")
	private Candidate candidate;

	@Embedded
	private EmploymentData data;

	protected CandidateEmploymentInfo() {
		// JPA
	}

	public CandidateEmploymentInfo(boolean isEmployed, String employmentType, String companyName, String jobTitle,
			String workPhone, BigDecimal monthlyIncome, LocalTime workStartTime, LocalTime workEndTime) {
		this.data = new EmploymentData(isEmployed, employmentType, companyName, jobTitle, workPhone, monthlyIncome,
				workStartTime, workEndTime);
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

	public boolean isEmployed() {
		return data != null && data.isEmployed();
	}

	public void setEmployed(boolean employed) {
		data().setEmployed(employed);
	}

	public String getEmploymentType() {
		return data == null ? null : data.getEmploymentType();
	}

	public void setEmploymentType(String employmentType) {
		data().setEmploymentType(employmentType);
	}

	public String getCompanyName() {
		return data == null ? null : data.getCompanyName();
	}

	public void setCompanyName(String companyName) {
		data().setCompanyName(companyName);
	}

	public String getJobTitle() {
		return data == null ? null : data.getJobTitle();
	}

	public void setJobTitle(String jobTitle) {
		data().setJobTitle(jobTitle);
	}

	public String getWorkPhone() {
		return data == null ? null : data.getWorkPhone();
	}

	public void setWorkPhone(String workPhone) {
		data().setWorkPhone(workPhone);
	}

	public BigDecimal getMonthlyIncome() {
		return data == null ? null : data.getMonthlyIncome();
	}

	public void setMonthlyIncome(BigDecimal monthlyIncome) {
		data().setMonthlyIncome(monthlyIncome);
	}

	public LocalTime getWorkStartTime() {
		return data == null ? null : data.getWorkStartTime();
	}

	public void setWorkStartTime(LocalTime workStartTime) {
		data().setWorkStartTime(workStartTime);
	}

	public LocalTime getWorkEndTime() {
		return data == null ? null : data.getWorkEndTime();
	}

	public void setWorkEndTime(LocalTime workEndTime) {
		data().setWorkEndTime(workEndTime);
	}

	private EmploymentData data() {
		if (data == null) {
			data = new EmploymentData();
		}
		return data;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CandidateEmploymentInfo that)) {
			return false;
		}
		return candidateId != null && candidateId.equals(that.candidateId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(candidateId);
	}
}
