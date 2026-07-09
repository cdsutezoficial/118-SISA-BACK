package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;

import java.util.UUID;

/**
 * Request body for {@code POST /plans/{id}/levels/{levelId}/subjects}
 * (design.md — REST endpoints). {@code planLevelId} is taken from the path
 * (the enclosing {@code levelId} segment), not this body. {@code
 * isRetakeable} is nullable so an omitted value can default to {@code true}
 * (spec: "isRetakeable default true") — the controller applies that
 * default, since a primitive {@code boolean} field would otherwise default
 * to {@code false} when omitted from the JSON payload.
 * {@code classificationId} MUST be provided (spec: "classificationId MUST
 * be provided as a non-null UUID") but is not validated against an existing
 * record in this slice.
 */
public record AddSubjectRequest(@NotBlank String code, @NotBlank String name, int credits, int weeklyHours,
		int evaluationUnits, int displayOrder, @NotNull SubjectType type, Boolean isRetakeable,
		@NotNull UUID classificationId) {
}
