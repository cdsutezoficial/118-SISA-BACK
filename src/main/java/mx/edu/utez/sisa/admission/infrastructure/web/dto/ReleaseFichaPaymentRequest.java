package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /candidates/{id}/payments/release} — the attempt the
 * applicant's browser gave up on.
 *
 * <p>{@code @NotBlank} rather than optional, for the same reason
 * {@code VerifyFichaPaymentRequest} has it: the use case settles exactly one attempt
 * by id, so an absent id has nothing to settle. Falling through to "release the
 * latest attempt" would be guessing, and with retries there are several.
 */
public record ReleaseFichaPaymentRequest(@NotBlank String orderId) {
}
