package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
 *
 * <p>{@code code} and {@code name} carry {@code @Size(max = 255)} because that
 * is the length Hibernate derives for their columns (no explicit
 * {@code length} on the entity); without it, an over-long value surfaces as a
 * generic "Data too long" database error rather than a field-level 400. They
 * also carry the same control-character {@code @Pattern} the plan's own text
 * fields already had — previously a subject code could be persisted with an
 * embedded newline, which breaks any {@code LIKE} search or CSV export over it.
 *
 * <p>The numeric fields carry {@code @Min} floors. They are all plain
 * {@code int} columns with no relational rule, so bean validation is enough:
 * {@code credits} and {@code weeklyHours} at 0, {@code evaluationUnits} and
 * {@code displayOrder} at 1 (both are counts where 0 is meaningless, and the
 * frontend already refused them). Without these, the backend accepted negative
 * credits.
 *
 * <p>Each floor gets its own message, naming its own field — messages are never
 * combined, because the frontend attributes them to the input that caused them.
 */
public record AddSubjectRequest(
		@NotBlank(message = "El código de la materia es obligatorio.") @Size(max = 255, message = "El código de la materia no puede superar 255 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El código de la materia contiene caracteres no válidos.") String code,
		@NotBlank(message = "El nombre de la materia es obligatorio.") @Size(max = 255, message = "El nombre de la materia no puede superar 255 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre de la materia contiene caracteres no válidos.") String name,
		@Min(value = 0, message = "Los créditos no pueden ser menores que 0.") int credits,
		@Min(value = 0, message = "Las horas semanales no pueden ser menores que 0.") int weeklyHours,
		@Min(value = 1, message = "Las unidades de evaluación deben ser mayores o iguales a 1.") int evaluationUnits,
		@Min(value = 1, message = "El orden en kardex debe ser mayor o igual a 1.") int displayOrder,
		@NotNull(message = "Selecciona el tipo de materia.") SubjectType type, Boolean isRetakeable,
		@NotNull(message = "Selecciona la clasificación.") UUID classificationId) {
}
