package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code PUT /plans/{id}} (design.md — REST endpoints).
 * {@code programId} is deliberately absent — moving a plan to a different
 * program is not supported (spec: "programId MUST NOT be changeable via
 * update"). {@code status} is likewise absent — status transitions go
 * through {@code PATCH /plans/{id}/status}. {@code maxExtraordinaryExamsPerPeriod}
 * MUST be {@code >= 0} on update too (spec: "Update Academic Plan" lists it
 * among the updatable fields with no exemption from its floor) — see
 * {@link CreateAcademicPlanRequest} for why {@code @Min} is used here.
 *
 * <p>Shares every constraint, message and rationale with
 * {@link CreateAcademicPlanRequest}: the two records must stay in sync, since a
 * field the create rejects but the update accepts is a way to write an invalid
 * plan.
 */
public record UpdateAcademicPlanRequest(
		@NotBlank(message = "La versión del plan es obligatoria.") @Size(max = 50, message = "La versión no puede superar 50 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La versión contiene caracteres no válidos.") String version,
		@NotBlank(message = "El periodo de vigencia es obligatorio.") @Size(max = 100, message = "El periodo de vigencia no puede superar 100 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El periodo de vigencia contiene caracteres no válidos.") String validityPeriod,
		@NotBlank(message = "La clave de titulación es obligatoria.") @Size(max = 100, message = "La clave de titulación no puede superar 100 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La clave de titulación contiene caracteres no válidos.") String titulationKey,
		@NotNull(message = "La fecha de vigencia es requerida.") LocalDate effectiveFrom,
		@Min(value = 1, message = "El total de niveles debe ser mayor o igual a 1.") int totalLevels,
		@NotNull(message = "La calificación mínima aprobatoria es obligatoria.") @Digits(integer = 2, fraction = 1, message = "La calificación mínima aprobatoria admite un decimal como máximo.") BigDecimal minPassingGrade,
		@Min(value = 0, message = "Los exámenes extraordinarios por periodo no pueden ser menores que 0.") int maxExtraordinaryExamsPerPeriod,
		boolean requiresSocialService, UUID socialServiceMinLevelId) {
}
