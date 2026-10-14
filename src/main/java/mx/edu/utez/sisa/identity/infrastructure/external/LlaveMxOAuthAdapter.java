package mx.edu.utez.sisa.identity.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort;
import mx.edu.utez.sisa.identity.infrastructure.config.LlaveMxProperties;
import mx.edu.utez.sisa.identity.shared.exception.LlaveMxIntegrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Real {@link LlaveMxOAuthPort} adapter — OAuth 2.0 Authorization Code Grant
 * with PKCE against Llave Morelos.
 *
 * <p>Three documented details this class depends on:
 * <ul>
 * <li>The token and refresh calls are {@code application/x-www-form-urlencoded}
 * bodies, not JSON.</li>
 * <li>{@code redirect_uri} must be byte-identical to the one registered at the
 * hub or the provider rejects the exchange — hence it is sent from
 * configuration, never rebuilt from the incoming request.</li>
 * <li>{@code prompt=consent} is required, so the consent screen is always
 * forced.</li>
 * </ul>
 *
 * <p>Every upstream failure — unconfigured keys, transport errors, non-2xx
 * responses — becomes {@link LlaveMxIntegrationException} (HTTP 502). The
 * provider is an upstream dependency the caller cannot fix, so they get a
 * clear "LlaveMX no disponible" rather than a generic 500. Tokens are never
 * logged: only the subject is, at INFO, and never the code or verifier.
 */
@Component
public class LlaveMxOAuthAdapter implements LlaveMxOAuthPort {

	private static final Logger log = LoggerFactory.getLogger(LlaveMxOAuthAdapter.class);

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final LlaveMxProperties properties;

	public LlaveMxOAuthAdapter(RestClient.Builder builder, ObjectMapper objectMapper,
			LlaveMxProperties properties) {
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.restClient = builder
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	@Override
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	@Override
	public String buildAuthorizationUrl(String state, String challenge) {
		requireConfigured();
		return UriComponentsBuilder.fromUriString(properties.authorizeUrl())
				.queryParam("client_id", properties.clientId())
				.queryParam("response_type", "code")
				.queryParam("scope", properties.scope())
				.queryParam("state", state)
				.queryParam("code_challenge", challenge)
				.queryParam("code_challenge_method", "S256")
				.queryParam("prompt", properties.prompt())
				.queryParam("redirect_uri", properties.redirectUri())
				.build()
				.encode()
				.toUriString();
	}

	@Override
	public LlaveMxTokenSet exchangeAuthorizationCode(String authorizationCode, String codeVerifier) {
		requireConfigured();
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("code", authorizationCode);
		form.add("redirect_uri", properties.redirectUri());
		form.add("code_verifier", codeVerifier);
		return postForm(properties.tokenUrl(), form, "intercambio del authorization code");
	}

	@Override
	public LlaveMxTokenSet refreshAccessToken(String refreshToken) {
		requireConfigured();
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "refresh_token");
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("refresh_token", refreshToken);
		return postForm(properties.refreshUrl(), form, "renovacion del access token");
	}

	@Override
	public LlaveMxProfile fetchProfile(String accessToken) {
		requireConfigured();
		String body = call(() -> restClient.get()
				.uri(properties.entityUrl())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(String.class), "consulta del funcionario");
		return parseProfile(body);
	}

