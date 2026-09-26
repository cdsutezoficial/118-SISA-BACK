package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /candidates/payment-access} — the applicant's identity
 * for the "vuelve a pagar mi ficha" flow: her folio and the last 3 characters
 * of her CURP.
 *
 * <p>The constraints are a cheap first line of defence only; the real checks
 * (and the throttling) are in {@code AccessFichaPaymentUseCaseImpl} and
 * {@code PaymentAccessRateLimiter}. {@code @Pattern} mirrors the portal's own
 * folio format so a typo gets an inline message instead of a round trip.
 */
public record FichaPaymentAccessRequest(

		@NotBlank(message = "El folio es obligatorio.")
		@Pattern(regexp = "^ADM-\\d{4}-\\d{6}$", message = "El folio no tiene el formato esperado (ej. ADM-2026-000101).")
		String folio,

		@NotBlank(message = "Los últimos 3 caracteres de tu CURP son obligatorios.")
		@Size(min = 3, max = 3, message = "Ingresa exactamente 3 caracteres de tu CURP.")
		String curpSuffix) {
}
