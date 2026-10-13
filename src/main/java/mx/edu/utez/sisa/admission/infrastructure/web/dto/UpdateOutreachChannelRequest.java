package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /outreach-channels/{id}}. {@code status} is
 * deliberately absent — status transitions go through
 * {@code PATCH /outreach-channels/{id}/status}.
 *
 * <p>Las tres reglas son idénticas a las de {@code CreateOutreachChannelRequest}
 * y por el mismo motivo: la normalización y la unicidad se comprueban en el caso
 * de uso, y el DTO sólo rechaza lo que ni siquiera tiene sentido normalizar.
 *
 * <p>El {@code @Pattern} es el laxo de control de caracteres
 * ({@code ^[^\p{Cc}]*$}), no el estricto de letras: un canal de difusión se nombra
 * de cualquier manera que la institución quiera, y hay nombres con dígitos o
 * signos que un patrón de {@code \p{L}} rechazaría sin que ningún documento del
 * dominio lo pida.
 *
 * <p>Ojo con el orden: el controller evalúa estas anotaciones <b>antes</b> que
 * {@code CatalogDisplayNameNormalizer}, así que el patrón tiene que aceptar todo
 * lo que el normalizador arregla. {@code ^[^\p{Cc}]*$} lo hace: el espacio duro
 * (U+00A0) y el BOM (U+FEFF) son whitespace, no caracteres de control.
 */
public record UpdateOutreachChannelRequest(
		@NotBlank(message = "El nombre del canal es obligatorio.") @Size(max = 150, message = "El nombre del canal no puede superar 150 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre del canal contiene caracteres no válidos.") String name) {
}
