package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /candidates/{id}/payments/confirm} — the EVO
 * {@code order.id} the applicant's browser carries back from the gateway
 * return URL. It MUST match the order persisted at checkout and EVO must report
 * {@code SUCCESS} for it before the ficha is marked paid.
 *
 * <p>Mandatory since the window-payment path was removed: previously the field
 * was optional and an absent value fell through to a local "pagado" with no
 * gateway check at all. {@code @NotBlank} turns that into a 400 at the edge,
 * and the use case re-checks it (domain tests bypass the web layer).
 */
public record VerifyFichaPaymentRequest(@NotBlank String orderId) {
}
