package mx.edu.utez.sisa.academic_config.domain.service;

/**
 * Normalización de los textos de una carrera, antes de persistir y antes de
 * buscar duplicados.
 *
 * <p>Además de recortar, <b>compacta las rachas de whitespace</b>: toda cadena de
 * espacios en blanco se reduce a un solo espacio y luego se recortan los
 * extremos. {@code "   a \t b  "} se guarda como {@code "a b"}. A la base nunca
 * llega una racha de espacios.
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
 * <p>Ojo con el orden de las validaciones: el {@code @Pattern} del DTO se
 * evalúa en el controller, <b>antes</b> que este normalizador. Por eso el
 * patrón tiene que aceptar todo lo que aquí se arregla; si rechazara las rachas
 * de espacios, el 400 se adelantaría a la limpieza.
 *
 * <p>La compactación también hace que la unicidad de
 * {@code (offerName, modality)} compare siempre la misma forma: {@code "Ingeniería
 *  en  Software  Presencial"} colisiona con {@code "Ingeniería en Software
 * Presencial"}.
 */
final class AcademicProgramTextNormalizer {

	private AcademicProgramTextNormalizer() {
	}

	static String name(String value) {
		return collapse(value);
	}

	static String offerName(String value) {
		return collapse(value);
	}

	/**
	 * El código no admite espacios internos por contrato —el {@code @Pattern} es
	 * {@code ^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$}—, así que aquí sólo hace falta
	 * recortar y pasar a mayúsculas.
	 */
	static String code(String value) {
		return value.trim().toUpperCase(java.util.Locale.ROOT);
	}

	/**
	 * Campos opcionales ({@code description} y {@code dgpCode}). Compacta igual
	 * que los obligatorios y devuelve {@code null} cuando no queda nada.
	 *
	 * <p>El emptiness se decide <b>sobre el texto ya compacto</b> y no con
	 * {@code isBlank()} sobre el original: {@code isBlank()} decide con
	 * {@link Character#isWhitespace}, que no reconoce el espacio duro
	 * {@code U+00A0}. Un campo que vale sólo espacios duros passaría el
	 * {@code isBlank()} y se guardaría como cadena vacía en vez de {@code null}.
	 */
	static String optional(String value) {
		if (value == null) {
			return null;
		}
		String collapsed = collapse(value);
		return collapsed.isEmpty() ? null : collapsed;
	}

	/** Colapsa cada racha de whitespace a un espacio y quita los exteriores. */
	private static String collapse(String value) {
		// El U+FEFF (BOM / zero-width no-break) se elimina entero, no se vuelve
		// espacio: es de anchura cero, así que convertirlo partiría la palabra por
		// la mitad. Hay que hacerlo explícitamente porque el `\s` de ECMAScript lo
		// incluye y el `(?U)\s` de Java no — Unicode lo sacó de la propiedad
		// *White_Space* en 4.0.1, pero la especificación de JavaScript nunca se
		// actualizó. Sin esta línea el frontend guardaría "a b" y el backend "a\uFEFFb".
		//
		// El replaceAll va antes del trim a propósito: `String.trim()` sólo quita
		// caracteres <= U+0020, así que un espacio duro final sobrevive al trim.
		// Colapsando primero, todos los whitespace quedan reducidos a un espacio
		// ASCII que el trim sí reconoce.
		return value.replace("\uFEFF", "").replaceAll("(?U)\\s+", " ").trim();
	}
}