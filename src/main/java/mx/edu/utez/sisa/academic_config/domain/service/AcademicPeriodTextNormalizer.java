package mx.edu.utez.sisa.academic_config.domain.service;

/**
 * Normalización del texto de un periodo académico, antes de persistir.
 *
 * <p>Además de recortar, <b>compacta las rachas de whitespace</b>: toda cadena de
 * espacios en blanco se reduce a un solo espacio y luego se recortan los
 * extremos. {@code "   Enero \t – \t Abril  "} se guarda como
 * {@code "Enero – Abril"}. A la base nunca llega una racha de espacios.
 *
 * <p>El flag {@code (?U)} es obligatorio y no es cosmético: sin él, el
 * {@code \s} de Java sólo reconoce {@code [ \t\n\x0B\f\r]} y <b>no</b> el espacio
 * duro {@code U+00A0} ni los espacios Unicode {@code U+2000}–{@code U+200A}, que
 * sí cubre el {@code \s} de JavaScript. Sin el flag, un nombre con espacio duro
 * pasaría la validación del frontend y el backend lo rechazaría con 400.
 *
 * <p>El frontend hace la misma compactación en {@code normalizeText}, que se usa
 * como {@code validateOn} del schema (para que las reglas midan el texto
 * compacto) y al construir el payload de envío.
 *
 * <p><b>Aquí la normalización no afecta a ninguna unicidad.</b> Es lo que la
 * distingue de {@link SubjectClassificationTextNormalizer}: el duplicado de un
 * periodo es {@code (year, periodNumber)}, y ninguno de los dos campos pasa por
 * texto. {@code name} es una etiqueta visible y dos periodos sí pueden llamarse
 * igual. El compactado es entonces higiene —ningún texto guardado lleva rachas ni
 * espacios exteriores— y no una condición para que el 409 acierte.
 *
 * <p>Ojo con el orden de las validaciones: el {@code @Pattern} del DTO se
 * evalúa en el controller, <b>antes</b> que este normalizador. Por eso el
 * patrón de {@code name} tiene que aceptar todo lo que aquí se arregla; si
 * rechazara las rachas o los espacios exteriores, el 400 se adelantaría a la
 * limpieza y el backend nunca compactaría nada.
 */
final class AcademicPeriodTextNormalizer {

	private AcademicPeriodTextNormalizer() {
	}

	/** Etiqueta visible del periodo: {@code "Enero – Abril 2026"}. */
	static String name(String value) {
		// El replaceAll va antes del trim a propósito: `String.trim()` sólo quita
		// caracteres <= U+0020, así que un espacio duro final sobrevive al trim.
		// Colapsando primero, todos los whitespace quedan reducidos a un espacio
		// ASCII que el trim sí reconoce.
		//
		// El U+FEFF (BOM / zero-width no-break) se elimina entero, no se vuelve
		// espacio: es de anchura cero, así que convertirlo partiría la palabra por
		// la mitad. Hay que hacerlo explícitamente porque el `\s` de ECMAScript lo
		// incluye y el `(?U)\s` de Java no.
		return value.replace("\uFEFF", "").replaceAll("(?U)\\s+", " ").trim();
	}
}