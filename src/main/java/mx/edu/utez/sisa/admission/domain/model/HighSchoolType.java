package mx.edu.utez.sisa.admission.domain.model;

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
 * High-school-type catalog aggregate root — second catalog of the
 * {@code admission} bounded context, Fase A of
 * {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md} (source:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md}, section
 * "HighSchoolType"). Represents the kind of high school a candidate comes
 * from (Conalep, Cobaem, Cecyte, Bachillerato General, etc.), consumed by the
 * future candidate registration form ({@code Candidate}, out of scope here).
 *
 * <p>Identical shape to {@link OutreachChannel} — no FKs, a single
 * {@code name} with NO uniqueness constraint (same criterion, the domain doc
 * does not ask for uniqueness on this catalog either) plus a plain
 * ACTIVE/INACTIVE status. The 5-use-case CRUD stack around it mirrors
 * {@code OutreachChannel}'s exactly.
 */
@Entity
@Table(name = "high_school_type")
public class HighSchoolType {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HighSchoolTypeStatus status;

	protected HighSchoolType() {
		// JPA
	}

	public HighSchoolType(String name) {
		this.name = name;
		this.status = HighSchoolTypeStatus.ACTIVE;
	}

	/**
	 * Updates the catalog's {@code name}. {@code status} is deliberately
	 * absent — status transitions are the sole responsibility of
	 * {@code ChangeHighSchoolTypeStatusUseCase}, same separation as
	 * {@code OutreachChannel#updateDetails}.
	 */
	public void updateDetails(String name) {
		this.name = name;
	}

	/**
	 * Transitions to {@code ACTIVE}. Idempotent — calling on an
	 * already-{@code ACTIVE} type is a no-op.
	 */
	public void activate() {
		this.status = HighSchoolTypeStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code INACTIVE}. Idempotent — calling on an
	 * already-{@code INACTIVE} type is a no-op. The record itself is never
	 * deleted.
	 */
	public void deactivate() {
		this.status = HighSchoolTypeStatus.INACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public HighSchoolTypeStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof HighSchoolType that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
