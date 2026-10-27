package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.DivisionStatus;

import java.util.UUID;

/**
 * A single row of {@code GET /divisions} (spec: "List Academic Divisions
 * (Paginated)"). {@code programCount} is the real number of
 * {@code AcademicProgram}s referencing the division (grouped count per page),
 * no longer the hardcoded stub kept pending HU-PROG-010.
 */
public record AcademicDivisionListItemResponse(UUID id, String name, String code, String description,
		UUID directorPersonId, DivisionStatus status, int programCount) {
}
