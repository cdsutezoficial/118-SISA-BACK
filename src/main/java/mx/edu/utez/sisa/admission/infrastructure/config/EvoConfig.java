package mx.edu.utez.sisa.admission.infrastructure.config;

import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

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
		String currency, String merchantName) {

	public EvoConfig {
		baseUrl = trimToNull(baseUrl);
		apiUsername = trimToNull(apiUsername);
		apiPassword = trimToNull(apiPassword);
		merchantId = trimToNull(merchantId);
		checkoutJsUrl = trimToNull(checkoutJsUrl);
		returnUrl = trimToNull(returnUrl);
		cancelUrl = trimToNull(cancelUrl);
		orderIdPrefix = trimToNull(orderIdPrefix);
		merchantName = trimToNull(merchantName);
		orderIdLength = orderIdLength == null || orderIdLength <= 0 ? 32 : orderIdLength;
		currency = currency == null || currency.isBlank() ? "MXN" : currency.trim();
	}

	/**
	 * The gateway's mandatory prerequisites are present and consistent. Throws
	 * {@link EvoPaymentGatewayException} at REQUEST time — not construction —
	 * so the app boots fine without EVO and only the checkout/confirm flows
	 * surface the 502.
	 */
	public void validate() {
		boolean missing = baseUrl == null || apiUsername == null || apiPassword == null || merchantId == null
				|| checkoutJsUrl == null || returnUrl == null || cancelUrl == null;
		if (missing) {
			throw new EvoPaymentGatewayException(
					"Pago en línea no configurado. El proveedor de pagos (EVO) no está disponible, contacta a Servicios Escolares.");
		}
		String baseMerchant = merchantFromBaseUrl();
		if (!isHttpsUrl(baseUrl) || baseMerchant == null || !merchantId.equals(baseMerchant)) {
			throw new EvoPaymentGatewayException(
					"Pago en línea no configurado correctamente. El merchant de EVO no coincide con la cuenta configurada.");
		}
		if (!("merchant." + merchantId).equals(apiUsername)) {
			throw new EvoPaymentGatewayException(
					"Pago en línea no configurado correctamente. El usuario de EVO no corresponde al merchant configurado.");
		}
		if (!isHttpsUrl(checkoutJsUrl) || !isHttpUrl(returnUrl) || !isHttpUrl(cancelUrl)) {
			throw new EvoPaymentGatewayException(
					"Pago en línea no configurado correctamente. Revisa las URLs de EVO y de retorno.");
		}
	}

	private String merchantFromBaseUrl() {
		try {
			String path = URI.create(baseUrl).getPath();
			if (path == null) {
				return null;
			}
			int separator = path.lastIndexOf('/');
			return separator >= 0 && separator < path.length() - 1 ? path.substring(separator + 1) : null;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static boolean isHttpsUrl(String value) {
		return isHttpUrlWithScheme(value, "https");
	}

	private static boolean isHttpUrl(String value) {
		return isHttpUrlWithScheme(value, "http") || isHttpUrlWithScheme(value, "https");
	}

	private static boolean isHttpUrlWithScheme(String value, String scheme) {
		try {
			URI uri = URI.create(value);
			return scheme.equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
