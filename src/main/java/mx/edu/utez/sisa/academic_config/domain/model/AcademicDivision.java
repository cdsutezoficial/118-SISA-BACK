package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Academic division aggregate root — the first capability of the
 * {@code academic_config} bounded context (spec: "Academic Division
 * Management"). Owns the ACTIVE/INACTIVE lifecycle; there is deliberately no
 * hard-delete operation (spec: "MUST be no operation that hard-deletes an
 * AcademicDivision").
 */
@Entity
@Table(name = "academic_division")
public class AcademicDivision {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String name;

	@Column(nullable = false, unique = true)
	private String code;

	@Column
	private String description;

	@Column
	private UUID directorPersonId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DivisionStatus status;

	protected AcademicDivision() {
		// JPA
	}

	/**
	 * @param directorPersonId optional — {@code null} is allowed (spec: "creation MUST still succeed")
	 */
	public AcademicDivision(String name, String code, String description, UUID directorPersonId) {
		this.name = name;
		this.code = code;
		this.description = description;
		this.directorPersonId = directorPersonId;
		this.status = DivisionStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code ACTIVE} (spec: "Reactivate an inactive
	 * division"). Idempotent — calling on an already-{@code ACTIVE} division
	 * is a no-op rather than an error.
	 */
	public void activate() {
		this.status = DivisionStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code INACTIVE} (spec: "Deactivate an active
	 * division"). Idempotent — calling on an already-{@code INACTIVE}
	 * division is a no-op. The record itself is never deleted.
	 */
	public void deactivate() {
		this.status = DivisionStatus.INACTIVE;
	}

	/**
	 * Updates the catalog fields (spec: "Update Academic Division").
	 * {@code status} is deliberately untouched — status transitions are the
	 * sole responsibility of {@link #activate()}/{@link #deactivate()}.
	 */
	public void updateDetails(String name, String code, String description, UUID directorPersonId) {
		this.name = name;
		this.code = code;
		this.description = description;
		this.directorPersonId = directorPersonId;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public UUID getDirectorPersonId() {
		return directorPersonId;
	}

	public DivisionStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AcademicDivision that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
