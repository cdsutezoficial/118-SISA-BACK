package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One entry of the {@code entries} list in
 * {@link SetGradeScaleRequest#entries()} (design.md — REST endpoints).
 * Coverage/gap/overlap validation against the enclosing scale's
 * {@code numericMin}/{@code numericMax} is enforced by
 * {@code AcademicPlan.setGradeScale}/{@code updateGradeScale}, not bean
 * validation here.
 */
public record GradeScaleEntryRequest(@NotNull BigDecimal fromValue, @NotNull BigDecimal toValue,
		@NotBlank String letter, String description, boolean passed) {
}
