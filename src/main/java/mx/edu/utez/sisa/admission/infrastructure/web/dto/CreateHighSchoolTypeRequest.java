package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /high-school-types}. This catalog only has
 * {@code name}.
 *
 * <p>Reglas idénticas a las de {@code CreateOutreachChannelRequest}, y por el
 * mismo motivo: es otro catálogo de nombre visible del mismo contexto, así que
 * comparte contrato en vez de repetirlo con su propio criterio.
 *
 * <p>El {@code @Pattern} es el laxo de control de caracteres
 * ({@code ^[^\p{Cc}]*$}), no el estricto de letras: un tipo de bachillerato se
 * nombra de cualquier manera que la institución quiera —"Bachillerato Técnico",
 * "Cecyte", "Conalep"— y un patrón de {@code \p{L}} rechazaría los acentos y los
 * signos sin que ningún documento del dominio lo pida.
 *
 * <p>Ojo con el orden: el controller evalúa estas anotaciones <b>antes</b> que
 * {@code CatalogDisplayNameNormalizer}, así que el patrón tiene que aceptar todo
 * lo que el normalizador arregla. {@code ^[^\p{Cc}]*$} lo hace: el espacio duro
 * (U+00A0) y el BOM (U+FEFF) son whitespace, no caracteres de control.
 */
public record CreateHighSchoolTypeRequest(
		@NotBlank(message = "El nombre del tipo es obligatorio.") @Size(max = 150, message = "El nombre del tipo no puede superar 150 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre del tipo contiene caracteres no válidos.") String name) {
}
