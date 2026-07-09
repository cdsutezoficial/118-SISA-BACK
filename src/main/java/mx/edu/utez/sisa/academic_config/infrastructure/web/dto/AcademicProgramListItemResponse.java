package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * A single row of {@code GET /programs} (spec: "List Academic Programs
 * (Paginated)" — "Each returned item MUST include the fields needed for a
 * list view: at minimum id, divisionId, name, offerName, code, level,
 * modality, and status"). {@code description} and {@code dgpCode} are
 * included to mirror {@code ListAcademicProgramsUseCase.ProgramSummary} exactly.
 */
public record AcademicProgramListItemResponse(UUID id, UUID divisionId, String name, String offerName, String code,
		AcademicLevel level, ProgramModality modality, String description, String dgpCode, ProgramStatus status) {
}
