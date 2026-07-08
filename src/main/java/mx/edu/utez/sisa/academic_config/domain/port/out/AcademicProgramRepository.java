package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link AcademicProgram}. Implemented by a JPA
 * adapter in Phase 5.
 */
public interface AcademicProgramRepository {

	AcademicProgram save(AcademicProgram program);

	Optional<AcademicProgram> findById(UUID id);

	/**
	 * Backs the {@code code} uniqueness check (spec: "code MUST be unique
	 * across all programs").
	 */
	Optional<AcademicProgram> findByCode(String code);

	/**
	 * Backs the {@code (offerName, modality)} composite uniqueness check
	 * (spec: "The pair (offerName, modality) MUST be unique across all
	 * programs"). Same {@code offerName} with a different {@code modality}
	 * MUST NOT be treated as a conflict.
	 */
	Optional<AcademicProgram> findByOfferNameAndModality(String offerName, ProgramModality modality);

	/**
	 * Filterable, paginated query backing {@code ListAcademicProgramsUseCase}.
	 */
	ProgramSearchPage search(ProgramSearchCriteria criteria);

	/**
	 * @param divisionId optional — filters to programs belonging to this division
	 * @param status     optional — filters to programs with this exact status
	 * @param search     optional free-text match against {@code name}, {@code offerName}, or
	 *                   {@code code}
	 * @param page       zero-based page index
	 * @param size       page size
	 */
	record ProgramSearchCriteria(UUID divisionId, ProgramStatus status, String search, int page, int size) {
	}

	/**
	 * @param content       the {@link AcademicProgram} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record ProgramSearchPage(List<AcademicProgram> content, long totalElements, int totalPages) {
	}
}
