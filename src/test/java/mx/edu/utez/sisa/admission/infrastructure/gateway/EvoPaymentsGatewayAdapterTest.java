package mx.edu.utez.sisa.admission.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort.EvoOrder;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort.EvoOrderStatus;
import mx.edu.utez.sisa.admission.domain.port.out.EvoPaymentsGatewayPort.EvoSession;
import mx.edu.utez.sisa.admission.infrastructure.config.EvoConfig;
import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Adapter tests against a mocked EVO gateway (MockRestServiceServer bound to
 * the RestClient builder): payload shape, session initiation, order retrieval,
 * and the failure paths (non-SUCCESS result, HTTP error body, transport
 * failure, missing credentials) all surfacing as
 * {@link EvoPaymentGatewayException}.
 */
class EvoPaymentsGatewayAdapterTest {

	private static final String BASE = "https://evopaymentsmexico.gateway.mastercard.com/api/rest/version/72/merchant/TESTUTEZ";
	private static final String RETURN = "http://localhost:5173/portal/registro/ficha";
	private static final String ORDER_ID = "TESTUTEZ-ADM-2026-000001";

	private MockRestServiceServer server;

	private EvoPaymentsGatewayAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new EvoPaymentsGatewayAdapter(builder, configuredConfig(), new ObjectMapper());
	}

	private static EvoConfig configuredConfig() {
		return new EvoConfig(BASE, "merchant.TESTUTEZ", "secret", "TESTUTEZ", null, RETURN, RETURN, "TESTUTEZ", 32,
				"MXN");
	}

	private static EvoOrder order() {
		return new EvoOrder(ORDER_ID, "Ficha de Admisión", new BigDecimal("500.00"), "MXN", RETURN, RETURN);
	}

	@Test
	void initiatesCheckoutWithTheExpectedPayload() {
		server.expect(requestTo(BASE + "/session")).andExpect(method(HttpMethod.POST)).andExpect(content().json("""
				{
				  "apiOperation": "INITIATE_CHECKOUT",
				  "checkoutMode": "WEBSITE",
				  "order": { "id": "TESTUTEZ-ADM-2026-000001", "amount": "500.00", "currency": "MXN", "description": "Ficha de Admisión" },
				  "interaction": { "operation": "PURCHASE", "returnUrl": "http://localhost:5173/portal/registro/ficha", "cancelUrl": "http://localhost:5173/portal/registro/ficha" }
				}
				""")).andRespond(withSuccess("""
				{"result":"SUCCESS","merchant":"TESTUTEZ","successIndicator":"AAAA/BRAVO/SUCCESS0001",
				 "session":{"id":"SESSION0001BR","version":"1"}}
				""", MediaType.APPLICATION_JSON));

		EvoSession session = adapter.initiateCheckoutSession(order());

		assertThat(session.id()).isEqualTo("SESSION0001BR");
		assertThat(session.merchant()).isEqualTo("TESTUTEZ");
		assertThat(session.successIndicator()).isEqualTo("AAAA/BRAVO/SUCCESS0001");
		assertThat(session.version()).isEqualTo("1");
		server.verify();
	}

	@Test
	void failsClearlyWhenSessionResultIsNotSuccess() {
		server.expect(requestTo(BASE + "/session")).andRespond(withSuccess("""
				{"result":"FAILURE","error":{"cause":"SERVER_BUSY","explanation":"try again later"}}
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.initiateCheckoutSession(order()))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("no pudo iniciar el pago en línea")
				.hasMessageContaining("SERVER_BUSY");
	}

	@Test
	void surfacesHttpErrorBodyFromTheGateway() {
		server.expect(requestTo(BASE + "/session")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.body("{\"error\":{\"cause\":\"INVALID_REQUEST\",\"explanation\":\"order.amount is invalid\"}}")
				.contentType(MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.initiateCheckoutSession(order()))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("rechazó la operación")
				.hasMessageContaining("order.amount is invalid");
	}

	@Test
	void wrapsTransportFailuresInDefaultSpanishMessage() {
		server.expect(requestTo(BASE + "/session")).andRespond(withException(new ConnectException("Connection refused")));

		assertThatThrownBy(() -> adapter.initiateCheckoutSession(order()))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("No se pudo contactar al proveedor de pagos (EVO)");
	}

	@Test
	void retrievesOrderResultAndAmount() {
		server.expect(requestTo(BASE + "/order/" + ORDER_ID)).andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"apiOperation\":\"RETRIEVE_ORDER\"}")).andRespond(withSuccess("""
				{"result":"SUCCESS","order":{"id":"TESTUTEZ-ADM-2026-000001","amount":"500.00","currency":"MXN"}}
				""", MediaType.APPLICATION_JSON));

		EvoOrderStatus status = adapter.retrieveOrder(ORDER_ID);

		assertThat(status.orderId()).isEqualTo(ORDER_ID);
		assertThat(status.result()).isEqualTo("SUCCESS");
		assertThat(status.amount()).isEqualByComparingTo("500.00");
		server.verify();
	}

	@Test
	void verifyThrowsClearMessageWhenGatewayIsNotConfigured() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer unused = MockRestServiceServer.bindTo(builder).build();
		EvoPaymentsGatewayAdapter bareAdapter = new EvoPaymentsGatewayAdapter(builder,
				new EvoConfig(null, null, null, null, null, null, null, null, null, null),
				new ObjectMapper());

		assertThatThrownBy(() -> bareAdapter.initiateCheckoutSession(order()))
				.isInstanceOf(EvoPaymentGatewayException.class)
				.hasMessageContaining("Pago en línea no configurado");
	}
}