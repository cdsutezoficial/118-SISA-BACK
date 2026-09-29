package mx.edu.utez.sisa.admission.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import mx.edu.utez.sisa.admission.shared.exception.TooManyPaymentAccessAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP sliding-window throttle for {@code POST /candidates/payment-access}.
 *
 * <p>WHY: that endpoint is the applicant's way back into an unpaid ficha and it
 * authenticates with {@code folio + last 3 CURP characters}. Folios are
 * sequential ({@code ADM-2026-000101}, {@code …0102}, …) and the suffix space is
 * ~46k, so without a ceiling a script could walk the whole folio range and
 * brute-force each one. Locking a candidate out of their own payment screen is
 * a far better failure mode than an enumeration leak, and 10 tries per 10
 * minutes is generous for a human who mistypes their CURP once or twice.
 *
 * <p>DESIGN NOTES / KNOWN LIMITS — deliberate, not oversights:
 * <ul>
 * <li><b>Per-instance, in-memory.</b> Behind more than one replica the effective
 * limit is {@code limit × instances}. A shared store (Bucket4j/Redis) is the fix
 * if this ever runs multi-node; for a single-instance dev/university deployment
 * it is the right trade.</li>
 * <li><b>Keyed on the resolved remote address</b> ({@code X-Forwarded-For} first
 * hop when present), so an unforwarded proxy collapses to one bucket — that
 * throttles everyone, but fails closed rather than open.</li>
 * <li><b>Counted on every attempt</b>, successful or not. Only a match should
 * not be rate limited, and counting successes too keeps the accounting honest.</li>
 * <li><b>Expired buckets are swept</b> lazily on access plus opportunistically
 * every {@code maxEntries} inserts, so the map cannot grow without bound.</li>
 * </ul>
 *
 * <p>Configured via {@code sisa.admission.payment-access.max-attempts} (default
 * 10) and {@code ….window-minutes} (default 10).
 */
@Component
public class PaymentAccessRateLimiter {

	private static final Logger log = LoggerFactory.getLogger(PaymentAccessRateLimiter.class);

	/**
	 * Sweep the bucket map once it holds this many keys. Bounds memory when
	 * many distinct IPs hit the endpoint; the sweep is O(keys) but amortized
	 * over {@code maxEntries} inserts.
	 */
	private static final int maxEntriesBeforeSweep = 10_000;

	private final int maxAttempts;

	private final Duration window;

	private final Clock clock;

	private final Map<String, Deque<Instant>> attemptsByClient = new ConcurrentHashMap<>();

	private int insertsSinceSweep;

	/**
	 * Explicitly {@code @Autowired}: this class has a second constructor (the
	 * clock seam below), and with more than one constructor and no annotated
	 * one Spring falls back to looking for a no-arg constructor and fails the
	 * whole context with "No default constructor found". The @WebMvcTest suite
	 * cannot catch this because it mocks the bean; only a real context can.
	 */
	@Autowired
	public PaymentAccessRateLimiter(@Value("${sisa.admission.payment-access.max-attempts:10}") int maxAttempts,
			@Value("${sisa.admission.payment-access.window-minutes:10}") long windowMinutes) {
		this(maxAttempts, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
	}

	/** Test seam: lets the suite drive a fake clock instead of sleeping. */
	PaymentAccessRateLimiter(int maxAttempts, Duration window, Clock clock) {
		this.maxAttempts = Math.max(1, maxAttempts);
		this.window = window;
		this.clock = clock;
	}

	/**
	 * Records an attempt and throws when the client is over budget.
	 *
	 * @throws TooManyPaymentAccessAttemptsException when this client already
	 *                                                made {@code maxAttempts}
	 *                                                calls inside the window
	 */
	public void checkAllowed(String clientKey) {
		Instant now = clock.instant();
		Deque<Instant> attempts = attemptsByClient.computeIfAbsent(clientKey, key -> new ArrayDeque<>());

		boolean allowed;
		synchronized (attempts) {
			evictExpired(attempts, now);
			allowed = attempts.size() < maxAttempts;
			if (allowed) {
				attempts.addLast(now);
			}
		}

		if (!allowed) {
			log.warn("payment-access rate limit exceeded for client {} ({} attempts / {} min)", clientKey, maxAttempts,
					window.toMinutes());
			throw new TooManyPaymentAccessAttemptsException(
					"Demasiados intentos de acceso. Espera unos minutos antes de volver a intentarlo.");
		}

		sweepIfNeeded();
	}

	private void evictExpired(Deque<Instant> attempts, Instant now) {
		Instant cutoff = now.minus(window);
		while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
			attempts.removeFirst();
		}
	}

	private void sweepIfNeeded() {
		if (attemptsByClient.size() < maxEntriesBeforeSweep) {
			return;
		}
		Instant cutoff = clock.instant().minus(window);
		attemptsByClient.entrySet().removeIf(entry -> {
			Deque<Instant> attempts = entry.getValue();
			synchronized (attempts) {
				evictExpired(attempts, cutoff);
				return attempts.isEmpty();
			}
		});
		insertsSinceSweep = 0;
		log.debug("payment-access rate limiter swept buckets down to {} clients", attemptsByClient.size());
	}

	/**
	 * Best-effort client identity: the left-most {@code X-Forwarded-For} entry
	 * when a proxy sets it, else the servlet-resolved remote address. Returns
	 * {@code "unknown"} rather than {@code null} so every caller lands in one
	 * shared bucket instead of bypassing the limiter with a null key.
	 */
	public static String clientKeyOf(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			int comma = forwarded.indexOf(',');
			String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
			if (!first.isBlank()) {
				return first.trim();
			}
		}
		String remote = request.getRemoteAddr();
		return remote == null || remote.isBlank() ? "unknown" : remote;
	}

	/** Visible for tests asserting the bucket bookkeeping. */
	int trackedClients() {
		return attemptsByClient.size();
	}
}
