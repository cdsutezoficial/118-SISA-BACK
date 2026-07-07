package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link AcademicDivision}. Implemented by a JPA
 * adapter in Phase 4.
 */
public interface AcademicDivisionRepository {

	AcademicDivision save(AcademicDivision division);

	Optional<AcademicDivision> findById(UUID id);

	/**
	 * Case-insensitive lookup by {@code code} (design.md — Testing Strategy:
	 * "case-insensitive uniqueness on name/code"). Case-insensitivity is the
	 * adapter's responsibility (e.g. {@code findByCodeIgnoreCase} on the
	 * Spring Data repository, Phase 4); this port only declares the
	 * contract.
	 */
	Optional<AcademicDivision> findByCode(String code);

	/**
	 * Case-insensitive lookup by {@code name} — see {@link #findByCode(String)}.
	 */
	Optional<AcademicDivision> findByName(String name);

	/**
	 * Filterable, paginated query backing {@code ListAcademicDivisionsUseCase}.
	 */
	DivisionSearchPage search(DivisionSearchCriteria criteria);

	/**
	 * @param status optional — filters to divisions with this exact status
	 * @param search optional free-text match against {@code name} or {@code code}
	 * @param page   zero-based page index
	 * @param size   page size
	 */
	record DivisionSearchCriteria(DivisionStatus status, String search, int page, int size) {
	}

	/**
	 * @param content       the {@link AcademicDivision} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record DivisionSearchPage(List<AcademicDivision> content, long totalElements, int totalPages) {
	}
}
