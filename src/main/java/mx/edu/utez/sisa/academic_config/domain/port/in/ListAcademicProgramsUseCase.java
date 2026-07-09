package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code AcademicProgram} catalog entries
 * (spec: "List Academic Programs (Paginated)"), mirroring the
 * {@code GET /divisions} convention. {@code divisionId} is a new filter
 * relative to {@code ListAcademicDivisionsUseCase} — programs belong to
 * divisions and staff need to see "programs in division X". Role
 * authorization is enforced by {@code SecurityFilterConfig}, not here.
 */
public interface ListAcademicProgramsUseCase {

	ListAcademicProgramsResult listPrograms(ListAcademicProgramsQuery query);

	/**
	 * @param status     optional — matches the program's current status
	 * @param search     optional free-text match against {@code name}, {@code offerName}, or
	 *                   {@code code}
	 * @param divisionId optional — filters to programs belonging to this division
	 * @param page       zero-based page index; negative values are normalized to 0
	 * @param size       page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListAcademicProgramsQuery(ProgramStatus status, String search, UUID divisionId, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListAcademicProgramsResult(List<ProgramSummary> items, long totalElements, int totalPages, int page,
			int size) {
	}

	/**
	 * Minimum fields needed for a list view (spec: "Each returned item MUST
	 * include the fields needed for a list view: at minimum id, divisionId,
	 * name, offerName, code, level, modality, and status").
	 */
	record ProgramSummary(UUID id, UUID divisionId, String name, String offerName, String code, AcademicLevel level,
			ProgramModality modality, String description, String dgpCode, ProgramStatus status) {
	}
}
