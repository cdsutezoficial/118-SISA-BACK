package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.model.HighSchoolTypeStatus;

import java.util.UUID;

/**
 * A single row of {@code GET /high-school-types}.
 */
public record HighSchoolTypeListItemResponse(UUID id, String name, HighSchoolTypeStatus status) {
}
