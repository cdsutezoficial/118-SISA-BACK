package mx.edu.utez.sisa.admission.infrastructure.web.dto;

/**
 * Optional body for {@code POST /candidates/{id}/payments/confirm} — the EVO
 * {@code order.id} the applicant's browser carries back from the gateway
 * return URL. When present it MUST match the order persisted at checkout
 * (Fase 4) and EVO must report {@code SUCCESS} for it before the ficha is
 * marked paid (Fase 5). Absent = legacy window confirm (Finanzas, no online
 * session was initiated).
 */
public record VerifyFichaPaymentRequest(String orderId) {
}