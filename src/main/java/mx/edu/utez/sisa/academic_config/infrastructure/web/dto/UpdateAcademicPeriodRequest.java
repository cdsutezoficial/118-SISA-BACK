package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;

import java.time.LocalDate;

/**
 * Request body for {@code PUT /periods/{id}}. {@code status} is deliberately
 * absent — status transitions go through {@code PATCH /periods/{id}/status}.
 *
 * <p>El contrato de los campos es exactamente el de
 * {@link CreateAcademicPeriodRequest}, y por eso las expresiones y los límites se
 * mantienen iguales: {@code name} con letras Unicode, dígitos, espacios y
 * guiones hasta 150; {@code year} entre 1900 y 2100; {@code periodNumber} desde 1
 * sin tope; y las cuatro fechas obligatorias. No hay un motivo para que el
 * alta y la edición acepten cosas distintas —un usuario que no puede guardar un
 * periodo con un nombre no debería poder editarlo tampoco—.
 *
 * <p>El mismo campo {@code name} con el mismo {@code @Pattern} que en el alta
 * implica la misma división de responsabilidades: el patrón acepta lo que
 * {@link mx.edu.utez.sisa.academic_config.domain.service.AcademicPeriodTextNormalizer}
 * puede arreglar, porque la validación corre antes que el normalizador.
 */
public record UpdateAcademicPeriodRequest(@NotBlank(message = "El nombre del periodo es obligatorio.") @Size(max = 150, message = "El nombre no puede superar 150 caracteres.") @Pattern(regexp = "^[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\p{L}\\p{N}]+(?:(?:[ \\uFEFF\\u00A0\\u2000-\\u200A]+|[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\u2010-\\u2015-][ \\uFEFF\\u00A0\\u2000-\\u200A]*)[\\p{L}\\p{N}]+)*[ \\uFEFF\\u00A0\\u2000-\\u200A]*$", message = "El nombre solo puede contener letras, números, espacios y guiones.") String name,
		@Min(value = 1900, message = "El año debe estar entre 1900 y 2100.") @Max(value = 2100, message = "El año debe estar entre 1900 y 2100.") int year,
		@Min(value = 1, message = "El número de periodo debe ser mayor o igual a 1.") int periodNumber,
		@NotNull(message = "Debes seleccionar el tipo de periodo.") PeriodType type,
		@NotNull(message = "La fecha de inicio es requerida.") LocalDate startDate,
		@NotNull(message = "La fecha de fin es requerida.") LocalDate endDate,
		@NotNull(message = "La fecha de inicio de inscripciones es requerida.") LocalDate enrollmentStart,
		@NotNull(message = "La fecha de fin de inscripciones es requerida.") LocalDate enrollmentEnd) {
}