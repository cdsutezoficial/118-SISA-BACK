package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase.InitiateCheckoutResult;

import java.util.UUID;

/**
 * Body for {@code POST /candidates/{id}/payments/checkout} — the gateway
 * checkout session the portal consumes to redirect the applicant to the EVO
 * payment page: {@code order.id} (to verify on return, Fase 5), the hosted
 * session identifier and the derived {@code checkoutUrl}
 * ({paymentPage}/checkout/payment/{sessionId}?version={version}). Projection
 * of {@code InitiateCheckoutResult}.
 */
public record CheckoutInitiationResponse(UUID candidateId, String orderId, String sessionId, String version,
		String merchant, String successIndicator, String checkoutUrl) {

	public static CheckoutInitiationResponse from(InitiateCheckoutResult result) {
		return new CheckoutInitiationResponse(result.candidateId(), result.orderId(), result.sessionId(),
				result.version(), result.merchant(), result.successIndicator(), result.checkoutUrl());
	}
}