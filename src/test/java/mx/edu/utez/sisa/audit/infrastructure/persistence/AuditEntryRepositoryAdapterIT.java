package mx.edu.utez.sisa.audit.infrastructure.persistence;

import mx.edu.utez.sisa.audit.domain.model.AuditEntry;
import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase.AuditFilter;
import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase.AuditPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La consulta del log contra MySQL real: cada filtro es opcional y {@code to} es
 * exclusivo.
 *
 * <p>El esquema lo reconstruyen las migraciones de Flyway al arrancar el contexto,
 * sobre la base local desechable que exige {@code IntegrationTestDatabaseGuard}.
 */
@DataJpaTest
@Import(AuditEntryRepositoryAdapter.class)
class AuditEntryRepositoryAdapterIT {

	private static final Instant DAY1 = Instant.parse("2026-11-01T12:00:00Z");

	private static final Instant DAY2 = Instant.parse("2026-11-02T12:00:00Z");

	private static final UUID ANA = UUID.randomUUID();

	private static final UUID LUIS = UUID.randomUUID();

	private static final UUID CANDIDATE = UUID.randomUUID();

	@Autowired
	private AuditEntryRepositoryAdapter adapter;

	@Test
	void everyFilterIsOptionalAndCombines() {
		adapter.saveAll(List.of(
				new AuditEntry(DAY1, ANA, "Ana", "CANDIDATE_FICHA_EDITED", "CANDIDATE", CANDIDATE, "curp", "A", "B"),
				new AuditEntry(DAY2, LUIS, "Luis", "CANDIDATE_FICHA_EDITED", "CANDIDATE", CANDIDATE, "firstName", "J",
						"Juan"),
				new AuditEntry(DAY2, ANA, "Ana", "USER_ROLE_ASSIGNED", "USER", UUID.randomUUID(), null, null, null)));

		assertThat(adapter.search(filter(null, null, null, null, null), 0, 50).total()).isEqualTo(3);
		assertThat(adapter.search(filter(null, null, ANA, null, null), 0, 50).total()).isEqualTo(2);
		assertThat(adapter.search(filter(null, null, null, "CANDIDATE", CANDIDATE), 0, 50).items())
				.extracting(AuditEntry::getField).containsExactly("firstName", "curp");
		assertThat(adapter.search(filter(DAY1, DAY2, null, null, null), 0, 50).items())
				.extracting(AuditEntry::getField).containsExactly("curp");
	}

	@Test
	void pagesNewestFirst() {
		adapter.saveAll(List.of(new AuditEntry(DAY1, ANA, "Ana", "A", "E", CANDIDATE, "a", null, "1"),
				new AuditEntry(DAY2, ANA, "Ana", "A", "E", CANDIDATE, "b", null, "2")));

		AuditPage first = adapter.search(filter(null, null, null, null, null), 0, 1);

		assertThat(first.total()).isEqualTo(2);
		assertThat(first.items()).extracting(AuditEntry::getField).containsExactly("b");
	}

	private static AuditFilter filter(Instant from, Instant to, UUID actor, String entityType, UUID entityId) {
		return new AuditFilter(from, to, actor, null, entityType, entityId);
	}
}
