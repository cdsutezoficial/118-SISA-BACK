package mx.edu.utez.sisa.admission.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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
 * {@code name} plus a plain ACTIVE/INACTIVE status. The 5-use-case CRUD stack
 * around it mirrors {@code OutreachChannel}'s exactly.
 *
 * <p><b>{@code name} gained a uniqueness constraint in Fase 10 (2026-10-05)</b>,
 * copying OutreachChannel. It shipped without one on the same grounds that one
 * did — the shared-kernel doc says only "no nulo", so a rule should not be
 * invented — but the plan of 2026-10-02 asks for uniqueness, and here it is the
 * right rule: this catalog lists the kinds of school a candidate comes from
 * (Conalep, Cecyte, Bachillerato Técnico), and two entries differing only in case
 * or accents would be indistinguishable to the person filling the registration
 * form. That last part is also why the collation's accent-insensitivity is a
 * feature here and not just an accident (see the {@code name} javadoc).
 */
@Entity
@Table(name = "high_school_type", uniqueConstraints = @UniqueConstraint(name = "uk_high_school_type_name", columnNames = "name"))
public class HighSchoolType {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/**
	 * 150 is the same ceiling {@code OutreachChannel.name} and
	 * {@code AcademicDivision.name} use for a catalog name, and far above any
	 * real entry ("Bachillerato Técnico" is 20). It matches the {@code @Size} on
	 * the DTOs.
	 *
	 * <p><b>The uniqueness of this column is whatever MySQL's collation makes
	 * it.</b> The project runs MySQL 8.4 and pins no charset or collation, so the
	 * table inherits {@code utf8mb4_0900_ai_ci}: case-insensitive <i>and</i>
	 * accent-insensitive. Here that is deliberate — {@code "Tecnico"} and
	 * {@code "Técnico"} are the same school type and must not be two entries — but
	 * it also means the index alone cannot police whitespace, because a
	 * {@code NO PAD} collation weighs a trailing space. That half is closed by
	 * {@code CatalogDisplayNameNormalizer} plus the Java check in
	 * {@code CreateHighSchoolTypeUseCaseImpl}, which compare the normalized value.
	 * The column is not uppercased on purpose: the name is displayed to the user.
	 */
	@Column(nullable = false, length = 150)
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
