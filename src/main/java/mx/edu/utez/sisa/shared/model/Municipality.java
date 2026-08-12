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
 * INEGI municipality ("municipio") — second closed, seed-only reference
 * catalog, child of {@link State} (source:
 * {@code 118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md}; plan:
 * {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md}).
 * {@code stateId} is a plain {@code UUID} column with no {@code @ManyToOne}
 * relation — same convention as {@code AcademicProgram#divisionId} (no
 * cross-aggregate object references in this codebase).
 *
 * <p>{@code inegiCode} is unique per {@code stateId}, NOT globally: multiple
 * states legitimately reuse the same 3-digit code (e.g. "001" is the first
 * municipality of nearly every state), so the uniqueness constraint is
 * composite ({@code state_id}, {@code inegi_code}) rather than a single-column
 * {@code unique = true} like {@link State#getInegiCode()}.
 *
 * <p>2,469 rows are seeded once by {@code StateAndMunicipalitySeedRunner}
 * from {@code classpath:seed/inegi_municipios.csv} and never written to
 * again.
 */
@Entity
@Table(name = "municipality", uniqueConstraints = @UniqueConstraint(columnNames = { "state_id", "inegi_code" }))
public class Municipality {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "state_id", nullable = false)
	private UUID stateId;

	@Column(nullable = false)
	private String name;

	@Column(name = "inegi_code", nullable = false, length = 3)
	private String inegiCode;

	protected Municipality() {
		// JPA
	}

	public Municipality(UUID stateId, String name, String inegiCode) {
		this.stateId = stateId;
		this.name = name;
		this.inegiCode = inegiCode;
	}

	public UUID getId() {
		return id;
	}

	public UUID getStateId() {
		return stateId;
	}

	public String getName() {
		return name;
	}

	public String getInegiCode() {
		return inegiCode;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Municipality that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
