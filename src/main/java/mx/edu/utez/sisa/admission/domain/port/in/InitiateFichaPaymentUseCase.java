package mx.edu.utez.sisa.admission.domain.port.in;

import java.util.UUID;

/**
 * Initiates the online payment for a ficha (EVO Hosted Checkout, Fase 4 of the
 * payment plan): builds the gateway {@code order} (order.id = prefix + folio,
 * amount/currency/description/return/cancelUrl per config), asks the gateway
 * for a checkout {@code session}, and — the concept that registers the
 * transaction — persists both {@code order.id} and {@code session.id} on the
 * {@code AdmissionPayment}. Governs {@code POST /candidates/{id}/payments/checkout}.
 *
 * <p>Validations, in order: the candidate and its ADMISSION_FICHA payment must
 * exist (404); the payment must still be {@code PENDING} (409); then the
 * gateway call happens (failure → {@code EvoPaymentGatewayException} → 502).
 *
 * <p>The {@code returnPath} argument lets a flow ask for the gateway to hand
 * the payer back to its own screen. It is allowlisted server-side — see
 * {@code CheckoutInitiationRequest} for the open-redirect reasoning.
 */
public interface InitiateFichaPaymentUseCase {

	/**
	 * @param candidateId the ficha to pay
	 * @param returnPath  optional in-app path the gateway should return the payer
	 *                    to (e.g. {@code /portal/ficha/pago}). Honoured only
	 *                    when it is on the configured allowlist; otherwise the
	 *                    default {@code returnUrl} is used, so a caller can
	 *                    never turn this into an open redirect.
	 */
	InitiateCheckoutResult initiateCheckout(UUID candidateId, String returnPath);

	record InitiateCheckoutResult(UUID candidateId, String orderId, String sessionId, String merchant,
			String successIndicator, String checkoutJsUrl) {
	}
}