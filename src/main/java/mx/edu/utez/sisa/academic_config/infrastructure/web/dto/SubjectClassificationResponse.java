package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import mx.edu.utez.sisa.academic_config.domain.model.ClassificationStatus;

import java.util.UUID;

/**
 * Response body for {@code POST /subject-classifications} — mirrors
 * {@code AcademicDivisionResponse}'s "full post-operation state" convention.
 */
public record SubjectClassificationResponse(UUID id, String name, String code, ClassificationStatus status) {
}
