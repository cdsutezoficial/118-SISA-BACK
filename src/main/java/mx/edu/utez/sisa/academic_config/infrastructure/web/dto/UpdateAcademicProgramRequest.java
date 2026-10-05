package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * Request body for {@code PUT /programs/{id}} (design.md — REST endpoints).
 * {@code status} is deliberately absent — status transitions go through
 * {@code PATCH /programs/{id}/status}. {@code dgpCode} is optional — MAY be
 * null or omitted when the program has not yet been registered with DGP.
 * {@code divisionId} is required (spec: "divisionId MUST be required" — it
 * MUST NOT be omitted or null), unlike
 * {@code CreateAcademicDivisionRequest.directorPersonId}. {@code
 * continuityProgramId} is optional — schema-only in this change, no
 * validation or linking logic applied.
 *
 * <p>Formato de los campos, alineado con la regla de negocio del 2026-10-04 y
 * con el schema de {@code CarrerasForm}:
 *
 * <ul>
 * <li>{@code name} — letras y acentos separados por espacios o por guiones.
 * Sin dígitos, paréntesis ni comas. El separador de palabras es un espacio
 * simple o guiones con espacios opcionales alrededor, y se aceptan las cinco
 * variantes Unicode U+2010–U+2015 además del guion ASCII. Admite espacios en
 * los extremos y espacios duros porque
 * {@link mx.edu.utez.sisa.academic_config.domain.service.AcademicProgramTextNormalizer}
 * los limpia antes de persistir; el tabulador y el salto de línea quedan fuera
 * a propósito, ya que es un campo de una línea y ninguno de los dos es
 * tecleable ahí.</li>
 * <li>{@code offerName} — la misma expresión que {@code name}, con su propio
 * mensaje. Es el otro nombre oficial de la carrera y forma parte de la
 * unicidad {@code (offerName, modality)}, así que admite exactamente lo mismo
 * que {@code name}.</li>
 * <li>{@code code} — a diferencia de la división, aquí el código <b>sí</b>
 * admite números y guiones: son segmentos alfanuméricos unidos por guiones
 * simples, sin guiones al inicio, al final ni consecutivos. Es la única
 * diferencia deliberada de formato respecto a División.</li>
 * <li>{@code description} — texto libre. Sólo se rechazan los caracteres de
 * control; el resto es contenido legítimo y React escapa al renderizar.</li>
 * <li>{@code dgpCode} — clave numérica que asigna la Dirección General de
 * Profesiones, opcional y sin {@code @Pattern}: es un número, no un nombre.
 * Sólo lleva techo de longitud, y coincide con el {@code maxLength} del
 * frontend.</li>
 * </ul>
 *
 * <p>La expresión de {@code name} y la de {@code offerName} están duplicadas
 * literalmente en {@link UpdateAcademicProgramRequest} y en la regla
 * {@code lettersSpacesAndHyphens} del frontend. Deben coincidir carácter a
 * carácter: si divergieran, el navegador dejaría pasar lo que el servidor
 * rechaza con 400.
 *
 * <p><b>La clase de espacios es {@code [ \uFEFF\u00A0\u2000-\u200A]+} y no un espacio
 * suelto, a propósito.</b> Este {@code @Pattern} se evalúa en el controller,
 * antes que
 * {@link mx.edu.utez.sisa.academic_config.domain.service.AcademicProgramTextNormalizer},
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
public record UpdateAcademicProgramRequest(@NotNull UUID divisionId,
		@NotBlank @Size(max = 150) @Pattern(regexp = "^[ \\uFEFF\\u00A0\\u2000-\\u200A]*\\p{L}+(?:(?:[ \\uFEFF\\u00A0\\u2000-\\u200A]+|[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\u2010-\\u2015-][ \\uFEFF\\u00A0\\u2000-\\u200A]*)\\p{L}+)*[ \\uFEFF\\u00A0\\u2000-\\u200A]*$", message = "El nombre solo puede contener letras, espacios y guiones.") String name,
		@NotBlank @Size(max = 200) @Pattern(regexp = "^[ \\uFEFF\\u00A0\\u2000-\\u200A]*\\p{L}+(?:(?:[ \\uFEFF\\u00A0\\u2000-\\u200A]+|[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\u2010-\\u2015-][ \\uFEFF\\u00A0\\u2000-\\u200A]*)\\p{L}+)*[ \\uFEFF\\u00A0\\u2000-\\u200A]*$", message = "El nombre de oferta solo puede contener letras, espacios y guiones.") String offerName,
		@NotBlank @Size(min = 2, max = 41) @Pattern(regexp = "^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$", message = "La clave solo puede contener letras, números y guiones.") String code,
		@NotNull AcademicLevel level, @NotNull ProgramModality modality, UUID continuityProgramId,
		@Size(max = 500) @Pattern(regexp = "^[^\\p{Cc}]*$", message = "La descripción contiene caracteres no válidos.") String description,
		@Size(max = 100) String dgpCode) {
}
