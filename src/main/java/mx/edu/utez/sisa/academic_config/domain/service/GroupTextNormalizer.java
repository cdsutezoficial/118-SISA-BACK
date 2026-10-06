package mx.edu.utez.sisa.academic_config.domain.service;

import java.util.Locale;

/**
 * Normaliza el texto de entrada de {@code Group} antes de persistirlo.
 *
 * <p>
 * A diferencia de {@code SubjectClassificationTextNormalizer} y
 * {@code AcademicPeriodTextNormalizer}, que sólo compactan espacios, aquí el
 * compacto es <em>condición de corrección</em> y no simple higiene:
 * {@code (generationId, code)} es clave única y la comparación por defecto de
 * MySQL es case-insensitive, así que un "3a" y un "3A" colisionan. Si el
 *compactado no fuera aquí, un cliente podría crear "3A" y luego fallar al
 * guardar "3a" como duplicado — o al revés — según el orden de casing que
 * llegara primero. Normalizar en un solo sitio hace que ambos caminos convergjan.
 *
 * <p>
 * Se usa {@link Locale#ROOT} y no {@link Locale#default()} a propósito:
 * {@code toUpperCase()} con la configuración regional convierte la "i" turca en
 * "İ" y la "ß" alemana en "SS", así que un servidor en turca o Alemania trataría
 * códigos distintos a los del resto de la instalación.
 *
 * <p>
 * Se eliminan los espacios en lugar de compactarse: {@code code} es un
 * identificador corto (nivel + letra), no una etiqueta, y el contrato
 * {@code ^\d+\p{L}+$} no admite espacios internos de todas formas. Compactarlos
 * a un solo espacio los dejaría igual de inválidos, sólo que más difícil de
 * diagnosticar.
 */
final class GroupTextNormalizer {

	private GroupTextNormalizer() {
	}

	/**
	 * Recorta, quita el BOM y los espacios, y pasa a mayúsculas con
	 * {@link Locale#ROOT}. Devuelve el valor tal cual si es {@code null}, para no
	 * mover el problema del campo ausente al normalizador: eso es trabajo de
	 * {@code @NotNull}.
	 */
	static String code(String value) {
		if (value == null) {
			return null;
		}
		return value.replace("\uFEFF", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
	}
}