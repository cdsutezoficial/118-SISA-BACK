package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /plans} (design.md — REST endpoints).
 * {@code programId} is required (spec: "programId MUST be required").
 * {@code socialServiceMinLevelId} MUST be {@code null} at creation
 * regardless of {@code requiresSocialService} (enforced by
 * {@code CreateAcademicPlanUseCaseImpl}, not bean validation, since it
 * depends on the other field's value). {@code maxExtraordinaryExamsPerPeriod}
 * MUST be {@code >= 0} (spec: "an integer >= 0") — enforced here via
 * {@code @Min}, unlike {@code minPassingGrade}'s range, since this is a
 * simple non-relational floor with no other field dependency, so bean
 * validation (already relied upon for {@code @NotBlank}/{@code @NotNull} on
 * this same record) is sufficient and guarantees a 400 through the existing
 * {@code MethodArgumentNotValidException} handler.
 *
 * <p><b>Every constraint carries its own Spanish message.</b> Without one,
 * Bean Validation falls back to {@code getDefaultMessage()} — {@code "must not
 * be blank"}, {@code "size must be between 0 and 50"} — which reaches a
 * Spanish UI in English. And the messages are deliberately
 * <b>one per field</b>: they are never combined, because the frontend maps
 * them back to the offending input by field.
 *
 * <p>{@code totalLevels} carries {@code @Min(1)} because without it a plan
 * with {@code totalLevels <= 0} can be created and is then permanently
 * unusable: every subsequent level insert is rejected by
 * {@code AddPlanLevelUseCaseImpl}'s {@code 1 <= levelNumber <= totalLevels}
 * check, so the plan can never be completed. The frontend already refused
 * those values; this closes the same hole on direct API calls.
 *
 * <p>{@code minPassingGrade}'s {@code @Digits} is a second line of defence
 * rather than the primary rule: the {@code [0, 10]} range lives in the use
 * case (it is relational), and this annotation only guarantees the value
 * cannot overflow {@code precision = 3, scale = 1} on the way to the column.
 */
public record CreateAcademicPlanRequest(
		@NotNull(message = "Debes seleccionar la carrera.") UUID programId,
		@NotBlank(message = "La versión del plan es obligatoria.") @Size(max = 50, message = "La versión no puede superar 50 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La versión contiene caracteres no válidos.") String version,
		@NotBlank(message = "El periodo de vigencia es obligatorio.") @Size(max = 100, message = "El periodo de vigencia no puede superar 100 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El periodo de vigencia contiene caracteres no válidos.") String validityPeriod,
		@NotBlank(message = "La clave de titulación es obligatoria.") @Size(max = 100, message = "La clave de titulación no puede superar 100 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La clave de titulación contiene caracteres no válidos.") String titulationKey,
		@NotNull(message = "La fecha de vigencia es requerida.") LocalDate effectiveFrom,
		@Min(value = 1, message = "El total de niveles debe ser mayor o igual a 1.") @Max(value = 15, message = "El total de niveles no puede ser mayor a 15.") int totalLevels,
		@NotNull(message = "La calificación mínima aprobatoria es obligatoria.") @Digits(integer = 2, fraction = 1, message = "La calificación mínima aprobatoria admite un decimal como máximo.") BigDecimal minPassingGrade,
		@Min(value = 0, message = "Los exámenes extraordinarios por periodo no pueden ser menores que 0.") @Max(value = 5, message = "Los exámenes extraordinarios por periodo no pueden ser mayores que 5.") int maxExtraordinaryExamsPerPeriod,
		boolean requiresSocialService, UUID socialServiceMinLevelId) {
}
