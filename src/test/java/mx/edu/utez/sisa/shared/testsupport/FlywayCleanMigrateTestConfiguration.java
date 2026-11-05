package mx.edu.utez.sisa.shared.testsupport;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rebuilds each test database from the migrations once per test run — the
 * replacement for {@code ddl-auto=create-drop}, but exercising the same
 * {@code db/migration} scripts production runs, so a broken migration fails
 * the build.
 *
 * <p>Full contexts clean only the first time a given database is opened in
 * this JVM; later ones just {@code migrate} (a no-op). Spring caches test
 * contexts and reuses them across classes, so cleaning on every new context
 * would wipe the data seeded by contexts still in the cache.
 * {@code @DataJpaTest} slices have their own database and clean on every
 * context, as {@code create-drop} did.
 *
 * <p>Registered as a test-only auto-configuration for full contexts
 * ({@code AutoConfiguration.imports}) and for {@code @DataJpaTest} slices
 * ({@code AutoConfigureDataJpa.imports}). {@code clean} is enabled only in
 * tests by {@link IntegrationTestDatabaseGuard}, which also guarantees the
 * database is a disposable local one.
 */
@AutoConfiguration(before = FlywayAutoConfiguration.class)
public class FlywayCleanMigrateTestConfiguration {

	/** Set by {@link IntegrationTestDatabaseGuard}: {@code true} for {@code @DataJpaTest} slices. */
	static final String CLEAN_EACH_CONTEXT = "sisa.it.clean-each-context";

	private static final Set<String> CLEANED_DATABASES = ConcurrentHashMap.newKeySet();

	@Bean
	FlywayMigrationStrategy cleanThenMigrate(@Value("${" + CLEAN_EACH_CONTEXT + ":false}") boolean cleanEachContext) {
		return (Flyway flyway) -> {
			if (cleanEachContext || CLEANED_DATABASES.add(databaseUrl(flyway))) {
				flyway.clean();
			}
			flyway.migrate();
		};
	}

	private static String databaseUrl(Flyway flyway) {
		try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
			return connection.getMetaData().getURL();
		}
		catch (SQLException e) {
			throw new IllegalStateException("No se pudo leer la URL de la base de pruebas", e);
		}
	}
}
