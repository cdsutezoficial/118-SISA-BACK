package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class AddressData {

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

	public AddressData() {
		// JPA
	}

	public AddressData(String street, String exteriorNumber, String interiorNumber, String neighborhood,
			String locality, String postalCode, UUID stateId, UUID municipalityId, String foreignState,
			String foreignMunicipality) {
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
}
