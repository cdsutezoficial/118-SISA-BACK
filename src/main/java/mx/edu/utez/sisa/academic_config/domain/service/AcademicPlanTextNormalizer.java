package mx.edu.utez.sisa.academic_config.domain.service;

import java.util.Locale;

/**
 * Normalización de los textos del agregado Plan — el plan y sus hijos (nivel,
 * materia y rango de escala) — antes de persistir y antes de buscar
 * duplicados.
 *
 * <p>Además de recortar, <b>compacta las rachas de whitespace</b>: toda cadena
 * de espacios en blanco se reduce a un solo espacio y luego se recortan los
 * extremos. {@code "   a \t b  "} se guarda como {@code "a b"}. A la base nunca
 * llega una racha de espacios.
 *
 * <p>El flag {@code (?U)} es obligatorio y no es cosmético: sin él, el
 * {@code \s} de Java sólo reconoce {@code [ \t\n\x0B\f\r]} y <b>no</b> el espacio
 * duro {@code U+00A0} ni los espacios Unicode {@code U+2000}–{@code U+200A}, que
 * sí cubre el {@code \s} de JavaScript. Sin el flag, un texto con espacio duro
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
 * <p>La compactación también hace que la unicidad compare siempre la misma
 * forma: {@code "Ingeniería en  Software"} colisiona con {@code "Ingeniería en
 * Software"}.
 */
final class AcademicPlanTextNormalizer {

	private AcademicPlanTextNormalizer() {
	}

	/** Versión del plan: {@code "2024-1"}, {@code "2023-A"}. */
	static String version(String value) {
		return collapse(value);
	}

	/** Periodo de vigencia: {@code "2024-2028"}, {@code "Enero 2023"}. */
	static String validityPeriod(String value) {
		return collapse(value);
	}

	/** Clave de titulación: {@code "IDGS-TIT-2024"}. */
	static String titulationKey(String value) {
		return collapse(value);
	}

	/**
	 * Nombre de la materia. Compacta igual que los campos del plan.
	 */
	static String subjectName(String value) {
		return collapse(value);
	}

	/**
	 * Código de la materia.
	 *
	 * <p>Se pasa a mayúsculas además de compactar, replicando el
	 * {@code normalizeCode} del frontend ({@code PlanMateriaForm} lo llevaba a
	 * mayúsculas en cada pulsación). Sin esta homologación, un alta hecha por
	 * API con {@code "mat101"} y otra hecha desde la UI con {@code "MAT101"}
	 * serían dos materias distintas para la unicidad.
	 *
	 * <p>La comparación de duplicados es además {@code equalsIgnoreCase} (ver
	 * {@code AcademicPlan#hasSubjectCode}), de modo que las filas que ya
	 * existan en la base con otra caja también colisionan.
	 */
	static String subjectCode(String value) {
		return collapse(value).toUpperCase(Locale.ROOT);
	}

	/**
	 * Descripción del nivel. Opcional: devuelve {@code null} cuando no queda
	 * nada, igual que {@link AcademicProgramTextNormalizer#optional}.
	 *
	 * <p>El emptiness se decide <b>sobre el texto ya compacto</b> y no con
	 * {@code isBlank()} sobre el original: {@code isBlank()} decide con
	 * {@link Character#isWhitespace}, que no reconoce el espacio duro
	 * {@code U+00A0}. Un campo que vale sólo espacios duros pasaría el
	 * {@code isBlank()} y se guardaría como cadena vacía en vez de {@code null}.
	 */
	static String levelDescription(String value) {
		return optional(value);
	}

	/** Letra del rango de una escala de calificación: {@code "CO"}, {@code "NP"}. */
	static String entryLetter(String value) {
		return collapse(value);
	}

	/**
	 * Descripción del rango de una escala. Obligatoria en la columna
	 * ({@code nullable = false}), pero se compacta y recorta igual que el resto.
	 */
	static String entryDescription(String value) {
		return collapse(value);
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

	/** Colapsa y devuelve {@code null} en vez de cadena vacía. */
	private static String optional(String value) {
		if (value == null) {
			return null;
		}
		String collapsed = collapse(value);
		return collapsed.isEmpty() ? null : collapsed;
	}
}
