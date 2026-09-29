package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Builds the EVO {@code order.id} from the environment identifier
 * ({@code EVO_ORDER_ID_PREFIX}, e.g. {@code TESTUTEZ}) plus the candidate's
 * ficha folio — the "append the identifier + the payer's id/folio to the
 * order id" rule in the integration notes — plus a random suffix.
 *
 * <p>Two of the three rules here are ours, not the gateway's. The integration
 * guide documents {@code order.id} as {@code Min length: 1, Max length: 40},
 * {@code REQUIRED}, and unique per order — and says nothing about which
 * characters, so the {@code [0-9A-Za-z_-]} restriction is a choice made here, to
 * keep ids safe to carry in a query string and in a log line. The length, likewise,
 * is {@code EVO_ORDER_ID_LENGTH} rather than the gateway's 40, so a deployment can
 * be stricter; {@code EvoConfig} clamps it to 40 either way. Example:
 * {@code TESTUTEZ-ADM-2026-000001-A7K2QF} (31 chars).
 *
 * <p><strong>Why the random suffix.</strong> The id used to be a pure function of
 * the folio, which broke in two ways at once. Recreating the database restarts
 * the folio sequence, so a new deployment re-issues ids the gateway already
 * considers paid and refuses the checkout. Worse, a re-derivable id is a
 * forgeable one: the folio is typed by the applicant and the amount is the same
 * for every ficha, so anybody who knows a folio could present that id as proof
 * of payment. {@link ConfirmFichaPaymentVerifiedUseCaseImpl} asks the gateway
 * about {@code payment.getOrderId()}, so an id the applicant can predict is an
 * id the applicant can satisfy. The suffix is what makes the id unguessable,
 * and {@code build()} is called once per checkout with the result persisted by
 * {@code registerCheckout()}, so nothing needs it to be re-derivable.
 *
 * <p>The parts are assembled separately so the cap is spent where it should
 * be: the prefix is never truncated, the folio gives up characters from the
 * left, and the suffix is never truncated — that is the part carrying the
 * uniqueness.
 */
public class OrderIdBuilder {

	/** Uppercase + digits only: a safe subset of the charset the gateway allows. */
	private static final char[] SUFFIX_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

	private static final int SUFFIX_LENGTH = 6;

	private final String prefix;

	private final int maxLength;

	private final RandomGenerator random;

	public OrderIdBuilder(String prefix, int maxLength) {
		this(prefix, maxLength, new SecureRandom());
	}

	OrderIdBuilder(String prefix, int maxLength, RandomGenerator random) {
		this.prefix = prefix == null ? "" : prefix.trim();
		this.maxLength = maxLength <= 0 ? 32 : maxLength;
		this.random = random;
	}

	public String build(String folio) {
		if (folio == null || folio.isBlank()) {
			throw new EvoPaymentGatewayException("No se pudo generar el identificador del pedido: folio vacío.");
		}

		String cleanFolio = sanitize(folio);
		if (cleanFolio.isEmpty()) {
			throw new EvoPaymentGatewayException("No se pudo generar el identificador del pedido.");
		}

		String cleanPrefix = sanitize(prefix);
		String suffix = randomSuffix();

		// Everything the cap leaves after the dash + suffix goes to the head, and
		// from there the prefix takes its cut first: it names the environment, so it
		// is what makes an order recognisable in Evo's console. The folio is what
		// gives way, and only from the left, so the digits that identify the ficha
		// survive. The suffix is never in the budget.
		int headBudget = maxLength - suffix.length() - 1;
		int folioRoom = headBudget - (cleanPrefix.isEmpty() ? 0 : cleanPrefix.length() + 1);
		if (folioRoom < 1) {
			throw new EvoPaymentGatewayException(
					"No se pudo generar el identificador del pedido: EVO_ORDER_ID_LENGTH=" + maxLength
							+ " no alcanza para el prefijo y el sufijo.");
		}
		if (cleanFolio.length() > folioRoom) {
			cleanFolio = cleanFolio.substring(cleanFolio.length() - folioRoom);
			// The cut can expose a separator that used to sit inside the folio, which
			// would double up against the one after the prefix. Costs a char of an
			// already-shrunken folio; the cap is a ceiling, not a target.
			cleanFolio = cleanFolio.replaceAll("^-+", "");
			if (cleanFolio.isEmpty()) {
				throw new EvoPaymentGatewayException("No se pudo generar el identificador del pedido.");
			}
		}

		String head = cleanPrefix.isEmpty() ? cleanFolio : cleanPrefix + "-" + cleanFolio;
		return head + "-" + suffix;
	}

	/**
	 * The order id is the applicant's only non-repudiation handle at the gateway,
	 * so the suffix is drawn from a cryptographically strong source rather than
	 * {@link java.util.Random}.
	 */
	private String randomSuffix() {
		StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
		for (int i = 0; i < SUFFIX_LENGTH; i++) {
			suffix.append(SUFFIX_ALPHABET[random.nextInt(SUFFIX_ALPHABET.length)]);
		}
		return suffix.toString();
	}

	private static String sanitize(String value) {
		return value.replaceAll("[^0-9A-Za-z_-]", "").replaceAll("^-+|-+$", "");
	}
}
