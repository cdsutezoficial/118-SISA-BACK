package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.Objects;
import java.util.UUID;

/**
 * Academic program aggregate root — the second capability of the
 * {@code academic_config} bounded context (spec: "Academic Program
 * Management", HU-PROG-010). Owns the ACTIVE/INACTIVE lifecycle; there is
 * deliberately no hard-delete operation (spec: "MUST be no operation that
 * hard-deletes an AcademicProgram"). {@code divisionId} is a plain
 * {@code UUID} column with no {@code @ManyToOne} relation (design.md —
 * Decision: matches {@code directorPersonId}'s precedent, no cross-aggregate
 * object references).
 */
@Entity
@Table(name = "academic_program", uniqueConstraints = @UniqueConstraint(columnNames = { "offer_name", "modality" }))
public class AcademicProgram {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID divisionId;

	@Column(nullable = false)
	private String name;

	@Column(name = "offer_name", nullable = false)
	private String offerName;

	@Column(nullable = false, unique = true)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AcademicLevel level;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProgramModality modality;

	@Column
	private UUID continuityProgramId;

	@Column
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProgramStatus status;

	protected AcademicProgram() {
		// JPA
	}

	/**
	 * @param divisionId          required — MUST reference an existing {@code AcademicDivision} (spec:
	 *                            "divisionId MUST be required")
	 * @param continuityProgramId optional — {@code null} is allowed; schema-only in this change, no
	 *                            validation or linking logic (spec: "continuityProgramId MAY be omitted
	 *                            or null at creation")
	 */
	public AcademicProgram(UUID divisionId, String name, String offerName, String code, AcademicLevel level,
			ProgramModality modality, UUID continuityProgramId, String description) {
		this.divisionId = divisionId;
		this.name = name;
		this.offerName = offerName;
		this.code = code;
		this.level = level;
		this.modality = modality;
		this.continuityProgramId = continuityProgramId;
		this.description = description;
		this.status = ProgramStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code ACTIVE} (spec: "Reactivate an inactive program").
	 * Idempotent — calling on an already-{@code ACTIVE} program is a no-op.
	 */
	public void activate() {
		this.status = ProgramStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code INACTIVE} (spec: "Deactivate an active program").
	 * Idempotent — calling on an already-{@code INACTIVE} program is a
	 * no-op. The record itself is never deleted.
	 */
	public void deactivate() {
		this.status = ProgramStatus.INACTIVE;
	}

	/**
	 * Updates the catalog fields (spec: "Update Academic Program").
	 * {@code status} is deliberately untouched — status transitions are the
	 * sole responsibility of {@link #activate()}/{@link #deactivate()}.
	 */
	public void updateDetails(UUID divisionId, String name, String offerName, String code, AcademicLevel level,
			ProgramModality modality, UUID continuityProgramId, String description) {
		this.divisionId = divisionId;
		this.name = name;
		this.offerName = offerName;
		this.code = code;
		this.level = level;
		this.modality = modality;
		this.continuityProgramId = continuityProgramId;
		this.description = description;
	}

	public UUID getId() {
		return id;
	}

	public UUID getDivisionId() {
		return divisionId;
	}

	public String getName() {
		return name;
	}

	public String getOfferName() {
		return offerName;
	}

	public String getCode() {
		return code;
	}

	public AcademicLevel getLevel() {
		return level;
	}

	public ProgramModality getModality() {
		return modality;
	}

	public UUID getContinuityProgramId() {
		return continuityProgramId;
	}

	public String getDescription() {
		return description;
	}

	public ProgramStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AcademicProgram that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
