package mx.edu.utez.sisa.admission.infrastructure.web;

import mx.edu.utez.sisa.admission.shared.exception.TooManyPaymentAccessAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PaymentAccessRateLimiter}, the per-IP throttle that
 * makes the weak {@code folio + last-3-CURP} identity proof on
 * {@code POST /candidates/payment-access} survivable.
 *
 * <p>Driven with a mutable {@link Clock} instead of {@code Thread.sleep} so the
 * window-expiry behaviour is deterministic and the suite stays fast.
 */
class PaymentAccessRateLimiterTest {

	private static final int MAX_ATTEMPTS = 3;

	private static final Duration WINDOW = Duration.ofMinutes(10);

	private MutableClock clock;

	private PaymentAccessRateLimiter limiter;

	/** Minimal settable clock so the suite never sleeps. */
	private static final class MutableClock extends Clock {

		private Instant instant = Instant.parse("2026-09-26T12:00:00Z");

		void advance(Duration amount) {
			instant = instant.plus(amount);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}

	@BeforeEach
	void setUp() {
		clock = new MutableClock();
		limiter = new PaymentAccessRateLimiter(MAX_ATTEMPTS, WINDOW, clock);
	}

	@Test
	void allowsUpToTheBudgetThenRejects() {
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			int attempt = i;
			assertThatCode(() -> limiter.checkAllowed("10.0.0.1")).doesNotThrowAnyException();
		}

		assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.1"))
				.isInstanceOf(TooManyPaymentAccessAttemptsException.class)
				.hasMessageContaining("Demasiados intentos");
	}

	@Test
	void budgetsAreIndependentPerClient() {
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			limiter.checkAllowed("10.0.0.1");
		}

		// a different IP is unaffected by the first one's exhaustion
		assertThatCode(() -> limiter.checkAllowed("10.0.0.2")).doesNotThrowAnyException();
	}

	@Test
	void windowExpiryRestoresTheBudget() {
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			limiter.checkAllowed("10.0.0.1");
		}
		assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.1"))
				.isInstanceOf(TooManyPaymentAccessAttemptsException.class);

		// still inside the window → the very next call is still blocked
		clock.advance(WINDOW.minusSeconds(1));
		assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.1"))
				.isInstanceOf(TooManyPaymentAccessAttemptsException.class);

		// past the window → the old attempts are evicted and it works again
		clock.advance(Duration.ofSeconds(2));
		assertThatCode(() -> limiter.checkAllowed("10.0.0.1")).doesNotThrowAnyException();
	}

	@Test
	void rejectedAttemptsDoNotExtendTheWindow() {
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			limiter.checkAllowed("10.0.0.1");
		}
		// a burst of rejects must not push the expiry forward indefinitely
		clock.advance(WINDOW.plusSeconds(1));
		assertThatCode(() -> limiter.checkAllowed("10.0.0.1")).doesNotThrowAnyException();
	}

	@Test
	void budgetsAreSlidingNotFixed() {
		limiter.checkAllowed("10.0.0.1");
		clock.advance(Duration.ofMinutes(6));
		limiter.checkAllowed("10.0.0.1");
		clock.advance(Duration.ofMinutes(6));

		// the first attempt has aged out, so a third fits inside the window
		assertThatCode(() -> limiter.checkAllowed("10.0.0.1")).doesNotThrowAnyException();
	}

	@Test
	void clientKeyPrefersTheLeftMostForwardedHop() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Forwarded-For", "203.0.113.7, 70.41.3.18, 150.172.238.178");
		request.setRemoteAddr("10.0.0.1");

		assertThat(PaymentAccessRateLimiter.clientKeyOf(request)).isEqualTo("203.0.113.7");
	}

	@Test
	void clientKeyFallsBackToTheRemoteAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.9");

		assertThat(PaymentAccessRateLimiter.clientKeyOf(request)).isEqualTo("10.0.0.9");
	}

	@Test
	void clientKeyCollapsesUnknownClientsIntoOneSharedBucket() {
		// MockHttpServletRequest defaults to 127.0.0.1, so both have to be
		// forced to "no usable address" to exercise the fallback.
		MockHttpServletRequest sinRemoto = new MockHttpServletRequest();
		sinRemoto.setRemoteAddr(null);
		MockHttpServletRequest vacio = new MockHttpServletRequest();
		vacio.setRemoteAddr("  ");

		// both must land on the same key, otherwise a null/blank address would
		// hand an attacker a fresh budget per request
		assertThat(PaymentAccessRateLimiter.clientKeyOf(sinRemoto)).isEqualTo("unknown");
		assertThat(PaymentAccessRateLimiter.clientKeyOf(vacio)).isEqualTo("unknown");
	}
}
