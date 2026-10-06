package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /outreach-channels} (mirrors
 * {@code CreateSubjectClassificationRequest}). This catalog only has
 * {@code name}.
 *
 * <p>Los tres mensajes van explícitos, como en los DTOs de {@code academic_config}:
 * el {@code @NotBlank} pelado de Spring ("must not be blank") se cuela en el
 * {@code message} del 400 y no es copy de esta aplicación.
 *
 * <p>El {@code @Pattern} es el laxo de control de caracteres
 * ({@code ^[^\p{Cc}]*$}), no el estricto de letras que usa
 * {@code CreateAcademicDivisionRequest}. Un canal de difusión se nombra de
 * cualquier manera que la institución quiera, y hay nombres con dígitos o
 * signos —"Radio UTEZ 102.5", "TikTok"— que un patrón de {@code \p{L}}
 * rechazaría sin que ningún documento del dominio lo pida. Lo que sí se rechaza
 * son los caracteres de control, que no tienen lectura en pantalla y rompen la
 * búsqueda del listado.
 *
 * <p>El {@code @Size(max = 150)} es el mismo techo que
 * {@code CreateAcademicDivisionRequest.name}: es un nombre de catálogo y 150
 * sobra para cualquiera. El de la columna vive en la entidad, no aquí.
 *
 * <p>Ojo con el orden: el controller evalúa estas anotaciones <b>antes</b> que
 * {@code CatalogDisplayNameNormalizer}. Por eso el patrón tiene que aceptar
 * todo lo que el normalizador arregla, y {@code ^[^\p{Cc}]*$} lo hace: el espacio
 * duro (U+00A0) y el BOM (U+FEFF) no son caracteres de control, sólo whitespace,
 * y los compacta el normalizador en vez de rechazarlos aquí.
 */
public record CreateOutreachChannelRequest(
		@NotBlank(message = "El nombre del canal es obligatorio.") @Size(max = 150, message = "El nombre del canal no puede superar 150 caracteres.") @Pattern(regexp = "^[^\\p{Cc}]*$", message = "El nombre del canal contiene caracteres no válidos.") String name) {
}
