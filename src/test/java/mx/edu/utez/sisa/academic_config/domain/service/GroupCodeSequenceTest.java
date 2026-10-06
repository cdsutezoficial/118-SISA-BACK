package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.shared.exception.NotEnoughGroupCodesException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La secuencia de claves de la creación masiva no tenía ningún test (C2 de las
 * incidencias de la primera prueba manual), pese a ser la pieza cuyo fallo se
 * manifestó como el 500 de {@code GET /groups/next-codes}. Es lógica pura: sin
 * repositorio ni transacción, sólo se necesita esto.
 */
class GroupCodeSequenceTest {

	@Test
	void nextCodesUsesTheLevelPrefixAndStartsAtA() {
		assertThat(GroupCodeSequence.nextCodes("3", List.of(), 3)).containsExactly("3A", "3B", "3C");
	}

	@Test
	void nextCodesFillsGapsInsteadOfContinuingAfterTheLastUsed() {
		// Con 3A y 3C ocupadas, "la siguiente a la última" daría 3D y dejaría el
		// 3B como hueco muerto para siempre; la regla correcta es primera libre.
		assertThat(GroupCodeSequence.nextCodes("3", List.of("3A", "3C"), 2)).containsExactly("3B", "3D");
	}

	@Test
	void nextCodesOnlySeesCodesOfItsOwnLevel() {
		// El prefijo separa espacios de letras: 1A y 2A no tapan la 3A.
		assertThat(GroupCodeSequence.nextCodes("3", List.of("1A", "2A"), 1)).containsExactly("3A");
	}

	@Test
	void nextCodesCoversTheWholeAlphabetWhenEverythingIsFree() {
		List<String> codes = GroupCodeSequence.nextCodes("10", List.of(), GroupCodeSequence.MAX_CODES);

		assertThat(codes).hasSize(26);
		assertThat(codes.getFirst()).isEqualTo("10A");
		assertThat(codes.getLast()).isEqualTo("10Z");
	}

	@Test
	void nextCodesThrowsWhenTheQuantityDoesNotFitInTheRemainingLetters() {
		// @Max(26) del DTO: ni con 26 pedidas cabe si ya hay una tomada, y el
		// error tiene que ser 409 (NotEnoughGroupCodes), no un IndexOutOfBounds.
		assertThatThrownBy(() -> GroupCodeSequence.nextCodes("3", List.of("3A"), GroupCodeSequence.MAX_CODES))
				.isInstanceOf(NotEnoughGroupCodesException.class);
	}

	@Test
	void nextCodesDoesNotRepeatALetterWithinTheSameBatch() {
		// El registro de lo ya elegido en este lote es el mismo `taken` que
		// marca lo ocupado en BD: sin él, quantity=3 devolvería 3A tres veces.
		assertThat(GroupCodeSequence.nextCodes("3", List.of(), 3)).doesNotHaveDuplicates();
	}
}
