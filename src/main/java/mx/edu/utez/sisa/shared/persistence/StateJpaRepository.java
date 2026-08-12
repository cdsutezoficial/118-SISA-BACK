package mx.edu.utez.sisa.shared.persistence;

import mx.edu.utez.sisa.shared.model.State;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Plain Spring Data repository for {@link State} — no port/adapter split.
 * {@code State} is pure, closed reference data with no business logic (see
 * {@code State}'s javadoc), so the over-engineering a hexagonal out-port would
 * add is deliberately skipped, per
 * {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md}. Used
 * directly by {@code StateAndMunicipalitySeedRunner} and
 * {@code StateController}.
 */
public interface StateJpaRepository extends JpaRepository<State, UUID> {

	/**
	 * Backs {@code GET /states} — full catalog, alphabetical, no pagination
	 * (only 32 rows, fixed size).
	 */
	List<State> findAllByOrderByNameAsc();
}
