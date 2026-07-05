package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Minimal shared-kernel {@code Person} for the Identity module's first slice.
 * Only the fields Identity needs are modeled here; the full Person shape
 * (address, health profile, academic background, etc.) is owned by
 * Admission/Enrollment and will extend this entity when those modules land.
 *
 * <p>Person creation is out of scope for Identity (see design.md — "Person
 * creation is out of scope: load-only reference"): rows are seeded by
 * {@code AdminSeedRunner} or test fixtures, never by an Identity use case.
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
