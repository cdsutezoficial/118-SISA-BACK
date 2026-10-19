package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;
import java.util.UUID;

/**
 * Closed, seed-only SEPOMEX settlement catalog keyed by 5-digit postal code.
 *
 * <p>Each row is one settlement choice, not one postal code: the public
 * admission wizard must surface multiple settlements when a postal code fans
 * out to several colonias. State and municipality names stay owned by the
 * existing INEGI catalogs; this table stores only the UUID references and the
 * minimal settlement fields the form actually uses. The SEPOMEX settlement id
 * stays only as a technical per-postal-code deduplication key for the seed.
 */
@Entity
@Table(name = "postal_code_settlement",
		uniqueConstraints = @UniqueConstraint(columnNames = { "postal_code", "sepomex_settlement_id" }))
public class PostalCodeSettlement {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "postal_code", nullable = false, length = 5)
	private String postalCode;

	@Column(name = "settlement_name", nullable = false)
	private String settlementName;

	@Column(name = "locality_name")
	private String localityName;

	@Column(name = "state_id", nullable = false)
	private UUID stateId;

	@Column(name = "municipality_id", nullable = false)
	private UUID municipalityId;

	@Column(name = "sepomex_settlement_id", nullable = false, length = 4)
	private String sepomexSettlementId;

	protected PostalCodeSettlement() {
		// JPA
	}

	public PostalCodeSettlement(String postalCode, String settlementName, String localityName, UUID stateId,
			UUID municipalityId, String sepomexSettlementId) {
		this.postalCode = postalCode;
		this.settlementName = settlementName;
		this.localityName = localityName;
		this.stateId = stateId;
		this.municipalityId = municipalityId;
		this.sepomexSettlementId = sepomexSettlementId;
	}

	public UUID getId() {
		return id;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getSettlementName() {
		return settlementName;
	}

	public String getLocalityName() {
		return localityName;
	}

	public UUID getStateId() {
		return stateId;
	}

	public UUID getMunicipalityId() {
		return municipalityId;
	}

	public String getSepomexSettlementId() {
		return sepomexSettlementId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PostalCodeSettlement that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}