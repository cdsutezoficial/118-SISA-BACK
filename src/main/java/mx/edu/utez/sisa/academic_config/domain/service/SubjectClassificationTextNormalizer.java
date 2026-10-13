package mx.edu.utez.sisa.academic_config.domain.service;

/**
 * Normalización de los textos de una clasificación de materia, antes de
 * persistir y antes de buscar duplicados.
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
 * <p>La compactación es también lo que hace que la unicidad del código compare
 * siempre la misma forma: {@code " INT-C-ADM "} y {@code "int-c-adm"} colisionan
 * porque los dos terminan en {@code INT-C-ADM}. Sin el recorte, el primero se
 * guardaría tal cual y el segundo crearía un segundo registro con el mismo
 * código, eludiendo el 409.
 *
 * <p>Ojo con el orden de las validaciones: el {@code @Pattern} del DTO se
 * evalúa en el controller, <b>antes</b> que este normalizador. Por eso el
 * patrón de {@code name} tiene que aceptar todo lo que aquí se arregla; el de
 * {@code code} es deliberadamente más estricto, igual que en División y
 * Carreras —ver {@link #code(String)}.
 */
final class SubjectClassificationTextNormalizer {

	private SubjectClassificationTextNormalizer() {
	}

	/** Nombre visible de la clasificación: {@code "Integradora"}. */
	static String name(String value) {
		return collapse(value);
	}

	/**
	 * Clave de la clasificación: {@code "INT-C-ADM"}, {@code "REG-UPD-DUP-B"}.
	 *
	 * <p>Se pasa a mayúsculas además de recortar, replicando el
	 * {@code normalizeCode} del frontend. El recorte no es cosmético para la
	 * unicidad: el {@code findByCode} del repositorio es
	 * {@code findByCodeIgnoreCase}, que es insensible a la caja pero <b>no</b>
	 * a los espacios. Sin recortar aquí, {@code " INT-C-ADM "} se guardaría
	 * distinto de {@code "INT-C-ADM"} y ambos pasarían el 409.
	 *
	 * <p>El código no admite espacios internos por contrato —el {@code @Pattern}
	 * es {@code ^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$}—, así que compactar rachas no
	 * aporta nada y aquí basta con recortar y pasar a mayúsculas.
	 */
	static String code(String value) {
		return value.trim().toUpperCase(java.util.Locale.ROOT);
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