package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OrderIdBuilder} — the EVO {@code order.id} rule:
 * environment identifier ({@code EVO_ORDER_ID_PREFIX}) plus the ficha folio,
 * plus a 6-char random suffix, restricted to {@code [0-9A-Za-z_-]} and capped
 * at {@code EVO_ORDER_ID_LENGTH}.
 *
 * <p>The builder is cap-agnostic: it honours whatever length it is handed, and it
 * is {@code EvoConfig} that clamps that to the 40 chars the gateway documents.
 * These tests pin that the cap is never spent on the suffix — not that 40 is the
 * cap — so the wide lengths below are deliberate.
 *
 * <p>The suffix is the part that has to hold: it is what stops the id from being
 * re-derivable (and therefore forgeable) and what stops a rebuilt database from
 * re-issuing ids the gateway already considers paid. These tests pin that it is
 * always present, that the cap is never spent on it, and that it actually varies.
 */
class OrderIdBuilderTest {

	private static final String SUFFIX = "[A-Z0-9]{6}";

	@Test
	void buildsPrefixPlusFolioPlusSuffix() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 32);
		assertThat(builder.build("ADM-2026-000001"))
				.hasSize(31)
				.matches("TESTUTEZ-ADM-2026-000001-" + SUFFIX);
	}

	@Test
	void usesFolioAloneWhenPrefixIsBlank() {
		OrderIdBuilder builder = new OrderIdBuilder("", 32);
		assertThat(builder.build("ADM-2026-000001"))
				.hasSize(22)
				.matches("ADM-2026-000001-" + SUFFIX);
	}

	/**
	 * Two checkouts of the same folio are two different orders at the gateway. If
	 * this ever goes back to a deterministic id, a rebuilt database re-issues ids
	 * Evo already has as captured.
	 */
	@Test
	void twoBuildsOfTheSameFolioAreDifferentOrders() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 32);

		Set<String> ids = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			ids.add(builder.build("ADM-2026-000001"));
		}

		assertThat(ids).hasSize(500);
	}

	@Test
	void stripsCharsNotAllowedByTheGateway() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 64);
		assertThat(builder.build("ADM 2026·000.001/extra"))
				.matches("TESTUTEZ-ADM2026000001extra-" + SUFFIX);
	}

	/**
	 * When the cap is tight the folio gives way from the left — the folio is only
	 * there to be recognisable, so losing its first characters is survivable —
	 * while the suffix, which carries the uniqueness, is untouched. Truncating
	 * from the right instead would collapse the last digits, and those are exactly
	 * what tells two fichas apart.
	 */
	@Test
	void truncatesTheFolioAndNeverTheSuffix() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 28);
		assertThat(builder.build("ADM-2026-000001"))
				.hasSize(27)
				.matches("TESTUTEZ-2026-000001-" + SUFFIX);
	}

	/** The separator the prefix contributes is never doubled by the cut. */
	@Test
	void aTruncatedFolioDoesNotDoubleTheSeparator() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 32);
		assertThat(builder.build("ADM-2026-000001"))
				.doesNotContain("--")
				.matches("TESTUTEZ-ADM-2026-000001-" + SUFFIX);
	}

	/**
	 * The cap holds as the prefix and the folio grow: the folio absorbs all of it,
	 * and what comes out is still a legal, still-unique id.
	 */
	@Test
	void lengthNeverExceedsMaxLength() {
		String[] folios = { "ADM-2026-000001", "A", "ADM-2026-000001-EXTRA-LONG-SUFFIX-0001" };
		int[] limits = { 32, 40, 64, 128 };

		for (int limit : limits) {
			OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", limit);
			for (String folio : folios) {
				assertThat(builder.build(folio))
						.hasSizeLessThanOrEqualTo(limit)
						.matches("[0-9A-Za-z_-]+");
			}
		}
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

	/**
	 * A cap too small to hold the suffix and one character of folio cannot be
	 * honoured without either dropping the uniqueness or overrunning the gateway's
	 * own limit, so it fails loudly instead of quietly.
	 */
	@Test
	void rejectsACapTooSmallToHoldTheSuffix() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 5);
		assertThatThrownBy(() -> builder.build("ADM-2026-000001"))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("EVO_ORDER_ID_LENGTH=5");
	}

	@Test
	void defaultsMaxLengthTo32WhenZero() {
		OrderIdBuilder builder = new OrderIdBuilder("TESTUTEZ", 0);
		assertThat(builder.build("ADM-2026-000001"))
				.hasSize(31)
				.matches("TESTUTEZ-ADM-2026-000001-" + SUFFIX);
	}
}
