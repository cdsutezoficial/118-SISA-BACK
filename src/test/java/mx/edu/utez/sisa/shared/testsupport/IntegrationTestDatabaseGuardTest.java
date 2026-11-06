package mx.edu.utez.sisa.shared.testsupport;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationTestDatabaseGuardTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"jdbc:mysql://localhost:3306/sisa_it?createDatabaseIfNotExist=true",
			"jdbc:mysql://127.0.0.1/sisa_it",
			"jdbc:mysql://localhost:3306/sisa_seed_test?useSSL=false" })
	void acceptsDisposableLocalDatabases(String url) {
		assertThatCode(() -> IntegrationTestDatabaseGuard.check(url)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"jdbc:mysql://localhost:3306/sisa",
			"jdbc:mysql://localhost:3306/sisa_dev",
			"jdbc:mysql://10.0.0.5:3306/sisa_it",
			"jdbc:mysql://db-sisa:3306/sisa_it",
			"jdbc:mysql://localhost:3306/sisa_it_backup",
			"jdbc:postgresql://localhost:5432/sisa_it" })
	void rejectsAnythingElse(String url) {
		assertThatThrownBy(() -> IntegrationTestDatabaseGuard.check(url))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SISA_IT_DB_URL");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"jdbc:mysql://localhost:3306/sisa_it?createDatabaseIfNotExist=true|jdbc:mysql://localhost:3306/sisa_jpa_it?createDatabaseIfNotExist=true",
			"jdbc:mysql://localhost/sisa_it|jdbc:mysql://localhost/sisa_jpa_it",
			"jdbc:mysql://localhost:3306/sisa_seed_test?useSSL=false|jdbc:mysql://localhost:3306/sisa_seed_jpa_test?useSSL=false" })
	void jpaSlicesGetASiblingDatabase(String pair) {
		String[] urls = pair.split("\\|");
		assertThat(IntegrationTestDatabaseGuard.sliceDatabase(urls[0])).isEqualTo(urls[1]);
		assertThatCode(() -> IntegrationTestDatabaseGuard.check(urls[1])).doesNotThrowAnyException();
	}

	@org.junit.jupiter.api.Test
	void rejectsMissingUrl() {
		assertThatThrownBy(() -> IntegrationTestDatabaseGuard.check(null)).isInstanceOf(IllegalStateException.class);
	}
}
