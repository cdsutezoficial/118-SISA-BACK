package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for {@code PUT /divisions/{id}} (design.md — REST endpoints).
 * {@code status} is deliberately absent — status transitions go through
 * {@code PATCH /divisions/{id}/status}.
 *
 * <p>Comparte exactamente las reglas de formato de
 * {@link CreateAcademicDivisionRequest}: {@code name} con letras y acentos
 * separados por espacios o guiones, {@code code} con sólo letras Unicode y
 * {@code description} como texto libre sin caracteres de control. Ambas clases
 * declaran los mismos patrones a propósito: si divergieran, una actualización
 * podría aceptar lo que un alta rechaza.
 *
 * <p>La clase de espacios del patrón de {@code name},
 * {@code [ \uFEFF\u00A0\u2000-\u200A]}, acepta rachas y espacios duros a
 * propósito, y el motivo está documentado en el create: el patrón se evalúa
 * antes que
 * {@link mx.edu.utez.sisa.academic_config.domain.service.AcademicDivisionTextNormalizer},
 * y si las rechazara el 400 se adelantaría a la compactación.
 */
public record UpdateAcademicDivisionRequest(
		@NotBlank @Size(max = 150) @Pattern(regexp = "^[ \\uFEFF\\u00A0\\u2000-\\u200A]*\\p{L}+(?:(?:[ \\uFEFF\\u00A0\\u2000-\\u200A]+|[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\u2010-\\u2015-][ \\uFEFF\\u00A0\\u2000-\\u200A]*)\\p{L}+)*[ \\uFEFF\\u00A0\\u2000-\\u200A]*$", message = "El nombre solo puede contener letras, espacios y guiones.") String name,
		@NotBlank @Size(min = 2, max = 12) @Pattern(regexp = "^\\p{L}+$", message = "La clave solo puede contener letras.") String code,
		@Size(max = 500) @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción contiene caracteres no válidos.") String description,
		UUID directorPersonId) {
}
