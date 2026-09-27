package mx.edu.utez.sisa.admission.infrastructure.config;

import mx.edu.utez.sisa.admission.shared.exception.EvoPaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EvoConfig}: safe-boot defaults (order id length 32,
 * currency MXN when absent) and request-time {@code validate()} which throws a
 * clear 502 message when the gateway credentials are missing — never a generic
 * 500, and never a startup failure while the rest of the app is fine.
 */
class EvoConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(UserConfigurations.of(PropertiesConfig.class));

	@EnableConfigurationProperties(EvoConfig.class)
	static class PropertiesConfig {
	}

	@Test
	void appliesSafeDefaultsWhenPropertiesAreAbsent() {
		runner.run(context -> {
			EvoConfig evo = context.getBean(EvoConfig.class);
			assertThat(evo.orderIdLength()).isEqualTo(40);
			assertThat(evo.currency()).isEqualTo("MXN");
		});
	}

	@Test
	void bindsConfiguredValues() {
		runner.withPropertyValues(
				"sisa.evo.base-url=https://evopaymentsmexico.gateway.mastercard.com/api/rest/version/72/merchant/TESTUTEZ",
				"sisa.evo.merchant-id=TESTUTEZ", "sisa.evo.order-id-length=40", "sisa.evo.currency=USD").run(context -> {
					EvoConfig evo = context.getBean(EvoConfig.class);
					assertThat(evo.baseUrl()).contains("/merchant/TESTUTEZ");
					assertThat(evo.merchantId()).isEqualTo("TESTUTEZ");
					assertThat(evo.orderIdLength()).isEqualTo(40);
					assertThat(evo.currency()).isEqualTo("USD");
				});
	}

	/**
	 * The guide caps {@code order.id} at 40 chars, so a length above that is not a
	 * stricter policy — it is an order id the gateway rejects at checkout. The
	 * config pulls it down instead of passing it through, which is what keeps a
	 * mistyped {@code EVO_ORDER_ID_LENGTH} from failing only at the first payment.
	 */
	@Test
	void clampsTheOrderIdLengthToTheGatewayCeiling() {
		runner.withPropertyValues("sisa.evo.order-id-length=64").run(
				context -> assertThat(context.getBean(EvoConfig.class).orderIdLength()).isEqualTo(40));
	}

	@Test
	void fallsBackToTheCeilingWhenTheLengthIsNotANumber() {
		runner.withPropertyValues("sisa.evo.order-id-length=0", "sisa.evo.order-id-length=-1").run(context -> {
			assertThat(context.getBean(EvoConfig.class).orderIdLength()).isEqualTo(40);
		});
	}

	@Test
	void validateThrowsClearMessageWhenCredentialsAreMissing() {
		runner.run(context -> {
			EvoConfig evo = context.getBean(EvoConfig.class);
			assertThatThrownBy(evo::validate).isInstanceOf(EvoPaymentGatewayException.class)
					.hasMessageContaining("Pago en línea no configurado");
		});
	}

	@Test
	void validatePassesWhenGatewayIsConfigured() {
		runner.withPropertyValues(
				"sisa.evo.base-url=https://evopaymentsmexico.gateway.mastercard.com/api/rest/version/72/merchant/TESTUTEZ",
				"sisa.evo.api-username=merchant.TESTUTEZ", "sisa.evo.api-password=secret",
				"sisa.evo.merchant-id=TESTUTEZ",
				"sisa.evo.checkout-js-url=https://evopaymentsmexico.gateway.mastercard.com/static/checkout/checkout.min.js",
				"sisa.evo.return-url=http://localhost:5173/portal/registro/ficha",
				"sisa.evo.cancel-url=http://localhost:5173/portal/registro/ficha")
				.run(context -> context.getBean(EvoConfig.class).validate());
	}

	@Test
	void validateRejectsMerchantMismatch() {
		runner.withPropertyValues(
				"sisa.evo.base-url=https://evopaymentsmexico.gateway.mastercard.com/api/rest/version/72/merchant/OTHER",
				"sisa.evo.api-username=merchant.TESTUTEZ", "sisa.evo.api-password=secret",
				"sisa.evo.merchant-id=TESTUTEZ",
				"sisa.evo.checkout-js-url=https://evopaymentsmexico.gateway.mastercard.com/static/checkout/checkout.min.js",
				"sisa.evo.return-url=http://localhost:5173/portal/registro/ficha",
				"sisa.evo.cancel-url=http://localhost:5173/portal/registro/ficha")
				.run(context -> assertThatThrownBy(context.getBean(EvoConfig.class)::validate)
						.isInstanceOf(EvoPaymentGatewayException.class).hasMessageContaining("merchant"));
	}

	@Test
	void validateRejectsUsernameMerchantMismatch() {
		runner.withPropertyValues(
				"sisa.evo.base-url=https://evopaymentsmexico.gateway.mastercard.com/api/rest/version/72/merchant/TESTUTEZ",
				"sisa.evo.api-username=merchant.OTHER", "sisa.evo.api-password=secret",
				"sisa.evo.merchant-id=TESTUTEZ",
				"sisa.evo.checkout-js-url=https://evopaymentsmexico.gateway.mastercard.com/static/checkout/checkout.min.js",
				"sisa.evo.return-url=http://localhost:5173/portal/registro/ficha",
				"sisa.evo.cancel-url=http://localhost:5173/portal/registro/ficha")
				.run(context -> assertThatThrownBy(context.getBean(EvoConfig.class)::validate)
						.isInstanceOf(EvoPaymentGatewayException.class).hasMessageContaining("usuario"));
	}
}