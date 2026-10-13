package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link HighSchoolType}. Has a
 * {@code findByName} for the same reason {@code OutreachChannelRepository} gained
 * one in Fase 9: the normalized name is unique and has to be checked before
 * saving.
 */
public interface HighSchoolTypeRepository {

	HighSchoolType save(HighSchoolType highSchoolType);

	/**
	 * Backs {@code GetHighSchoolTypeUseCase} — same convention as
	 * {@code OutreachChannelRepository#findById}.
	 */
	Optional<HighSchoolType> findById(UUID id);

	/**
	 * Uniqueness lookup for {@code name}, backing the 409 of
	 * {@code CreateHighSchoolTypeUseCase} / {@code UpdateHighSchoolTypeUseCase}.
	 *
	 * <p><b>Deliberately not filtered by {@code status}:</b> the normalized name is
	 * unique across active <i>and</i> inactive rows, because a deactivated type
	 * still occupies its name. The registration form's option pickers filter by
	 * status, so two rows differing only in status would surface as two identical
	 * options to the applicant.
	 *
	 * @param name the already-normalized candidate name
	 */
	Optional<HighSchoolType> findByName(String name);

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
