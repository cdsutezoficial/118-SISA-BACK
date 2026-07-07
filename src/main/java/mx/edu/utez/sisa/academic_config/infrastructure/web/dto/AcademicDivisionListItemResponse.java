package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;

import java.util.UUID;

/**
 * A single row of {@code GET /divisions} (spec: "List Academic Divisions
 * (Paginated)"). {@code programCount} is a hardcoded {@code 0} stub pending
 * real {@code AcademicProgram} data (HU-PROG-010).
 */
public record AcademicDivisionListItemResponse(UUID id, String name, String code, String description,
		UUID directorPersonId, DivisionStatus status, int programCount) {
}
