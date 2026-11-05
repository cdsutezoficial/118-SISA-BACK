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

	@Embedded
	private EmploymentData data;

	protected EmploymentInfo() {
		// JPA
	}

	public EmploymentInfo(boolean isEmployed, String employmentType, String companyName, String jobTitle,
			String workPhone, BigDecimal monthlyIncome, LocalTime workStartTime, LocalTime workEndTime) {
		this.data = new EmploymentData(isEmployed, employmentType, companyName, jobTitle, workPhone, monthlyIncome,
				workStartTime, workEndTime);
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
