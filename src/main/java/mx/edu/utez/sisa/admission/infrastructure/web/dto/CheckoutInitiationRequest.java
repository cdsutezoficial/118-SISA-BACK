package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Optional body of {@code POST /candidates/{id}/payments/checkout}.
 *
 * <p>It exists for one reason: the gateway gets a SINGLE configured
 * {@code returnUrl} ({@code sisa.evo.return-url}), so a payer is always sent
 * back to the same screen. That is fine while only the post-registration flow
 * existed, but the "vuelve a pagar mi ficha" flow starts from
 * {@code /portal/ficha/pago} and must land back <em>there</em> — otherwise
 * a 3D Secure challenge would bounce the applicant onto
 * {@code /portal/registro/ficha}, i.e. onto her full ficha, having proved
 * nothing more than a sequential folio plus 3 CURP characters.
 *
 * <p><b>Security.</b> This is an open-redirect surface, so {@code returnPath} is
 * <b>not</b> used as-is. The pattern rejects anything that is not a plain
 * absolute in-app path — {@code //evil.com} (protocol-relative) and {@code ..}
 * (traversal) are refused here — and the use case additionally requires the
 * value to be on a configured allowlist before it is honoured. The final URL is
 * always built from the configured return URL's own scheme and host with only
 * the path swapped, so a successful exploit cannot even leave our origin.
 */
public record CheckoutInitiationRequest(

		/**
		 * In-app path to return to, e.g. {@code /portal/ficha/pago}. Blank
		 * or absent means "use the configured default".
		 */
		@Pattern(regexp = "^/(?!/)(?!.*\\.\\.)[A-Za-z0-9/_-]*$", message = "Ruta de retorno inválida") String returnPath) {
}
