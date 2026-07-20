package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;

import java.util.List;
import java.util.Optional;

/**
 * Persistence out-port for {@link SubjectClassification}. Phase 1 (List)
 * only needed the filterable, paginated {@link #search(ClassificationSearchCriteria)}
 * query — Phase 2 (Create) adds {@link #save(SubjectClassification)} and
 * {@link #findByCode(String)}. {@code findById} is still intentionally
 * omitted (YAGNI) and will be added in the phase that first needs it (Get by
 * id).
 */
public interface SubjectClassificationRepository {

	SubjectClassification save(SubjectClassification classification);

	/**
	 * Case-insensitive lookup by {@code code} — same convention as
	 * {@code AcademicDivisionRepository#findByCode}. Case-insensitivity is the
	 * adapter's responsibility ({@code findByCodeIgnoreCase} on the Spring
	 * Data repository); this port only declares the contract.
	 */
	Optional<SubjectClassification> findByCode(String code);

	/**
	 * Filterable, paginated query backing {@code ListSubjectClassificationsUseCase}.
	 */
	ClassificationSearchPage search(ClassificationSearchCriteria criteria);

	/**
	 * @param status optional — filters to classifications with this exact status
	 * @param search optional free-text match against {@code name} or {@code code}
	 * @param page   zero-based page index
	 * @param size   page size
	 */
	record ClassificationSearchCriteria(ClassificationStatus status, String search, int page, int size) {
	}

	/**
	 * @param content       the {@link SubjectClassification} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record ClassificationSearchPage(List<SubjectClassification> content, long totalElements, int totalPages) {
	}
}
