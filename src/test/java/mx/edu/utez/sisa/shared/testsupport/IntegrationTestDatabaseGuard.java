package mx.edu.utez.sisa.shared.testsupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Points every test context at a disposable local database and refuses to
 * start against anything else.
 *
 * <p>Each test run rebuilds the schema ({@link
 * FlywayCleanMigrateTestConfiguration}: Flyway {@code clean} + {@code migrate}),
 * so a test run against the shared development database or QA would wipe it.
 * The developer's {@code .env} {@code DB_URL} usually names the shared
 * database, so tests ignore it and read {@code SISA_IT_DB_URL} instead
 * (default {@value #DEFAULT_URL}). A test that sets
 * {@code spring.datasource.url} inline (the seed ITs use their own schema)
 * still wins, and is checked like any other.
 *
 * <p>{@code @DataJpaTest} slices get a sibling database ({@code sisa_jpa_it})
 * rebuilt on every context: their repository searches assert exact counts on
 * an empty schema, while full contexts share {@code sisa_it} and need the
 * seeded catalogs to survive across cached contexts.
 *
 * <p>Allowed: host {@code localhost}/{@code 127.0.0.1} and a database name
 * ending in {@code _it} or {@code _test}. Registered only in
 * {@code src/test/resources/META-INF/spring.factories}, so production code
 * never loads it.
 */
public class IntegrationTestDatabaseGuard implements EnvironmentPostProcessor {

	static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/sisa_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

	private static final Pattern MYSQL_URL = Pattern.compile("^jdbc:mysql://([^/:?]+)(?::\\d+)?/([^?]+).*$");

	private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");

	/** Inlined by Spring Boot into every {@code @DataJpaTest} context. */
	private static final String DATA_JPA_SLICE_MARKER = "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTestContextBootstrapper";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String url = environment.getProperty("SISA_IT_DB_URL", DEFAULT_URL);
		boolean jpaSlice = environment.containsProperty(DATA_JPA_SLICE_MARKER);
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("DB_URL", jpaSlice ? sliceDatabase(url) : url);
		overrides.put(FlywayCleanMigrateTestConfiguration.CLEAN_EACH_CONTEXT, String.valueOf(jpaSlice));
		putIfPresent(environment, overrides, "SISA_IT_DB_USERNAME", "DB_USERNAME");
		putIfPresent(environment, overrides, "SISA_IT_DB_PASSWORD", "DB_PASSWORD");
		overrides.put("spring.flyway.clean-disabled", "false");
		// Spring keeps up to 32 test contexts cached, each with its own pool;
		// at Hikari's default of 10 they exceed MySQL's 151 connections.
		overrides.put("spring.datasource.hikari.maximum-pool-size", "4");
		overrides.put("spring.datasource.hikari.minimum-idle", "1");
		environment.getPropertySources().addFirst(new MapPropertySource("integrationTestDatabase", overrides));

		check(environment.getProperty("spring.datasource.url"));
	}

	static void check(String url) {
		Matcher matcher = url == null ? null : MYSQL_URL.matcher(url);
		boolean allowed = matcher != null && matcher.matches()
				&& LOCAL_HOSTS.contains(matcher.group(1))
				&& (matcher.group(2).endsWith("_it") || matcher.group(2).endsWith("_test"));
		if (!allowed) {
			throw new IllegalStateException("Las pruebas borran y recrean la base al arrancar, así que solo corren "
					+ "contra una base local desechable (host localhost/127.0.0.1 y nombre terminado en _it o _test). "
					+ "Se recibió: " + url + ". Define SISA_IT_DB_URL, por ejemplo " + DEFAULT_URL
					+ " — ver docs/guias/migraciones.md.");
		}
	}

	/** {@code …/sisa_it?…} → {@code …/sisa_jpa_it?…}; same for a {@code _test} suffix. */
	static String sliceDatabase(String url) {
		return url.replaceFirst("(/[^/?]+?)_(it|test)(\\?|$)", "$1_jpa_$2$3");
	}

	private static void putIfPresent(ConfigurableEnvironment environment, Map<String, Object> overrides,
			String source, String target) {
		String value = environment.getProperty(source);
		if (value != null) {
			overrides.put(target, value);
		}
	}
}
