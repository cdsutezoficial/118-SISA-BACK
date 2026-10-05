package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.edu.utez.sisa.academic_config.domain.model.SubjectType;

import java.util.UUID;

/**
 * Request body for {@code PUT
 * /plans/{id}/levels/{levelId}/subjects/{subjectId}} (design.md — REST
 * endpoints). Moving a subject to a different level is not supported in
 * this slice — mirrors {@code UpdateSubjectUseCase.UpdateSubjectCommand},
 * which has no {@code planLevelId} field.
 *
 * <p>Shares every constraint and message with {@link AddSubjectRequest}: the
 * two records must stay in sync, since a field the create accepts but the
 * update rejects is a confusing asymmetry.
 */
public record UpdateSubjectRequest(
		@NotBlank(message = "El código de la materia es obligatorio.") @Size(max = 255, message = "El código de la materia no puede superar 255 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El código de la materia contiene caracteres no válidos.") String code,
		@NotBlank(message = "El nombre de la materia es obligatorio.") @Size(max = 255, message = "El nombre de la materia no puede superar 255 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre de la materia contiene caracteres no válidos.") String name,
		@Min(value = 0, message = "Los créditos no pueden ser menores que 0.") int credits,
		@Min(value = 0, message = "Las horas semanales no pueden ser menores que 0.") int weeklyHours,
		@Min(value = 1, message = "Las unidades de evaluación deben ser mayores o iguales a 1.") int evaluationUnits,
		@Min(value = 1, message = "El orden en kardex debe ser mayor o igual a 1.") int displayOrder,
		@NotNull(message = "Selecciona el tipo de materia.") SubjectType type, Boolean isRetakeable,
		@NotNull(message = "Selecciona la clasificación.") UUID classificationId) {
}
