package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectClassification;

import java.util.List;

/**
 * Persistence out-port for {@link SubjectClassification}. Phase 1 (List)
 * only needs the filterable, paginated {@link #search(ClassificationSearchCriteria)}
 * query — {@code save}/{@code findById}/{@code findByCode} are intentionally
 * omitted here (YAGNI) and will be added in the phase that first needs them
 * (Create).
 */
public interface SubjectClassificationRepository {

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
