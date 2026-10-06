package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for {@code POST /divisions} (design.md — REST endpoints).
 * {@code directorPersonId} is optional (spec: "creation MUST still succeed"
 * without a director).
 *
 * <p>Formato de los campos, alineado con la regla de negocio del 2026-10-04 y
 * con el schema de {@code DivisionesForm}:
 *
 * <ul>
 * <li>{@code name} — letras y acentos separados por espacios o por guiones.
 * Sin dígitos, paréntesis ni comas. El separador de palabras es un espacio
 * simple o guiones con espacios opcionales alrededor, y se aceptan las cinco
 * variantes Unicode U+2010–U+2015 además del guion ASCII porque los nombres ya
 * guardados en la base las usan. Admite espacios en los extremos y espacios
 * duros porque {@code AcademicDivisionTextNormalizer} los limpia antes de
 * persistir; el tabulador y el salto de línea quedan fuera a propósito, ya que
 * {@code name} es un campo de una línea y ninguno de los dos es tecleable
 * ahí.</li>
 * <li>{@code code} — sólo letras Unicode, sin espacios, dígitos ni guiones. Se
 * admiten letras Unicode para que "Ñ" y las tildes valgan.</li>
 * <li>{@code description} — texto libre. Sólo se rechazan los caracteres de
 * control; el resto es contenido legítimo y React escapa al renderizar.</li>
 * </ul>
 *
 * <p>La expresión de {@code name} está duplicada literalmente en
 * {@link UpdateAcademicDivisionRequest} y en la regla
 * {@code lettersSpacesAndHyphens} del frontend. Deben coincidir carácter a
 * carácter: si divergieran, el navegador dejaría pasar lo que el servidor
 * rechaza con 400.
 *
 * <p><b>La clase de espacios es {@code [ \uFEFF\u00A0\u2000-\u200A]+} y no un
 * espacio suelto, a propósito.</b> Este {@code @Pattern} se evalúa en el controller,
 * antes que
 * {@link mx.edu.utez.sisa.academic_config.domain.service.AcademicDivisionTextNormalizer},
 * que es quien compacta las rachas. Si el patrón las rechazara, el 400 se
 * adelantaría a la limpieza y el backend nunca guardaría un texto limpio. La
 * compactación es la que limpia; el patrón sólo define la forma. Por eso
 * {@code "a" + 20 espacios + "b"} es un payload válido que se persiste como
 * {@code "a b"}.
 *
 * <p>Esa clase incluye el espacio duro {@code U+00A0} y los espacios Unicode
 * {@code U+2000}–{@code U+200A} porque el frontend sí los acepta: sin ellos, un
 * nombre pegado desde un navegador o un documento de Word pasaría la validación
 * del cliente y el servidor lo rechazaría con 400. También incluye
 * {@code U+FEFF} (BOM), que el normalizador elimina entero en lugar de volverlo
 * espacio para no partir una palabra por la mitad.
 *
 * <p>Cada campo lleva un único {@code @Pattern} y no un {@code @Pattern.List}:
 * sobre los componentes de un {@code record} la versión repetible se propaga
 * también como anotación de tipo, y Hibernate Validator la encuentra duplicada y
 * lanza {@code AnnotationFormatError} (500 en lugar de 400).
 */
public record CreateAcademicDivisionRequest(
		@NotBlank @Size(max = 150) @Pattern(regexp = "^[ \\uFEFF\\u00A0\\u2000-\\u200A]*\\p{L}+(?:(?:[ \\uFEFF\\u00A0\\u2000-\\u200A]+|[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\u2010-\\u2015-][ \\uFEFF\\u00A0\\u2000-\\u200A]*)\\p{L}+)*[ \\uFEFF\\u00A0\\u2000-\\u200A]*$", message = "El nombre solo puede contener letras, espacios y guiones.") String name,
		@NotBlank @Size(min = 2, max = 12) @Pattern(regexp = "^\\p{L}+$", message = "La clave solo puede contener letras.") String code,
		@Size(max = 500) @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción contiene caracteres no válidos.") String description,
		UUID directorPersonId) {
}