	/**
	 * Runs the upstream call behind {@code sisa.llave-mx.timeout}.
	 *
	 * <p>The deadline is enforced here rather than on the request factory
	 * because the injected {@code RestClient.Builder} is Boot's, whose factory
	 * carries the deployment's SSL and proxy settings that must not be swapped
	 * out. A provider that never answers would otherwise hold a login open until
	 * the container's own read timeout — minutes of a spinning browser for
	 * something the applicant cannot do anything about.
	 */
	private String call(Supplier<String> exchange, String what) {
		CompletableFuture<String> pending;
		try {
			pending = CompletableFuture.supplyAsync(exchange);
		} catch (RejectedExecutionException e) {
			throw new LlaveMxIntegrationException("LlaveMX no esta disponible en este momento.");
		}
		try {
			return pending.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			pending.cancel(true);
			log.warn("LlaveMX {} excedio el tiempo de espera de {}", what, properties.timeout());
			throw new LlaveMxIntegrationException("LlaveMX no respondio a tiempo. Intenta de nuevo.");
		} catch (InterruptedException e) {
			pending.cancel(true);
			Thread.currentThread().interrupt();
			throw new LlaveMxIntegrationException("No se pudo completar la operacion. Intenta de nuevo.");
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RestClientResponseException response) {
				// Status only: the body can echo the client id or endpoint paths.
				log.warn("LlaveMX {} rechazado con status {}", what, response.getStatusCode().value());
				throw new LlaveMxIntegrationException("LlaveMX rechazo la operacion de " + what + ".");
			}
			log.warn("LlaveMX {} fallo: {}", what, cause == null ? "null" : cause.getClass().getSimpleName());
			throw new LlaveMxIntegrationException("LlaveMX no esta disponible en este momento.");
		}
	}

	@SuppressWarnings("unchecked")
	private LlaveMxProfile parseProfile(String body) {
		if (body == null || body.isBlank()) {
			throw new LlaveMxIntegrationException("LlaveMX devolvio una respuesta vacia.");
		}
		Map<String, Object> payload;
		try {
			payload = objectMapper.readValue(body, Map.class);
		} catch (Exception e) {
			throw new LlaveMxIntegrationException("No se pudo interpretar la respuesta de LlaveMX.");
		}
		// Documented shape (GET /api/v1/llave/digital/entity):
		//   { "data": { "person": { "curp", "name", "first_surname",
		//       "second_surname", "contact": { "email" } }, "scopes": [...] } }
		// The `entity` alternative is kept because some deployments wrap the same
		// payload differently, and the flat read is the last resort for a root
		// object that needs no unwrapping at all.
		Map<String, Object> container = containerOf(payload);
		Map<String, Object> person = mapOf(container, "person");
		if (person == null) {
			person = container;
		}

		String subject = subjectOf(person);
		if (subject == null) {
			// The keys, never the values: a CURP or a name in a log is a person in
			// a log. This is the one failure that cannot be diagnosed from copy.
			log.warn("LlaveMX entity no trajo un identificador; claves presentes: {}", person.keySet());
			throw new LlaveMxIntegrationException("LlaveMX no devolvio el identificador del funcionario.");
		}
		Map<String, Object> contact = mapOf(person, "contact");
		return new LlaveMxProfile(subject,
				firstNonBlank(text(contact == null ? null : contact, "email"), text(person, "email")),
				fullNameOf(person),
				firstNonBlank(text(person, "curp"), text(person, "CURP")),
				rolesOf(person),
				prefillOf(person),
				payload);
	}

	/**
	 * The subset the registration wizard locks and pre-fills (RF-AUTH-003).
	 *
	 * <p>Read straight out of the documented {@code person} shape and normalized
	 * into what SISA already stores, so nothing provider-specific ever reaches
	 * the form. Every value may come back blank: which of them the provider
	 * actually returns is not contractual, and a blank field is deliberately
	 * left for the applicant to type rather than rendered as an empty locked box
	 * they cannot edit.
	 */
	private static LlaveMxPrefill prefillOf(Map<String, Object> person) {
		Map<String, Object> contact = mapOf(person, "contact");
		Map<String, Object> origin = mapOf(person, "origin");
		return new LlaveMxPrefill(
				text(person, "name"),
				text(person, "first_surname"),
				text(person, "second_surname"),
				birthDateOf(text(person, "date_of_birth")),
				sexOf(person),
				text(origin, "state"),
				text(origin, "city"),
				firstNonBlank(text(contact, "email"), text(person, "email")),
				firstNonBlank(text(contact, "phone_number"), text(contact, "phone"), text(person, "phone")));
	}

	/**
	 * {@code date_of_birth} is documented without a format, so only the two
	 * shapes a date API realistically returns are converted — an ISO instant
	 * ({@code 1999-02-14T00:00:00}) and an ISO date — into the
	 * {@code dd/MM/yyyy} the ficha DTO and the front's DatePicker both speak.
	 *
	 * <p>Anything else becomes blank on purpose. A wrong date is far worse than
	 * an empty one: the applicant would submit a birth date they never typed,
	 * with no way to notice, and CURP/date mismatches are exactly what admission
	 * checks later.
	 */
	private static String birthDateOf(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String value = raw.trim();
		if (value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
			return value.substring(8, 10) + "/" + value.substring(5, 7) + "/" + value.substring(0, 4);
		}
		return value.matches("\\d{2}/\\d{2}/\\d{4}") ? value : "";
	}

	/**
	 * The provider types sex as {@code gender: { id, name }} and the name is
	 * free text, so it is matched loosely against both Spanish spellings and the
	 * single-letter codes. A value that fits neither is dropped rather than
	 * guessed — the field has exactly two options, and picking the wrong one
	 * silently corrupts an identity record.
	 */
	private static String sexOf(Map<String, Object> person) {
		Object gender = person.get("gender");
		String raw = gender instanceof Map<?, ?> map
				? firstNonBlank(text(castMap(map), "name"), text(castMap(map), "value"), text(castMap(map), "code"))
				: gender == null ? null : String.valueOf(gender);
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
		if (normalized.contains("fem") || normalized.contains("mujer") || normalized.equals("f")) {
			return "Femenino";
		}
		if (normalized.contains("masc") || normalized.contains("hombre") || normalized.equals("m")) {
			return "Masculino";
		}
		return "";
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castMap(Map<?, ?> map) {
		return (Map<String, Object>) map;
	}

	/**
	 * The identity document never exposes an OAuth {@code sub}. The stable
	 * identifier has to be derived from what it does carry, and the field that
	 * carries it is the one unconfirmed part of the contract — hence configurable
	 * rather than hardcoded, so a wrong guess is a config change and not a
	 * redeploy.
	 *
	 * <p>CURP is the default because it is what SISA identifies fichas by, which
	 * lets the account-linking message quote the value the administrator has to
	 * write into {@code llave_mx_subject}. {@code key_mx_id} covers the person
	 * without one.
	 */
	private String subjectOf(Map<String, Object> person) {
		return firstNonBlank(text(person, properties.subjectField()), text(person, "curp"), text(person, "key_mx_id"));
	}

	private Map<String, Object> containerOf(Map<String, Object> payload) {
		Map<String, Object> source = payload;
		for (String wrapper : List.of("data", "entity")) {
			Map<String, Object> nested = mapOf(payload, wrapper);
			if (nested != null && !nested.isEmpty()) {
				return nested;
			}
		}
		return source;
	}

	private static Map<String, Object> mapOf(Map<String, Object> source, String key) {
		if (source == null) {
			return null;
		}
		Object value = source.get(key);
		return value instanceof Map<?, ?> map && !map.isEmpty() ? (Map<String, Object>) map : null;
	}

	/** {@code name first_surname second_surname}, skipping whichever are absent. */
	private static String fullNameOf(Map<String, Object> person) {
		StringBuilder joined = new StringBuilder();
		for (String key : List.of("name", "first_surname", "second_surname")) {
			String value = text(person, key);
			if (value == null || value.isBlank()) {
				continue;
			}
			if (joined.length() > 0) {
				joined.append(' ');
			}
			joined.append(value);
		}
		return joined.length() > 0 ? joined.toString()
				: firstNonBlank(text(person, "fullName"), text(person, "nombre"));
	}

	private List<String> rolesOf(Map<String, Object> source) {
		for (String key : List.of("roles", "rolesLlave", "puestos")) {
			Object value = source.get(key);
			if (value instanceof List<?> list && !list.isEmpty()) {
				return list.stream().map(String::valueOf).toList();
			}
			if (value instanceof String text && !text.isBlank()) {
				return Arrays.stream(text.split("[,\\s]+")).filter(s -> !s.isBlank()).toList();
			}
		}
		return List.of();
	}

	private LlaveMxTokenSet postForm(String url, MultiValueMap<String, String> form, String what) {
		String body = call(() -> restClient.post()
				.uri(url)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(String.class), what);
		return parseTokenSet(body, what);
	}

	private LlaveMxTokenSet parseTokenSet(String body, String what) {
		if (body == null || body.isBlank()) {
			throw new LlaveMxIntegrationException("LlaveMX devolvio una respuesta vacia en " + what + ".");
		}
		Map<String, Object> payload;
		try {
			payload = objectMapper.readValue(body, Map.class);
		} catch (Exception e) {
			throw new LlaveMxIntegrationException("No se pudo interpretar la respuesta de LlaveMX.");
		}
		Object accessToken = payload.get("access_token");
		if (accessToken == null || String.valueOf(accessToken).isBlank()) {
			throw new LlaveMxIntegrationException("LlaveMX no devolvio un access token.");
		}
		Object expiresIn = payload.get("expires_in");
		return new LlaveMxTokenSet(String.valueOf(accessToken), text(payload, "refresh_token"),
				firstNonBlank(text(payload, "token_type"), "Bearer"),
				expiresIn instanceof Number number ? number.longValue() : 3600L);
	}

	private void requireConfigured() {
		if (!properties.isConfigured()) {
			throw new LlaveMxIntegrationException(
					"La integracion con LlaveMX no esta configurada (faltan LLAVE_CLIENT_ID, LLAVE_CLIENT_SECRET, LLAVE_REDIRECT_URI o LLAVE_ENTITY_URL).");
		}
	}

	private static String text(Map<String, Object> source, String key) {
		Object value = source.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}
}
