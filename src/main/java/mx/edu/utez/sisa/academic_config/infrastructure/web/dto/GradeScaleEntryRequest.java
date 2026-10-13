package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One entry of the {@code entries} list in
 * {@link SetGradeScaleRequest#entries()} (design.md — REST endpoints).
 * Coverage/gap/overlap validation against the enclosing scale's
 * {@code numericMin}/{@code numericMax} is enforced by
 * {@code AcademicPlan.setGradeScale}/{@code updateGradeScale}, not bean
 * validation here.
 *
 * <p>{@code fromValue} and {@code toValue} carry the same
 * {@code @Digits(integer = 4, fraction = 1)} as the scale bounds, for the same
 * reason: the columns are {@code precision = 5, scale = 1} and the missing
 * annotation turned an out-of-range value into a generic database 400 instead
 * of a field-level one.
 *
 * <p>{@code description} gained a {@code @NotBlank} it should always have had:
 * its column is {@code nullable = false} but the field carried no annotation,
 * so an empty or missing description passed validation and then failed in the
 * flush as a generic 400 naming no field — while the frontend had been
 * requiring it all along.
 *
 * <p>{@code letter} keeps its {@code @NotBlank} and gains {@code @Size}: the
 * column is a 255-length string, so a longer letter was a database error rather
 * than a field-level 400.
 */
public record GradeScaleEntryRequest(
		@NotNull(message = "El valor inicial del rango es obligatorio.") @Digits(integer = 4, fraction = 1, message = "El valor inicial del rango admite un decimal como máximo.") BigDecimal fromValue,
		@NotNull(message = "El valor final del rango es obligatorio.") @Digits(integer = 4, fraction = 1, message = "El valor final del rango admite un decimal como máximo.") BigDecimal toValue,
		@NotBlank(message = "La letra del rango es obligatoria.") @Size(max = 255, message = "La letra del rango no puede superar 255 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La letra del rango contiene caracteres no válidos.") String letter,
		@NotBlank(message = "La descripción del rango es obligatoria.") @Size(max = 255, message = "La descripción del rango no puede superar 255 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción del rango contiene caracteres no válidos.") String description,
		boolean passed) {
}
