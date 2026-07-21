package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase.ListGenerationsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListGenerationsUseCase.ListGenerationsResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository.GenerationSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository.GenerationSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListGenerationsUseCaseImplTest {

	@Mock
	private GenerationRepository generationRepository;

	private ListGenerationsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListGenerationsUseCaseImpl(generationRepository);
	}

	@Test
	void listGenerations_mapsPageContentToSummaries() {
		Generation generation = new Generation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 7, 2026);
		when(generationRepository.search(new GenerationSearchCriteria(null, null, null, 0, 20)))
				.thenReturn(new GenerationSearchPage(List.of(generation), 1L, 1));

		ListGenerationsResult result = useCase.listGenerations(new ListGenerationsQuery(null, null, null, 0, 20));

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).code()).isEqualTo("2026-7");
		assertThat(result.totalElements()).isEqualTo(1L);
	}

	@Test
	void listGenerations_passesProgramIdFilterThrough() {
		UUID programId = UUID.randomUUID();
		when(generationRepository.search(new GenerationSearchCriteria(GenerationStatus.ACTIVE, "2026", programId, 0, 20)))
				.thenReturn(new GenerationSearchPage(List.of(), 0L, 0));

		useCase.listGenerations(new ListGenerationsQuery(GenerationStatus.ACTIVE, "2026", programId, 0, 20));

		// Verified via the stub above matching exact criteria — Mockito throws
		// an UnnecessaryStubbingException at verification time if the call
		// shape ever drifts from what listGenerations actually passes through.
	}

	@Test
	void listGenerations_negativePageNormalizesToZero() {
		when(generationRepository.search(new GenerationSearchCriteria(null, null, null, 0, 20)))
				.thenReturn(new GenerationSearchPage(List.of(), 0L, 0));

		ListGenerationsResult result = useCase.listGenerations(new ListGenerationsQuery(null, null, null, -5, 20));

		assertThat(result.page()).isZero();
	}

	@Test
	void listGenerations_nonPositiveSizeFallsBackToDefault() {
		when(generationRepository.search(new GenerationSearchCriteria(null, null, null, 0, 20)))
				.thenReturn(new GenerationSearchPage(List.of(), 0L, 0));

		ListGenerationsResult result = useCase.listGenerations(new ListGenerationsQuery(null, null, null, 0, 0));

		assertThat(result.size()).isEqualTo(20);
	}

	@Test
	void listGenerations_oversizedSizeIsCappedAtMax() {
		when(generationRepository.search(new GenerationSearchCriteria(null, null, null, 0, 100)))
				.thenReturn(new GenerationSearchPage(List.of(), 0L, 0));

		ListGenerationsResult result = useCase.listGenerations(new ListGenerationsQuery(null, null, null, 0, 500));

		assertThat(result.size()).isEqualTo(100);
	}
}
