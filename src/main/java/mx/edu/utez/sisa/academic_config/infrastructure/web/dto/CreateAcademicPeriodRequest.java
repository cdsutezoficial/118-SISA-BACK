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
 * Request body for {@code POST /periods}. {@code status} is deliberately
 * absent — every new period defaults to {@code CONFIGURATION} (enforced by
 * the {@code AcademicPeriod} constructor).
 *
 * <p>Formato de los campos, decididos con el usuario el 2026-10-05:
 *
 * <ul>
 * <li>{@code name} — letras, números, espacios y guiones. Es el mismo contrato de
 * {@code name} que {@code SubjectClassificationRequest}, así que comparte
 * expresión con aquella clase: letras Unicode ({@code \p{L}}), dígitos
 * ({@code \p{N}}), espacios y guiones, y las cinco variantes Unicode
 * {@code U+2010}–{@code U+2015} además del guion ASCII. El guion con espacios
 * alrededor vale, porque el propio ejemplo del dominio es
 * {@code "Enero – Abril 2026"}, con raya. El techo de 150 coincide con el
 * {@code @Column} de la entidad.</li>
 * <li>{@code year} — {@code @Min(1900) @Max(2100)}. No había ningún rango antes,
 * así que un año de {@code 0} —que es lo que produce un campo vacío— o uno de
 * {@code 99999} llegaban hasta la base.</li>
 * <li>{@code periodNumber} — {@code @Min(1)} y <b>sin tope</b>, a propósito. El
 * documento del dominio dice "1, 2, 3 dentro del año", pero el mismo documento
 * exige "NO asumir cuatrimestres fijos — 100% configurable" y "soportar
 * bimestrales/semestrales a futuro". Un bimestral necesita más de tres periodos
 * al año, así que un {@code @Max(3)} contradiría al dominio y dejaría fuera
 * registros que el propio sistema puede llegar a producir. Se decide con el
 * usuario el 2026-10-05.</li>
 * <li>Las <b>cuatro fechas son obligatorias</b>: inicio y fin del periodo, e
 * inicio y fin de las inscripciones. Esto <b>cambia</b> el requisito original de
 * la fase, que pedía hacer opcionales las dos últimas; la decisión del usuario
 * del 2026-10-05 es que las cuatro se llenan, y por eso aquí los cuatro
 * {@code @NotNull} se quedan.</li>
 * </ul>
 *
 * <p>El orden de las fechas no lo puede expresar una anotación, porque depende de
 * dos campos a la vez. Vive en {@code AcademicPeriod.validateDateRanges}, que
 * devuelve 400 con {@code InvalidPlanDataException}.
 *
 * <p>El {@code @Pattern} de {@code name} <b>acepta rachas de espacio y espacios
 * exteriores a propósito</b>: se evalúa en el controller, <b>antes</b> que
 * {@link mx.edu.utez.sisa.academic_config.domain.service.AcademicPeriodTextNormalizer},
 * que es quien compacta. Si las rechazara, el 400 se adelantaría a la limpieza y
 * el backend nunca compactaría nada. La compactación es la que limpia; el patrón
 * sólo define la forma. La clase de espacios es
 * {@code [ \uFEFF\u00A0\u2000-\u200A]} e incluye el espacio duro {@code U+00A0} y
 * los Unicode {@code U+2000}–{@code U+200A} porque el frontend sí los acepta: sin
 * ellos, un nombre pegado desde un navegador o un documento de Word pasaría la
 * validación del cliente y el servidor lo rechazaría con 400.
 *
 * <p>La expresión es idéntica carácter a carácter a
 * {@code lettersNumbersSpacesAndHyphens} del frontend. Si divergieran, el
 * navegador dejaría pasar lo que el servidor rechaza con 400.
 */
public record CreateAcademicPeriodRequest(@NotBlank(message = "El nombre del periodo es obligatorio.") @Size(max = 150, message = "El nombre no puede superar 150 caracteres.") @Pattern(regexp = "^[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\p{L}\\p{N}]+(?:(?:[ \\uFEFF\\u00A0\\u2000-\\u200A]+|[ \\uFEFF\\u00A0\\u2000-\\u200A]*[\\u2010-\\u2015-][ \\uFEFF\\u00A0\\u2000-\\u200A]*)[\\p{L}\\p{N}]+)*[ \\uFEFF\\u00A0\\u2000-\\u200A]*$", message = "El nombre solo puede contener letras, números, espacios y guiones.") String name,
		@Min(value = 1900, message = "El año debe estar entre 1900 y 2100.") @Max(value = 2100, message = "El año debe estar entre 1900 y 2100.") int year,
		@Min(value = 1, message = "El número de periodo debe ser mayor o igual a 1.") int periodNumber,
		@NotNull(message = "Debes seleccionar el tipo de periodo.") PeriodType type,
		@NotNull(message = "La fecha de inicio es requerida.") LocalDate startDate,
		@NotNull(message = "La fecha de fin es requerida.") LocalDate endDate,
		@NotNull(message = "La fecha de inicio de inscripciones es requerida.") LocalDate enrollmentStart,
		@NotNull(message = "La fecha de fin de inscripciones es requerida.") LocalDate enrollmentEnd) {
}