package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;

import java.util.UUID;

/**
 * Request body for {@code PUT
 * /plans/{id}/levels/{levelId}/subjects/{subjectId}} (design.md — REST
 * endpoints). Moving a subject to a different level is not supported in
 * this slice — mirrors {@code UpdateSubjectUseCase.UpdateSubjectCommand},
 * which has no {@code planLevelId} field.
 */
public record UpdateSubjectRequest(@NotBlank String code, @NotBlank String name, int credits, int weeklyHours,
		int evaluationUnits, int displayOrder, @NotNull SubjectType type, Boolean isRetakeable,
		@NotNull UUID classificationId) {
}
