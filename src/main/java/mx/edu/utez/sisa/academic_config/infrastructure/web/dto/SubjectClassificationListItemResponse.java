package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;

import java.util.UUID;

/**
 * A single row of {@code GET /subject-classifications}.
 */
public record SubjectClassificationListItemResponse(UUID id, String name, String code, ClassificationStatus status) {
}
