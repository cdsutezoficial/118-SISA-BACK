package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SelectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Paginated, filterable query for {@code ProgramAdmissionConfig} entries,
 * mirroring {@code ListGenerationsUseCase}'s convention. Filterable only by
 * {@code status} and {@code programId} (plan §5) — this aggregate has no
 * {@code code}/{@code name} field to back a free-text {@code search} filter.
 * Role authorization is enforced by {@code SecurityFilterConfig}, not here.
 */
public interface ListProgramAdmissionConfigsUseCase {

	ListProgramAdmissionConfigsResult listProgramAdmissionConfigs(ListProgramAdmissionConfigsQuery query);

	/**
	 * @param status    optional — matches the config's current status
	 * @param programId optional — filters to configs belonging to this program
	 * @param page      zero-based page index; negative values are normalized to 0
	 * @param size      page size; normalized to a minimum of 1 and capped at {@link #MAX_PAGE_SIZE}
	 */
	record ListProgramAdmissionConfigsQuery(ProgramAdmissionConfigStatus status, UUID programId, int page, int size) {

		public static final int DEFAULT_PAGE_SIZE = 20;

		public static final int MAX_PAGE_SIZE = 100;
	}

	record ListProgramAdmissionConfigsResult(List<ProgramAdmissionConfigSummary> items, long totalElements,
			int totalPages, int page, int size) {
	}

	record ProgramAdmissionConfigSummary(UUID id, UUID programId, UUID periodId, UUID targetGenerationId,
			boolean isOffered, int maxCandidates, Instant opensAt, Instant closesAt,
			ProgramAdmissionConfigStatus status, SelectionStatus selectionStatus) {
	}
}
