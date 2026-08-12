package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link HighSchoolType}. No {@code findByName}
 * lookup — same "no uniqueness constraint to enforce" rationale as
 * {@code OutreachChannelRepository}.
 */
public interface HighSchoolTypeRepository {

	HighSchoolType save(HighSchoolType highSchoolType);

	/**
	 * Backs {@code GetHighSchoolTypeUseCase} — same convention as
	 * {@code OutreachChannelRepository#findById}.
	 */
	Optional<HighSchoolType> findById(UUID id);

	/**
	 * Filterable, paginated query backing {@code ListHighSchoolTypesUseCase}.
	 */
	HighSchoolTypeSearchPage search(HighSchoolTypeSearchCriteria criteria);

	/**
	 * @param status optional — filters to types with this exact status
	 * @param search optional free-text match against {@code name}
	 * @param page   zero-based page index
	 * @param size   page size
	 */
	record HighSchoolTypeSearchCriteria(HighSchoolTypeStatus status, String search, int page, int size) {
	}

	/**
	 * @param content       the {@link HighSchoolType} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record HighSchoolTypeSearchPage(List<HighSchoolType> content, long totalElements, int totalPages) {
	}
}
