package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /subject-classifications} (mirrors
 * {@code CreateAcademicDivisionRequest}). Unlike Division's request, there is
 * no optional director field — this catalog only has {@code name}/{@code code}.
 */
public record CreateSubjectClassificationRequest(@NotBlank String name, @NotBlank String code) {
}
