package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Home address for a {@link Person}, one of 5 normalized child tables added
 * in Fase B (source: {@code 118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md};
 * plan: {@code docs/plans/2026-07-29-person-extension.md} — José's decision
 * of 2026-07-28: model each Person value object as its own table in a shared-
 * primary-key 1-to-1 relationship, rather than a JPA {@code @Embeddable}, so
 * "no address captured" resolves natively to {@code null} instead of an
 * ambiguous all-null embeddable).
 *
 * <p>{@code personId} is both this table's primary key and its foreign key
 * to {@code person} ({@code @MapsId}) — there is no independent repository:
 * this entity is only ever reached via {@code Person#getAddress()} (plan
 * §4, "sin repositorios propios para las 5 tablas nuevas").
 *
 * <p>{@code stateId}/{@code municipalityId} are plain {@code UUID} columns
 * with no JPA relationship — same convention as every other cross-aggregate
 * reference in this codebase (e.g. {@code Group#generationId}).
 */
@Entity
@Table(name = "person_address")
public class Address {

	@Id
	@Column(name = "person_id")
	private UUID personId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "person_id")
	private Person person;

	@Column(nullable = false)
	private String street;

	@Column(name = "exterior_number", nullable = false)
	private String exteriorNumber;

	@Column(name = "interior_number")
	private String interiorNumber;

	@Column
	private String neighborhood;

	@Column
	private String locality;

	@Column(name = "postal_code", nullable = false)
	private String postalCode;

	@Column(name = "state_id")
	private UUID stateId;

	@Column(name = "municipality_id")
	private UUID municipalityId;

	@Column(name = "foreign_state")
	private String foreignState;

	@Column(name = "foreign_municipality")
	private String foreignMunicipality;

	protected Address() {
		// JPA
	}

	public Address(String street, String exteriorNumber, String interiorNumber, String neighborhood, String locality,
			String postalCode, UUID stateId, UUID municipalityId, String foreignState, String foreignMunicipality) {
		this.street = street;
		this.exteriorNumber = exteriorNumber;
		this.interiorNumber = interiorNumber;
		this.neighborhood = neighborhood;
		this.locality = locality;
		this.postalCode = postalCode;
		this.stateId = stateId;
		this.municipalityId = municipalityId;
		this.foreignState = foreignState;
		this.foreignMunicipality = foreignMunicipality;
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

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getExteriorNumber() {
		return exteriorNumber;
	}

	public void setExteriorNumber(String exteriorNumber) {
		this.exteriorNumber = exteriorNumber;
	}

	public String getInteriorNumber() {
		return interiorNumber;
	}

	public void setInteriorNumber(String interiorNumber) {
		this.interiorNumber = interiorNumber;
	}

	public String getNeighborhood() {
		return neighborhood;
	}

	public void setNeighborhood(String neighborhood) {
		this.neighborhood = neighborhood;
	}

	public String getLocality() {
		return locality;
	}

	public void setLocality(String locality) {
		this.locality = locality;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public void setPostalCode(String postalCode) {
		this.postalCode = postalCode;
	}

	public UUID getStateId() {
		return stateId;
	}

	public void setStateId(UUID stateId) {
		this.stateId = stateId;
	}

	public UUID getMunicipalityId() {
		return municipalityId;
	}

	public void setMunicipalityId(UUID municipalityId) {
		this.municipalityId = municipalityId;
	}

	public String getForeignState() {
		return foreignState;
	}

	public void setForeignState(String foreignState) {
		this.foreignState = foreignState;
	}

	public String getForeignMunicipality() {
		return foreignMunicipality;
	}

	public void setForeignMunicipality(String foreignMunicipality) {
		this.foreignMunicipality = foreignMunicipality;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Address address)) {
			return false;
		}
		return personId != null && personId.equals(address.personId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(personId);
	}
}
