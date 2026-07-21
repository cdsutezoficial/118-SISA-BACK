package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository.GenerationSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository.GenerationSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Real-DB (H2) coverage for {@link GenerationRepositoryAdapter#search},
 * mirroring {@code AcademicPeriodRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(GenerationRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GenerationRepositoryAdapterSearchIT {

	@Autowired
	private GenerationRepositoryAdapter adapter;

	@Autowired
	private GenerationJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newGeneration(programId, 1, 2026));
		Generation finished = jpaRepository.save(newGeneration(programId, 2, 2026));
		finished.finish();
		jpaRepository.save(finished);

		GenerationSearchPage activePage = adapter.search(new GenerationSearchCriteria(GenerationStatus.ACTIVE, null, null, 0, 20));
		GenerationSearchPage finishedPage = adapter
				.search(new GenerationSearchCriteria(GenerationStatus.FINISHED, null, null, 0, 20));

		assertThat(activePage.totalElements()).isEqualTo(1L);
		assertThat(finishedPage.totalElements()).isEqualTo(1L);
	}

	@Test
	void searchMatchesCodeCaseInsensitively() {
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newGeneration(programId, 1, 2026));
		jpaRepository.save(newGeneration(programId, 2, 2027));

		GenerationSearchPage byFragment = adapter.search(new GenerationSearchCriteria(null, "2026", null, 0, 20));

		assertThat(byFragment.totalElements()).isEqualTo(1L);
		assertThat(byFragment.content().get(0).getCode()).isEqualTo("2026-1");
	}

	@Test
	void programIdFilterMatchesOnlyGenerationsOfThatProgram() {
		UUID programA = UUID.randomUUID();
		UUID programB = UUID.randomUUID();
		jpaRepository.save(newGeneration(programA, 1, 2026));
		jpaRepository.save(newGeneration(programA, 2, 2026));
		jpaRepository.save(newGeneration(programB, 1, 2026));

		GenerationSearchPage programAPage = adapter.search(new GenerationSearchCriteria(null, null, programA, 0, 20));
		GenerationSearchPage programBPage = adapter.search(new GenerationSearchCriteria(null, null, programB, 0, 20));

		assertThat(programAPage.totalElements()).isEqualTo(2L);
		assertThat(programBPage.totalElements()).isEqualTo(1L);
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		UUID programId = UUID.randomUUID();
		for (int i = 1; i <= 5; i++) {
			jpaRepository.save(newGeneration(programId, i, 2026));
		}

		GenerationSearchPage firstPage = adapter.search(new GenerationSearchCriteria(null, null, null, 0, 2));
		GenerationSearchPage secondPage = adapter.search(new GenerationSearchCriteria(null, null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void resultsAreSortedByCodeAscending() {
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newGeneration(programId, 2, 2027));
		jpaRepository.save(newGeneration(programId, 1, 2026));

		GenerationSearchPage page = adapter.search(new GenerationSearchCriteria(null, null, null, 0, 20));

		assertThat(page.content()).extracting(Generation::getCode).containsExactly("2026-1", "2027-2");
	}

	@Test
	void saveInsertsANewRow() {
		Generation saved = adapter.save(newGeneration(UUID.randomUUID(), 1, 2026));

		assertThat(saved.getId()).isNotNull();
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findByProgramIdAndNumberReturnsMatch() {
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newGeneration(programId, 7, 2026));

		assertThat(adapter.findByProgramIdAndNumber(programId, 7)).isPresent();
		assertThat(adapter.findByProgramIdAndNumber(programId, 8)).isEmpty();
		assertThat(adapter.findByProgramIdAndNumber(UUID.randomUUID(), 7)).isEmpty();
	}

	@Test
	void findByIdReturnsTheGenerationWhenItExists() {
		Generation saved = jpaRepository.save(newGeneration(UUID.randomUUID(), 1, 2026));

		assertThat(adapter.findById(saved.getId())).isPresent().get().extracting(Generation::getCode)
				.isEqualTo("2026-1");
	}

	@Test
	void findByIdReturnsEmptyWhenGenerationDoesNotExist() {
		assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
	}

	private static Generation newGeneration(UUID programId, int number, int startPeriodYear) {
		return new Generation(UUID.randomUUID(), UUID.randomUUID(), programId, number, startPeriodYear);
	}
}
