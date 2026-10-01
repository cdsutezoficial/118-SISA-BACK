package mx.edu.utez.sisa.admission.domain.port.out;

import java.math.BigDecimal;

/**
 * Port for the EVO (Mastercard Payment Gateway, Hosted Checkout model)
 * integration — the payment provider is an infrastructure dependency, so the
 * domain only sees this narrow contract. Implemented by
 * {@code EvoPaymentsGatewayAdapter} (REST + Basic auth against the gateway).
 *
 * <p>Two operations per integration guide ({@code evo.txt}):
 * <ul>
 * <li>{@link #initiateCheckoutSession} — {@code POST /session} with
 * {@code apiOperation=INITIATE_CHECKOUT}; returns the session the merchant
 * site uses with {@code checkout.min.js} (payment page).</li>
 * <li>{@link #retrieveOrder} — {@code GET /order/{orderId}} (the gateway routes on
 * method + path: retrieval is a GET and the response is flat, with
 * {@code result}/{@code amount} at the top level); lets the backend verify,
 * before marking the ficha PAID, that the payer actually completed a SUCCESSFUL
 * purchase (and that the amount matches the ficha order).</li>
 * </ul>
 */
public interface EvoPaymentsGatewayPort {

	EvoSession initiateCheckoutSession(EvoOrder order);

	EvoOrderStatus retrieveOrder(String orderId);

	/**
	 * @param id        the gateway order id (merchant-side, 32/64 alphanumerics)
	 * @param reference the merchant's own reference for the sale — SISA sends
	 *                  the ficha payment reference ({@code REF-…}) so the
	 *                  gateway statement reconciles against the ticket
	 */
	record EvoOrder(String id, String reference, String description, BigDecimal amount, String currency,
			String returnUrl, String cancelUrl) {
	}

	record EvoSession(String id, String merchant, String successIndicator, String version) {
	}

	/**
	 * What {@code Retrieve Order} reports about one order. The three fields are what
	 * the confirmation path has always needed; the settlement fields are what the daily
	 * sweep needs to tell "captured the money" from "said SUCCESS and captured nothing",
	 * and they are documented as ALWAYS PROVIDED by {@code Referencias de API.txt}.
	 *
	 * <p>There is no {@code gatewayCode}: it does not come back from {@code Retrieve
	 * Order}, so an order that reports {@code SUCCESS} with no capture cannot be
	 * diagnosed — it has to be retained, not investigated. See the sweep use case.
	 */
	record EvoOrderStatus(String orderId, String result, BigDecimal amount, BigDecimal totalAuthorizedAmount,
			BigDecimal totalCapturedAmount, BigDecimal totalDisbursedAmount, BigDecimal totalRefundedAmount,
			String creationTime, String lastUpdatedTime, String error) {

		/**
		 * A status carrying only what the confirmation path reads, with no settlement
		 * data. A non-null {@code totalCapturedAmount} keeps {@code SUCCESS} meaning
		 * "money was taken" for callers that predate the sweep, and {@code null} leaves
		 * it genuinely unknown rather than pretending nothing was captured.
		 */
		public EvoOrderStatus(String orderId, String result, BigDecimal amount, BigDecimal totalCapturedAmount) {
			this(orderId, result, amount, null, totalCapturedAmount, null, null, null, null, null);
		}

		/**
		 * The pre-sweep shape: {@code SUCCESS} with the amount, everything else unknown.
		 */
		public EvoOrderStatus(String orderId, String result, BigDecimal amount) {
			this(orderId, result, amount, null, "SUCCESS".equals(result) ? amount : null, null, null, null, null, null);
		}

		/** True when the bank reported an explicit error node, regardless of {@code result}. */
		public boolean hasError() {
			return error != null && !error.isBlank();
		}

		/** True when money actually moved, which outranks {@code result} in every decision. */
		public boolean capturedAny() {
			return totalCapturedAmount != null && totalCapturedAmount.signum() > 0;
		}
	}
}