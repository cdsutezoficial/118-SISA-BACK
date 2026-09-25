package mx.edu.utez.sisa.admission.infrastructure.config;

import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EVO (Mastercard gateway) Hosted Checkout configuration, fed by the
 * {@code EVO_*} variables of {@code .env} through the {@code sisa.evo.*}
 * mappings in {@code application.properties}. Registered via
 * {@code @ConfigurationPropertiesScan} on {@code CoreApplication} (record
 * constructor binding — NOT a {@code @Component}, whose constructor would be
 * treated as plain autowiring).
 *
 * <p>Safe-boot by design: missing credentials do NOT fail startup.
 * {@link #validate()} fails at request time with a clear 502 ("Pago en línea
 * no configurado") instead of a generic 500, so the rest of the app keeps
 * running while the payment gateway is absent or misconfigured.
 */
@ConfigurationProperties(prefix = "sisa.evo")
public record EvoConfig(String baseUrl, String apiUsername, String apiPassword, String merchantId,
		String checkoutJsUrl, String returnUrl, String cancelUrl, String orderIdPrefix, Integer orderIdLength,
		String currency, String pageVersion, String merchantName) {

	/**
	 * Hosted Checkout page version used to build
	 * {@code {paymentPageBase}/api/page/version/{pageVersion}/pay}. It is the
	 * EVO <em>API</em> version (the same one the REST base URL carries, e.g.
	 * {@code /api/rest/version/72/}), NOT the per-session token the
	 * {@code INITIATE_CHECKOUT} response returns in {@code session.version} —
	 * the gateway rejects the latter there with
	 * {@code "Unsupported value for 'version = …'"}.
	 */
	public static final String DEFAULT_PAGE_VERSION = "72";

	public EvoConfig {
		orderIdLength = orderIdLength == null || orderIdLength <= 0 ? 32 : orderIdLength;
		currency = currency == null || currency.isBlank() ? "MXN" : currency;
		pageVersion = pageVersion == null || pageVersion.isBlank() ? DEFAULT_PAGE_VERSION : pageVersion.trim();
		merchantName = merchantName == null || merchantName.isBlank() ? null : merchantName.trim();
	}

	/**
	 * The gateway's mandatory prerequisites are present (base URL + API
	 * credentials + merchant id). Throws {@link EvoPaymentGatewayException} at
	 * REQUEST time — not construction — so the app boots fine without EVO and
	 * only the checkout/confirm flows surface the 502.
	 */
	public void validate() {
		boolean missing = baseUrl == null || baseUrl.isBlank() || apiUsername == null || apiUsername.isBlank()
				|| apiPassword == null || apiPassword.isBlank() || merchantId == null || merchantId.isBlank();
		if (missing) {
			throw new EvoPaymentGatewayException(
					"Pago en línea no configurado. El proveedor de pagos (EVO) no está disponible, contacta a Servicios Escolares.");
		}
	}
}