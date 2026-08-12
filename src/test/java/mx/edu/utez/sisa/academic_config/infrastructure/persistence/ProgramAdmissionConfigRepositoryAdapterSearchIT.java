package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository.ProgramAdmissionConfigSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository.ProgramAdmissionConfigSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for
 * {@link ProgramAdmissionConfigRepositoryAdapter#search}, mirroring
 * {@code GenerationRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(ProgramAdmissionConfigRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ProgramAdmissionConfigRepositoryAdapterSearchIT {

	private static final Instant OPENS_AT = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant CLOSES_AT = Instant.parse("2026-03-01T00:00:00Z");

	@Autowired
	private ProgramAdmissionConfigRepositoryAdapter adapter;

	@Autowired
	private ProgramAdmissionConfigJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		jpaRepository.save(newConfig(UUID.randomUUID(), UUID.randomUUID()));
		ProgramAdmissionConfig closed = jpaRepository.save(newConfig(UUID.randomUUID(), UUID.randomUUID()));
		closed.close();
		jpaRepository.save(closed);

		ProgramAdmissionConfigSearchPage openPage = adapter
				.search(new ProgramAdmissionConfigSearchCriteria(ProgramAdmissionConfigStatus.OPEN, null, 0, 20));
		ProgramAdmissionConfigSearchPage closedPage = adapter
				.search(new ProgramAdmissionConfigSearchCriteria(ProgramAdmissionConfigStatus.CLOSED, null, 0, 20));

		assertThat(openPage.totalElements()).isEqualTo(1L);
		assertThat(closedPage.totalElements()).isEqualTo(1L);
	}

	@Test
	void programIdFilterMatchesOnlyConfigsOfThatProgram() {
		UUID programA = UUID.randomUUID();
		UUID programB = UUID.randomUUID();
		jpaRepository.save(newConfig(programA, UUID.randomUUID()));
		jpaRepository.save(newConfig(programA, UUID.randomUUID()));
		jpaRepository.save(newConfig(programB, UUID.randomUUID()));

		ProgramAdmissionConfigSearchPage programAPage = adapter
				.search(new ProgramAdmissionConfigSearchCriteria(null, programA, 0, 20));
		ProgramAdmissionConfigSearchPage programBPage = adapter
				.search(new ProgramAdmissionConfigSearchCriteria(null, programB, 0, 20));

		assertThat(programAPage.totalElements()).isEqualTo(2L);
		assertThat(programBPage.totalElements()).isEqualTo(1L);
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		for (int i = 0; i < 5; i++) {
			jpaRepository.save(newConfig(UUID.randomUUID(), UUID.randomUUID()));
		}

		ProgramAdmissionConfigSearchPage firstPage = adapter
				.search(new ProgramAdmissionConfigSearchCriteria(null, null, 0, 2));
		ProgramAdmissionConfigSearchPage secondPage = adapter
				.search(new ProgramAdmissionConfigSearchCriteria(null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void saveInsertsANewRow() {
		ProgramAdmissionConfig saved = adapter.save(newConfig(UUID.randomUUID(), UUID.randomUUID()));

		assertThat(saved.getId()).isNotNull();
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findByProgramIdAndPeriodIdReturnsMatch() {
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		jpaRepository.save(newConfig(programId, periodId));

		assertThat(adapter.findByProgramIdAndPeriodId(programId, periodId)).isPresent();
		assertThat(adapter.findByProgramIdAndPeriodId(programId, UUID.randomUUID())).isEmpty();
		assertThat(adapter.findByProgramIdAndPeriodId(UUID.randomUUID(), periodId)).isEmpty();
	}

	@Test
	void findByIdReturnsTheConfigWhenItExists() {
		ProgramAdmissionConfig saved = jpaRepository.save(newConfig(UUID.randomUUID(), UUID.randomUUID()));

		assertThat(adapter.findById(saved.getId())).isPresent().get()
				.extracting(ProgramAdmissionConfig::getMaxCandidates).isEqualTo(50);
	}

	@Test
	void findByIdReturnsEmptyWhenConfigDoesNotExist() {
		assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
	}

	private static ProgramAdmissionConfig newConfig(UUID programId, UUID periodId) {
		return new ProgramAdmissionConfig(programId, periodId, UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT);
	}
}
