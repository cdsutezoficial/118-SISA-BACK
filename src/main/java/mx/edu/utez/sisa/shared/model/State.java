package mx.edu.utez.sisa.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * INEGI federal entity ("estado") — first of two closed, seed-only reference
 * catalogs (source: {@code 118-SISA-CLAUDE/docs/design/dominio/00-shared-kernel.md},
 * sections "State"/"Municipality"; plan:
 * {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md}). Lives
 * in {@code shared.model} — alongside {@link Person} — rather than any
 * bounded context because {@code Person} will reference it (Fase B of the
 * same plan, out of scope here).
 *
 * <p>No CRUD, no use cases, no ports: 32 rows are seeded once by
 * {@code StateAndMunicipalitySeedRunner} from
 * {@code classpath:seed/inegi_estados.csv} and never written to again — a
 * plain {@code JpaRepository} ({@code StateJpaRepository}) is the only
 * persistence access, consumed directly by the seed runner and the read-only
 * {@code StateController}.
 */
@Entity
@Table(name = "state")
public class State {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "inegi_code", nullable = false, unique = true, length = 2)
	private String inegiCode;

	protected State() {
		// JPA
	}

	public State(String name, String inegiCode) {
		this.name = name;
		this.inegiCode = inegiCode;
	}

	public UUID getId() {
		return id;
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
		if (!(o instanceof State state)) {
			return false;
		}
		return id != null && id.equals(state.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
