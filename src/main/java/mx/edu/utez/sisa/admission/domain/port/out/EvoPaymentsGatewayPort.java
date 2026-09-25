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

	record EvoOrderStatus(String orderId, String result, BigDecimal amount) {
	}
}