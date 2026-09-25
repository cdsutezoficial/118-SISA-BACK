package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase.InitiateCheckoutResult;

import java.util.UUID;

/**
 * Body for {@code POST /candidates/{id}/payments/checkout} — the EVO Hosted
 * Checkout session consumed by the portal's embedded payment panel: the
 * merchant-side {@code order.id}, the hosted {@code session.id}, the
 * {@code successIndicator} returned to the configured return URL, and the
 * official Checkout SDK URL loaded by the browser.
 */
public record CheckoutInitiationResponse(UUID candidateId, String orderId, String sessionId, String merchant,
		String successIndicator, String checkoutJsUrl) {

	public static CheckoutInitiationResponse from(InitiateCheckoutResult result) {
		return new CheckoutInitiationResponse(result.candidateId(), result.orderId(), result.sessionId(),
				result.merchant(), result.successIndicator(), result.checkoutJsUrl());
	}
}
