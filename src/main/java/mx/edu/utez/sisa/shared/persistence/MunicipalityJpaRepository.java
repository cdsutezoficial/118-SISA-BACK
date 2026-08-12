package mx.edu.utez.sisa.shared.persistence;

import mx.edu.utez.sisa.shared.model.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Plain Spring Data repository for {@link Municipality} — same
 * "no port/adapter split for closed reference data" rationale as
 * {@link StateJpaRepository}. Used directly by
 * {@code StateAndMunicipalitySeedRunner} and {@code MunicipalityController}.
 */
public interface MunicipalityJpaRepository extends JpaRepository<Municipality, UUID> {

	/**
	 * Backs {@code GET /municipalities?stateId=} — no state has more than a
	 * few hundred municipalities (Oaxaca, the extreme real case, has 570), so
	 * no pagination is needed.
	 */
	List<Municipality> findByStateIdOrderByNameAsc(UUID stateId);
}
