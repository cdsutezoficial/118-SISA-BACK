package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link ProgramAdmissionConfig} — same shape as
 * {@code GenerationRepository}: {@link #save}, an existence lookup for the
 * aggregate's uniqueness rule ({@link #findByProgramIdAndPeriodId}), {@link
 * #findById}, and a filterable paginated {@link #search(ProgramAdmissionConfigSearchCriteria)}.
 */
public interface ProgramAdmissionConfigRepository {

	ProgramAdmissionConfig save(ProgramAdmissionConfig config);

	/**
	 * Backs the {@code (programId, periodId)} uniqueness rule (docs:
	 * {@code 02-config-academica.md} line 224).
	 */
	Optional<ProgramAdmissionConfig> findByProgramIdAndPeriodId(UUID programId, UUID periodId);

	Optional<ProgramAdmissionConfig> findById(UUID id);

	/**
	 * Filterable, paginated query backing {@code ListProgramAdmissionConfigsUseCase}.
	 */
	ProgramAdmissionConfigSearchPage search(ProgramAdmissionConfigSearchCriteria criteria);

	/**
	 * @param status    optional — filters to configs with this exact status
	 * @param programId optional — filters to configs belonging to this program
	 * @param page      zero-based page index
	 * @param size      page size
	 */
	record ProgramAdmissionConfigSearchCriteria(ProgramAdmissionConfigStatus status, UUID programId, int page,
			int size) {
	}

	/**
	 * @param content       the {@link ProgramAdmissionConfig} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record ProgramAdmissionConfigSearchPage(List<ProgramAdmissionConfig> content, long totalElements, int totalPages) {
	}
}
