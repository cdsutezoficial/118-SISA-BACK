package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /subject-classifications} (mirrors
 * {@code CreateAcademicDivisionRequest}). Unlike Division's request, there is
 * no optional director field — this catalog only has {@code name}/{@code code}.
 *
 * <p>Formato de los campos, decididos con el usuario el 2026-10-05:
 *
 * <ul>
 * <li>{@code name} — letras, números, espacios y guiones. Es el contrato de
 * División y Carrera ({@code lettersSpacesAndHyphens}) <b>más los dígitos</b>, y
 * sin la restricción de que el guion tenga que ser ASCII: se aceptan las cinco
 * variantes Unicode U+2010–U+2015 además del guion, porque los datos guardados
 * usan la raya {@code –}. El guion admite espacios a ambos lados, así que
 * {@code "Materia integradora - 1"} es válido. Las letras son Unicode
 * ({@code \p{L}}), de modo que {@code "Práctica"}, {@code "Español"} y
 * {@code "Diseño"} se guardan sin transliterar. Se rechazan paréntesis, comas,
 * signos de puntuación y caracteres de control: el usuario pegó
 * {@code $%&/()=[:_.-2das - =)("OP.-{{-{} en el campo y el formulario lo aceptaba
 * entero. El techo de 150 coincide con el {@code @Column} de la entidad.</li>
 * <li>{@code code} — segmentos {@code [A-Za-z0-9]} unidos por guiones simples,
 * sin espacios. Es lo que admiten los valores reales observados
 * ({@code INT-C-ADM}, {@code REG-UPD-DUP-B}). El máximo es 20 porque es el dato
 * más largo que se ha visto son 12; la columna actual es {@code varchar(255)} y
 * {@code ddl-auto=update} no suele encoger columnas, así que el techo no rompe
 * registros existentes.</li>
 * </ul>
 *
 * <p>La expresión de {@code name} es la de {@code lettersNumbersSpacesAndHyphens}
 * del frontend; la de {@code code} es la de {@code codePattern}, y es la misma
 * que usa {@link CreateAcademicProgramRequest}. Ambas deben coincidir carácter a
 * carácter con su regla del frontend: si divergieran, el navegador dejaría pasar
 * lo que el servidor rechaza con 400.
 *
 * <p>El separador de palabras del {@code @Pattern} de {@code name} <b>acepta
 * rachas de espacio a propósito</b> ({@code +} y no un espacio suelto), y el
 * {@code @Pattern} de {@code code} <b>rechaza los espacios en los
 * extremos</b>. Los dos criterios son deliberados y el mismo en cada capa: este
 * {@code @Pattern} se evalúa en el controller, <b>antes</b> que
 * {@link mx.edu.utez.sisa.academic_config.domain.service.SubjectClassificationTextNormalizer},
 * que es quien compacta. Si el patrón de {@code name} rechazara las rachas, el
 * 400 se adelantaría a la limpieza; por eso las acepta. El de {@code code} las
 * rechaza para compartir expresión con el código de División y Carrera: la UI
 * manda el código ya recortado y en mayúsculas, así que nunca alcanza el caso
 * {@code " INT-C-ADM "}, que sí recibe 400 en una llamada directa al API.
 *
 * <p>La clase de espacios es {@code [ \uFEFF\u00A0\u2000-\u200A]} e incluye el
 * espacio duro {@code U+00A0} y los espacios Unicode {@code U+2000}–{@code U+200A}
 * porque el frontend sí los acepta: sin ellos, un nombre pegado desde un
 * navegador o un documento de Word pasaría la validación del cliente y el
 * servidor lo rechazaría con 400. También incluye {@code U+FEFF} (BOM), que el
 * normalizador elimina entero en lugar de volverlo espacio para no partir una
 * palabra por la mitad. El tabulador y el salto de línea quedan fuera y quedan
 * anotados como deuda, igual que en las fases anteriores: son {@code \p{Cc}}, el
 * normalizador los convertiría en un espacio, y una llamada directa al API los
 * recibe con 400 aunque el backend sabría limpiarlos.
 *
 * <p>Cada campo lleva un único {@code @Pattern} y no un {@code @Pattern.List}:
 * sobre los componentes de un {@code record} la versión repetible se propaga
 * también como anotación de tipo, y Hibernate Validator la encuentra duplicada y
 * lanza {@code AnnotationFormatError} (500 en lugar de 400).
 */
public record CreateSubjectClassificationRequest(
		@NotBlank @Size(max = 150) @Pattern(regexp = "^[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\p{L}\\p{N}]+(?:(?:[ \\uFEFF\\u00A0\\u2000-\\u200A]+|[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\u2010-\\u2015-][ \\uFEFF\\u00A0\\u2000-\\u200A]*)[\\p{L}\\p{N}]+)*[ \\uFEFF\\u00A0\\u2000-\\u200A]*$", message = "El nombre solo puede contener letras, números, espacios y guiones.") String name,
		@NotBlank @Size(min = 2, max = 20) @Pattern(regexp = "^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$", message = "La clave solo puede contener letras, números y guiones.") String code) {
}