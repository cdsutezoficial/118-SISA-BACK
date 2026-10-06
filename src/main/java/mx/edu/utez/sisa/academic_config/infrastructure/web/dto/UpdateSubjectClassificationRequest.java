package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /subject-classifications/{id}} (Phase 4 —
 * "Actualización"). {@code status} is deliberately absent — status
 * transitions go through {@code PATCH /subject-classifications/{id}/status}
 * (Phase 5).
 *
 * <p>Las reglas de formato son las mismas de
 * {@link CreateSubjectClassificationRequest}, y el import de referencia en el
 * javadoc es a propósito: si las dos clases divergieran, una actualización podría
 * aceptar lo que un alta rechaza, que es la forma más difícil de detectar de un
 * contrato roto. La expresión de cada campo está duplicada literalmente porque
 * una constante compartida obligaría a los dos DTOs a importar de un paquete
 * distinto al que ya usan para el resto de anotaciones.
 *
 * <p>Cada campo lleva un único {@code @Pattern} y no un {@code @Pattern.List}:
 * sobre los componentes de un {@code record} la versión repetible se propaga
 * también como anotación de tipo, y Hibernate Validator la encuentra duplicada y
 * lanza {@code AnnotationFormatError} (500 en lugar de 400).
 */
public record UpdateSubjectClassificationRequest(
		@NotBlank @Size(max = 150) @Pattern(regexp = "^[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\p{L}\\p{N}]+(?:(?:[ \\uFEFF\\u00A0\\u2000-\\u200A]+|[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\u2010-\\u2015-][ \\uFEFF\\u00A0\\u2000-\\u200A]*)[\\p{L}\\p{N}]+)*[ \\uFEFF\\u00A0\\u2000-\\u200A]*$", message = "El nombre solo puede contener letras, números, espacios y guiones.") String name,
		@NotBlank @Size(min = 2, max = 20) @Pattern(regexp = "^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$", message = "La clave solo puede contener letras, números y guiones.") String code) {
}