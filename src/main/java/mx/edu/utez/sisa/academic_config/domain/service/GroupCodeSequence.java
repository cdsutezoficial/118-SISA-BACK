package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.shared.exception.NotEnoughGroupCodesException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Asigna las claves de una creación masiva de grupos: nivel + letra, del tipo
 * {@code "3A"}, {@code "3B"}, …
 *
 * <p>
 * La letra se elige como la <em>primera libre</em> a partir de la A, no como
 * "la siguiente a la última existente". Las dos cosas coinciden mientras no
 * haya huecos, pero en cuanto los hay divergen: con {@code 3A} y {@code 3C}
 * ya guardados, "la siguiente a la última" devolvería {@code 3D} y dejaría el
 * {@code 3B} como hueco muerto para siempre, mientras que la primera libre
 * reutiliza el hueco. El md de la fase 8 pide "detectar la última letra
 * existente"; se implementa la primera libre porque es la misma intención sin
 * ese defecto, y sólo se diferencia en el caso con huecos.
 *
 * <p>
 * El rango es A–Z a propósito. No hay convención de qué sigue a la Z en este
 * dominio, y un código como {@code 3AA} violaría el contrato
 * {@code ^\d+\p{L}+$} que comparten el DTO y el form (que admite varios
 * dígitos y una sola letra), así que inventar una regla de dos letras crearía
 * códigos que la validación del alta manual rechazaría. El tope de 26 sale de
 * ahí, y {@code CreateGroupsBulkRequest} lo refleja con un {@code @Max(26)}.
 *
 * <p>
 * No es un componente Spring a propósito: es lógica pura sin puertos, y así
 * queda testeable sin repository ni transacción.
 */
final class GroupCodeSequence {

	/** Primera letra del rango. */
	static final char FIRST_LETTER = 'A';

	/** Última letra del rango, inclusive. */
	static final char LAST_LETTER = 'Z';

	/** Cuántas claves caben en el rango, y por tanto el {@code @Max} del DTO. */
	static final int MAX_CODES = LAST_LETTER - FIRST_LETTER + 1;

	private GroupCodeSequence() {
	}

	/**
	 * @param levelPrefix prefijo numérico del nivel, sin la letra: {@code "3"}
	 *                    para el tercer nivel, {@code "10"} para el décimo
	 * @param usedCodes   claves ya en uso dentro de la generación. Se compara
	 *                    tal cual, sin normalizar: quien llama ya pasó los
	 *                    valores por {@link GroupTextNormalizer}, y volver a
	 *                    normalizar aquí escondería el error si algún día dejara
	 *                    de hacerlo
	 * @param quantity    cuántas claves devolver
	 * @throws NotEnoughGroupCodesException si no quedan letras libres suficientes
	 */
	static List<String> nextCodes(String levelPrefix, Collection<String> usedCodes, int quantity) {
		// Copia propia: el llamador puede pasar una lista de JpaRepository y
		// modificarla aquí sería un efecto secundario invisible.
		Set<String> taken = new HashSet<>(usedCodes);
		List<String> codes = new ArrayList<>(quantity);

		for (char letter = FIRST_LETTER; letter <= LAST_LETTER && codes.size() < quantity; letter++) {
			String code = levelPrefix + letter;
			// `add` devuelve false si ya estaba: sirve a la vez de "¿está libre?"
			// y de registro de lo ya elegido en este mismo lote, para que un
			// quantity mayor que 1 no repita la misma letra.
			if (taken.add(code)) {
				codes.add(code);
			}
		}

		if (codes.size() < quantity) {
			throw new NotEnoughGroupCodesException(
					"Only " + codes.size() + " free codes left for level " + levelPrefix + ", " + quantity
							+ " requested");
		}
		return codes;
	}
}