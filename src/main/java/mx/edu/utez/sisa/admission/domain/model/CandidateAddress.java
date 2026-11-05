package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.shared.model.AddressData;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "candidate_address")
public class CandidateAddress {

	@Id
	@Column(name = "candidate_id")
	private UUID candidateId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "candidate_id")
	private Candidate candidate;

	@Embedded
	private AddressData data;

	protected CandidateAddress() {
		// JPA
	}

	public CandidateAddress(String street, String exteriorNumber, String interiorNumber, String neighborhood,
			String locality, String postalCode, UUID stateId, UUID municipalityId, String foreignState,
			String foreignMunicipality) {
		this.data = new AddressData(street, exteriorNumber, interiorNumber, neighborhood, locality, postalCode,
				stateId, municipalityId, foreignState, foreignMunicipality);
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
		if (!(o instanceof CandidateAddress that)) {
			return false;
		}
		return candidateId != null && candidateId.equals(that.candidateId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(candidateId);
	}
}
