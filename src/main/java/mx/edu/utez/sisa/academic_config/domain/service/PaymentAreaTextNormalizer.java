package mx.edu.utez.sisa.academic_config.domain.service;

/**
 * Normalización de los textos de un área de pago, antes de persistir y antes de
 * buscar duplicados.
 *
 * <p>Es la séptima normalizadora del contexto {@code academic_config}, y sigue la
 * convención del módulo de tener una por catálogo
 * ({@code AcademicPeriodTextNormalizer}, {@code AcademicDivisionTextNormalizer},
 * {@code AcademicPlanTextNormalizer}, {@code AcademicProgramTextNormalizer},
 * {@code GroupTextNormalizer}, {@code SubjectClassificationTextNormalizer}).
 * <b>Deuda:</b> {@link #collapse(String)} es byte a byte idéntico a
 * {@code AcademicDivisionTextNormalizer#collapse} y a
 * {@code AcademicPeriodTextNormalizer#collapse}; el día que se unan, esta clase
 * debería subir al shared kernel ({@code mx.edu.utez.sisa.shared}).
 *
 * <p>La normalización no es cosmética: es la que hace que se cumpla el criterio
 * de unicidad. {@code PaymentArea} tiene <b>dos</b> claves de negocio únicas
 * ({@code name} y {@code code}) y el dominio las compara con
 * {@code findByName}/{@code findByCode} case-insensitive, que en MySQL delega en
 * la colación. Pero la colación ({@code utf8mb4_0900_ai_ci}, {@code NO PAD})
 * <b>no</b> ignora los espacios: sin normalizar, {@code "Colegiaturas"} y
 * {@code " Colegiaturas "} pasarían las dos la validación de duplicado y la
 * segunda reventaría después con un {@code DuplicateKeyException} del índice
 * único, que el usuario vería como un 500.
 *
 * <p>{@code name} <b>no</b> pasa a mayúsculas porque es texto visible —"Cuotas de
 * Inscripción", "Colegiaturas"— y pasarlo a mayúsculas cambiaría lo que el
 * usuario ve en el listado y en el selector de conceptos. La comparación sin
 * sensibilidad a mayúsculas la da la colación, no el valor almacenado.
 *
 * <p>{@code code} sí pasa a mayúsculas: es un identificador corto, no texto
 * visible, y la regla de negocio confirmada el 2026-10-05 lo define como
 * <b>2 a 5 alfanuméricos en mayúscula</b> ({@code COL}, {@code INS},
 * {@code INSC}, {@code COLE}). El frontend replica esto con
 * {@code normalizeCode}, que recorta y pasa a mayúsculas.
 */
final class PaymentAreaTextNormalizer {

	private PaymentAreaTextNormalizer() {
	}

	/**
	 * El nombre es texto libre: sólo se compacta y recorta. El {@code @Pattern}
	 * del DTO ({@code ^[^\p{Cc}]*$}) rechaza los caracteres de control, así que
	 * aquí no puede quedar ninguno.
	 */
	static String name(String value) {
		return collapse(value);
	}

	/**
	 * El código no admite espacios internos por contrato —el {@code @Pattern} es
	 * {@code ^[A-Z0-9]{2,5}$}—, así que aquí sólo hace falta recortar y pasar a
	 * mayúsculas.
	 *
	 * <p>El {@code Locale.ROOT} es obligatorio: con el locale por defecto
	 * ({@code tr} en Máquinas turcas, y cualquier locale que redefina el
	 * case-mapping) {@code toUpperCase()} puede devolver una {@code i} con punto
	 * en lugar de {@code I}, y eso grabaría un valor que ya no cumple el
	 * {@code @Pattern} que sí lo validó en el controller.
	 */
	static String code(String value) {
		return value.trim().toUpperCase(java.util.Locale.ROOT);
	}

	static String description(String value) {
		return value == null ? null : collapse(value);
	}

	/** Colapsa cada racha de whitespace a un espacio y quita los exteriores. */
	private static String collapse(String value) {
		// El U+FEFF (BOM / zero-width no-break) se elimina entero, no se vuelve
		// espacio: es de anchura cero, así que convertirlo partiría la palabra por
		// la mitad. Hay que hacerlo explícitamente porque el `\s` de ECMAScript lo
		// incluye y el `(?U)\s` de Java no — Unicode lo sacó de la propiedad
		// *White_Space* en 4.0.1, pero la especificación de JavaScript nunca se
		// actualizó. Sin esta línea el frontend guardaría "a b" y el backend
		// "a\uFEFFb", que además sonarían como dos áreas distintas.
		//
		// El replaceAll va antes del trim a propósito: `String.trim()` sólo quita
		// caracteres <= U+0020, así que un espacio duro final sobrevive al trim.
		// Colapsando primero, todos los whitespace quedan reducidos a un espacio
		// ASCII que el trim sí reconoce.
		return value.replace("\uFEFF", "").replaceAll("(?U)\\s+", " ").trim();
	}
}
