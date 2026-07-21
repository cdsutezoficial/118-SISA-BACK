package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link Generation} — same shape as
 * {@code AcademicPeriodRepository}: {@link #save}, an existence lookup for
 * the aggregate's uniqueness rule ({@link #findByProgramIdAndNumber}, scoped
 * to the denormalized {@code programId} rather than {@code planId} — PO
 * confirmed 2026-07-20 that {@code number} is a per-program, not per-plan,
 * consecutive counter), {@link #findById}, and a filterable paginated
 * {@link #search(GenerationSearchCriteria)}.
 */
public interface GenerationRepository {

	Generation save(Generation generation);

	/**
	 * Backs the {@code (programId, number)} uniqueness rule (plan §3/§4 —
	 * PO-confirmed 2026-07-20: no uniqueness on {@code (programId, year)},
	 * only on the {@code number} consecutive itself).
	 */
	Optional<Generation> findByProgramIdAndNumber(UUID programId, int number);

	Optional<Generation> findById(UUID id);

	/**
	 * Filterable, paginated query backing {@code ListGenerationsUseCase}.
	 */
	GenerationSearchPage search(GenerationSearchCriteria criteria);

	/**
	 * @param status    optional — filters to generations with this exact status
	 * @param search    optional free-text match against {@code code}
	 * @param programId optional — filters to generations belonging to this program
	 * @param page      zero-based page index
	 * @param size      page size
	 */
	record GenerationSearchCriteria(GenerationStatus status, String search, UUID programId, int page, int size) {
	}

	/**
	 * @param content       the {@link Generation} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record GenerationSearchPage(List<Generation> content, long totalElements, int totalPages) {
	}
}
