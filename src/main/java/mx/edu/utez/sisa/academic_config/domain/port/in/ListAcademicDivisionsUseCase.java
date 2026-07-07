package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;

import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code AcademicDivision} catalog entries
 * (spec: "List Academic Divisions (Paginated)"), mirroring the
 * {@code GET /users} convention. Role authorization is enforced by
 * {@code SecurityFilterConfig}, not here.
 */
public interface ListAcademicDivisionsUseCase {

	ListAcademicDivisionsResult listDivisions(ListAcademicDivisionsQuery query);

	/**
	 * @param status optional — matches the division's current status
	 * @param search optional free-text match against {@code name} or {@code code}
	 * @param page   zero-based page index; negative values are normalized to 0
	 * @param size   page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListAcademicDivisionsQuery(DivisionStatus status, String search, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListAcademicDivisionsResult(List<DivisionSummary> items, long totalElements, int totalPages, int page,
			int size) {
	}

	/**
	 * @param programCount hardcoded {@code 0} stub pending real {@code AcademicProgram} data (HU-PROG-010) —
	 *                      NOT a real aggregation query (spec: "Every item reports a stub programCount")
	 */
	record DivisionSummary(UUID id, String name, String code, String description, UUID directorPersonId,
			DivisionStatus status, int programCount) {
	}
}
