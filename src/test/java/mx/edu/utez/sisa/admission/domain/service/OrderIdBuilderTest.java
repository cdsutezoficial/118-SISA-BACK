package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OrderIdBuilder} — the EVO {@code order.id} rule:
 * environment identifier ({@code EVO_ORDER_ID_PREFIX}, fallback the merchant
 * is decided by the caller) plus the ficha folio, restricted to
 * {@code [0-9A-Za-z_-]} and capped at {@code EVO_ORDER_ID_LENGTH} (32).
 */
class OrderIdBuilderTest {

	@Test
	void buildsPrefixPlusFolio() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 32);
		assertThat(builder.build("ADM-2026-000001")).isEqualTo("TESTUTEZ-ADM-2026-000001");
	}

	@Test
	void usesFolioAloneWhenPrefixIsBlank() {
		OrderIdBuilder builder = new OrderIdBuilder("", 32);
		assertThat(builder.build("ADM-2026-000001")).isEqualTo("ADM-2026-000001");
	}

	@Test
	void stripsCharsNotAllowedByTheGateway() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 64);
		assertThat(builder.build("ADM 2026·000.001/extra")).isEqualTo("TESTUTEZ-ADM2026000001extra");
	}

	@Test
	void truncatesToMaxLength() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 10);
		assertThat(builder.build("ADM-2026-000001")).hasSize(10).isEqualTo("TESTUTEZ-A");
	}

	@Test
	void rejectsBlankFolio() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 32);
		assertThatThrownBy(() -> builder.build("  "))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("No se pudo generar el identificador del pedido");
	}

	@Test
	void rejectsOrderIdThatSanitizesToNothing() {
		OrderIdBuilder builder = new OrderIdBuilder("!!!", 32);
		assertThatThrownBy(() -> builder.build("###"))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("No se pudo generar el identificador del pedido");
	}

	@Test
	void defaultsMaxLengthTo32WhenZero() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 0);
		assertThat(builder.build("ADM-2026-000001")).isEqualTo("TESTUTEZ-ADM-2026-000001");
	}
}