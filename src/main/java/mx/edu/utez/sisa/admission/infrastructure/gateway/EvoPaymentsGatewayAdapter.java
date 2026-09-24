package mx.edu.utez.sisa.admission.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort;
import mx.edu.utez.sisa.admission.infrastructure.config.EvoConfig;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EVO (Mastercard Payment Gateway) REST adapter — Hosted Checkout model. Talks
 * to {@code {EVO_BASE_URL}/session} ({@code INITIATE_CHECKOUT}) and
 * {@code {EVO_BASE_URL}/order/{orderId}} ({@code RETRIEVE_ORDER}) with Basic
 * HTTP auth ({@code merchant.<merchantId>}:{@code apiPassword}), JSON payloads
 * per the integration guide ({@code cosas-pagos/evo.txt}).
 *
 * <p>Every gateway failure — unconfigured keys, transport/network problems,
 * or a non-2xx/rejected operation from EVO — surfaces as
 * {@link EvoPaymentGatewayException} (→ HTTP 502): the processor is an
 * upstream dependency the applicant cannot fix, so the caller gets a clear
 * "Pago en línea no disponible" instead of a generic 500.
 *
 * <p>Registered with an explicit bean name to keep the same disambiguation
 * convention as the three {@code GlobalExceptionHandler}s (only this context's
 * handler lives in {@code admission}).
 */
@Component("evoPaymentsGatewayAdapter")
public class EvoPaymentsGatewayAdapter implements EvoPaymentsGatewayPort {

	private static final Logger log = LoggerFactory.getLogger(EvoPaymentsGatewayAdapter.class);

	private final RestClient restClient;

	private final EvoConfig evoConfig;

	private final ObjectMapper objectMapper;

	public EvoPaymentsGatewayAdapter(RestClient.Builder builder, EvoConfig evoConfig, ObjectMapper objectMapper) {
		this.evoConfig = evoConfig;
		this.objectMapper = objectMapper;
		RestClient.Builder rest = builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth(evoConfig));
		if (evoConfig.baseUrl() != null && !evoConfig.baseUrl().isBlank()) {
			rest = rest.baseUrl(stripTrailingSlash(evoConfig.baseUrl()));
		}
		this.restClient = rest.build();
	}

	@Override
	public EvoSession initiateCheckoutSession(EvoOrder order) {
		evoConfig.validate();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("apiOperation", "INITIATE_CHECKOUT");
		payload.put("checkoutMode", "WEBSITE");
		payload.put("order", orderPayload(order));
		payload.put("interaction", interactionPayload(order));

		SessionResponse res = post("/session", payload, SessionResponse.class);
		if (res == null || !"SUCCESS".equals(res.result())) {
			throw new EvoPaymentGatewayException(
					"El proveedor de pagos (EVO) no pudo iniciar el pago en línea: " + describeError(res == null ? null : res.error()));
		}
		if (res.session() == null || res.session().id() == null || res.session().id().isBlank()) {
			throw new EvoPaymentGatewayException("El proveedor de pagos (EVO) no devolvió una sesión de pago.");
		}
		log.info("EVO: sesión de pago iniciada para la orden {}", order.id());
		return new EvoSession(res.session().id(), res.merchant(), res.successIndicator(), res.session().version());
	}

	@Override
	public EvoOrderStatus retrieveOrder(String orderId) {
		evoConfig.validate();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("apiOperation", "RETRIEVE_ORDER");

		OrderRetrieveResponse res = post("/order/" + orderId, payload, OrderRetrieveResponse.class);
		if (res == null) {
			throw new EvoPaymentGatewayException(
					"El proveedor de pagos (EVO) no respondió al consultar el pedido " + orderId + ".");
		}
		BigDecimal amount = res.order() != null && res.order().amount() != null
				? new BigDecimal(res.order().amount()) : null;
		return new EvoOrderStatus(orderId, res.result(), amount);
	}

	private <T> T post(String path, Map<String, Object> payload, Class<T> responseType) {
		try {
			return restClient.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(payload)
					.retrieve().body(responseType);
		} catch (RestClientResponseException ex) {
			throw gatewayException(ex.getResponseBodyAsString(), ex);
		} catch (RestClientException ex) {
			throw new EvoPaymentGatewayException(
					"No se pudo contactar al proveedor de pagos (EVO): " + rootCauseMessage(ex), ex);
		}
	}

	private EvoPaymentGatewayException gatewayException(String body, RestClientResponseException cause) {
		String detail = parseErrorDetail(body);
		return new EvoPaymentGatewayException(
				"El proveedor de pagos (EVO) rechazó la operación" + (detail.isBlank() ? "" : ": " + detail), cause);
	}

	private String parseErrorDetail(String body) {
		if (body == null || body.isBlank()) {
			return "";
		}
		String condensed;
		try {
			ErrorBody errorBody = objectMapper.readValue(body, ErrorBody.class);
			if (errorBody.error() == null || (errorBody.error().cause() == null && errorBody.error().explanation() == null)) {
				condensed = body;
			} else {
				String cause = errorBody.error().cause() == null ? "" : errorBody.error().cause();
				String explanation = errorBody.error().explanation() == null ? "" : errorBody.error().explanation();
				condensed = (cause + " — " + explanation).replaceAll("\\s+", " ").trim();
			}
		} catch (Exception parseEx) {
			condensed = body;
		}
		return condensed.length() > 200 ? condensed.substring(0, 200) + "…" : condensed;
	}

	private static String describeError(Object error) {
		if (error instanceof ErrorInfo info) {
			String cause = info.cause() == null ? "" : info.cause();
			String explanation = info.explanation() == null ? "" : info.explanation();
			String detail = (cause + " — " + explanation).replaceAll("\\s+", " ").trim();
			if (!detail.isBlank()) {
				return detail;
			}
		}
		return "el proveedor no aceptó la operación";
	}

	private static Map<String, Object> orderPayload(EvoOrder order) {
		Map<String, Object> orderMap = new LinkedHashMap<>();
		orderMap.put("id", order.id());
		orderMap.put("amount", order.amount() == null ? null : order.amount().toPlainString());
		orderMap.put("currency", order.currency());
		putIfNotNull(orderMap, "description", order.description());
		return orderMap;
	}

	private static Map<String, Object> interactionPayload(EvoOrder order) {
		Map<String, Object> interaction = new LinkedHashMap<>();
		interaction.put("operation", "PURCHASE");
		putIfNotNull(interaction, "returnUrl", order.returnUrl());
		putIfNotNull(interaction, "cancelUrl", order.cancelUrl());
		return interaction;
	}

	private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
	}

	private static String basicAuth(EvoConfig config) {
		String username = config.apiUsername() == null ? "" : config.apiUsername();
		String password = config.apiPassword() == null ? "" : config.apiPassword();
		return Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private static String rootCauseMessage(Throwable ex) {
		Throwable root = ex;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String message = root.getMessage();
		if (message == null || message.isBlank()) {
			return root.getClass().getSimpleName();
		}
		String condensed = message.replaceAll("\\s+", " ").trim();
		return condensed.length() > 200 ? condensed.substring(0, 200) + "…" : condensed;
	}

	// ── EVO JSON response shapes (only the fields this integration consumes) ──

	private record SessionResponse(String result, String merchant, String successIndicator, Session session,
			ErrorInfo error) {
		record Session(String id, String version) {
		}
	}

	private record OrderRetrieveResponse(String result, Order order, ErrorInfo error) {
		record Order(String id, String amount, String currency) {
		}
	}

	private record ErrorInfo(String cause, String explanation) {
	}

	private record ErrorBody(ErrorInfo error) {
	}
}