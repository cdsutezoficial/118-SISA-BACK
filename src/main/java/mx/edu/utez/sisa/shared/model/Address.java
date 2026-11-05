package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

	@Embedded
	private AddressData data;

	protected Address() {
		// JPA
	}

	public Address(String street, String exteriorNumber, String interiorNumber, String neighborhood, String locality,
			String postalCode, UUID stateId, UUID municipalityId, String foreignState, String foreignMunicipality) {
		this.data = new AddressData(street, exteriorNumber, interiorNumber, neighborhood, locality, postalCode,
				stateId, municipalityId, foreignState, foreignMunicipality);
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
		return data == null ? null : data.getStreet();
	}

	public void setStreet(String street) {
		data().setStreet(street);
	}

	public String getExteriorNumber() {
		return data == null ? null : data.getExteriorNumber();
	}

	public void setExteriorNumber(String exteriorNumber) {
		data().setExteriorNumber(exteriorNumber);
	}

	public String getInteriorNumber() {
		return data == null ? null : data.getInteriorNumber();
	}

	public void setInteriorNumber(String interiorNumber) {
		data().setInteriorNumber(interiorNumber);
	}

	public String getNeighborhood() {
		return data == null ? null : data.getNeighborhood();
	}

	public void setNeighborhood(String neighborhood) {
		data().setNeighborhood(neighborhood);
	}

	public String getLocality() {
		return data == null ? null : data.getLocality();
	}

	public void setLocality(String locality) {
		data().setLocality(locality);
	}

	public String getPostalCode() {
		return data == null ? null : data.getPostalCode();
	}

	public void setPostalCode(String postalCode) {
		data().setPostalCode(postalCode);
	}

	public UUID getStateId() {
		return data == null ? null : data.getStateId();
	}

	public void setStateId(UUID stateId) {
		data().setStateId(stateId);
	}

	public UUID getMunicipalityId() {
		return data == null ? null : data.getMunicipalityId();
	}

	public void setMunicipalityId(UUID municipalityId) {
		data().setMunicipalityId(municipalityId);
	}

	public String getForeignState() {
		return data == null ? null : data.getForeignState();
	}

	public void setForeignState(String foreignState) {
		data().setForeignState(foreignState);
	}

	public String getForeignMunicipality() {
		return data == null ? null : data.getForeignMunicipality();
	}

	public void setForeignMunicipality(String foreignMunicipality) {
		data().setForeignMunicipality(foreignMunicipality);
	}

	private AddressData data() {
		if (data == null) {
			data = new AddressData();
		}
		return data;
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
