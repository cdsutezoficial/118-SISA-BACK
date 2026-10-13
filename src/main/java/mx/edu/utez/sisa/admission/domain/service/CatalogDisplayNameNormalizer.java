package mx.edu.utez.sisa.admission.domain.service;

/**
 * Normalización del nombre visible de un catálogo del contexto {@code admission}:
 * canales de difusión ({@link mx.edu.utez.sisa.admission.domain.model.OutreachChannel})
 * y tipos de bachillerato
 * ({@link mx.edu.utez.sisa.admission.domain.model.HighSchoolType}).
 *
 * <p>Existe como clase única porque los dos catálogos la necesitan idéntica y ya
 * iba a ser la segunda copia: {@code academic_config} tiene sus dos replicas
 * ({@code AcademicPeriodTextNormalizer},
 * {@code AcademicDivisionTextNormalizer#name}) con el mismo cuerpo byte a byte.
 * <b>Deuda conocida:</b> esas dos siguen duplicadas porque viven en otro bounded
 * context; el día que se unan, esta clase debería subir al shared kernel
 * ({@code mx.edu.utez.sisa.shared}) y las cuatro chamar a un único sitio.
 *
 * <p><b>No pasa a mayúsculas</b>, a diferencia del código de otros catálogos
 * ({@code AcademicDivisionTextNormalizer#code}). El nombre de un canal o de un
 * tipo de bachillerato es texto visible —"Facebook", "Bachillerato Técnico"— y
 * pasarlo a mayúsculas cambiaría lo que el usuario ve en el listado y en los
 * selectores de referencia. La comparación sin sensibilidad a mayúsculas la da la
 * colación de MySQL (ver el javadoc de {@code OutreachChannel}), no el valor
 * almacenado.
 *
 * <p>Lo que sí hace es <b>compactar las rachas de whitespace</b>: toda cadena de
 * espacios en blanco se reduce a un espacio y luego se recortan los extremos.
 * {@code "  Bachillerato   Técnico "} se guarda como {@code "Bachillerato Técnico"}.
 * Sin esto, el criterio de aceptación de estas fases —{@code " Facebook "} cuenta
 * como el mismo canal que {@code "Facebook"}— no se cumpliría: la colación de
 * MySQL ({@code NO PAD}) distingue el espacio final, y comparar el valor crudo
 * dejaría pasar el duplicado.
 */
final class CatalogDisplayNameNormalizer {

	private CatalogDisplayNameNormalizer() {
	}

	/** Colapsa cada racha de whitespace a un espacio y quita los exteriores. */
	static String displayName(String value) {
		// El U+FEFF (BOM / zero-width no-break) se elimina entero, no se vuelve
		// espacio: es de anchura cero, así que convertirlo partiría la palabra por
		// la mitad. Hay que hacerlo explícitamente porque el `\s` de ECMAScript lo
		// incluye y el `(?U)\s` de Java no — Unicode lo sacó de la propiedad
		// *White_Space* en 4.0.1, pero la especificación de JavaScript nunca se
		// actualizó. Sin esta línea el frontend guardaría "a b" y el backend
		// "a\uFEFFb", que además sonarían como dos canales distintos.
		//
		// El replaceAll va antes del trim a propósito: `String.trim()` sólo quita
		// caracteres <= U+0020, así que un espacio duro final sobrevive al trim.
		// Colapsando primero, todos los whitespace quedan reducidos a un espacio
		// ASCII que el trim sí reconoce.
		return value.replace("\uFEFF", "").replaceAll("(?U)\\s+", " ").trim();
	}
}
