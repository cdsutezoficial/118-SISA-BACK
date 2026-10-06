package mx.edu.utez.sisa.admission.domain.service;

/**
 * Normalización del texto de un canal de difusión, antes de persistir y antes de
 * buscar duplicados.
 *
 * <p><b>No pasa a mayúsculas</b>, a diferencia del código de otros catálogos
 * ({@code AcademicDivisionTextNormalizer#code}). El nombre de un canal es texto
 * visible —"Facebook", "Referido familiar"— y pasarlo a mayúsculas cambiaría lo
 * que el usuario ve en el listado. La comparación sin sensibilidad a mayúsculas
 * la da la consulta ({@code findByNameIgnoreCase}), no el valor almacenado.
 *
 * <p>Lo que sí hace, igual que los normalizadores hermanos, es <b>compactar las
 * rachas de whitespace</b>: toda cadena de espacios en blanco se reduce a un
 * espacio y luego se recortan los extremos. {@code "  Referido   familiar "} se
 * guarda como {@code "Referido familiar"}. Sin esto, el criterio de aceptación
 * " {@code  Facebook } cuenta como el mismo canal que {@code Facebook}" no se
 * cumpliría: la búsqueda ignora mayúsculas pero no el espacio suelto.
 *
 * <p>El flag {@code (?U)} es obligatorio y no es cosmético: sin él, el
 * {@code \s} de Java sólo reconoce {@code [ \t\n\x0B\f\r]} y <b>no</b> el espacio
 * duro {@code U+00A0} ni los espacios Unicode {@code U+2000}–{@code U+200A}, que
 * sí cubre el {@code \s} de JavaScript. Sin el flag, un nombre con espacio duro
 * pasaría la validación del frontend y el backend lo rechazaría con 400.
 *
 * <p>Ojo con el orden de las validaciones: el {@code @Pattern} del DTO se
 * evalúa en el controller, <b>antes</b> que este normalizador. Por eso el patrón
 * tiene que aceptar todo lo que aquí se arregla; el {@code ^[^\p{Cc}]*$} de los
 * DTO de este catálogo lo hace, porque el espacio duro y el BOM no son
 * caracteres de control.
 */
final class OutreachChannelTextNormalizer {

	private OutreachChannelTextNormalizer() {
	}

	static String name(String value) {
		return collapse(value);
	}

	/** Colapsa cada racha de whitespace a un espacio y quita los exteriores. */
	private static String collapse(String value) {
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
