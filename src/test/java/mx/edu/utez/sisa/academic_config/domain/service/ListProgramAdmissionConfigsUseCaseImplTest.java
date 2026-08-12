package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase.ListProgramAdmissionConfigsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListProgramAdmissionConfigsUseCase.ListProgramAdmissionConfigsResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository.ProgramAdmissionConfigSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository.ProgramAdmissionConfigSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListProgramAdmissionConfigsUseCaseImplTest {

	private static final Instant OPENS_AT = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant CLOSES_AT = Instant.parse("2026-03-01T00:00:00Z");

	@Mock
	private ProgramAdmissionConfigRepository configRepository;

	private ListProgramAdmissionConfigsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListProgramAdmissionConfigsUseCaseImpl(configRepository);
	}

	@Test
	void listProgramAdmissionConfigs_mapsPageContentToSummaries() {
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT);
		when(configRepository.search(new ProgramAdmissionConfigSearchCriteria(null, null, 0, 20)))
				.thenReturn(new ProgramAdmissionConfigSearchPage(List.of(config), 1L, 1));

		ListProgramAdmissionConfigsResult result = useCase
				.listProgramAdmissionConfigs(new ListProgramAdmissionConfigsQuery(null, null, 0, 20));

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).maxCandidates()).isEqualTo(50);
		assertThat(result.totalElements()).isEqualTo(1L);
	}

	@Test
	void listProgramAdmissionConfigs_passesStatusAndProgramIdFiltersThrough() {
		UUID programId = UUID.randomUUID();
		when(configRepository.search(new ProgramAdmissionConfigSearchCriteria(ProgramAdmissionConfigStatus.OPEN,
				programId, 0, 20))).thenReturn(new ProgramAdmissionConfigSearchPage(List.of(), 0L, 0));

		useCase.listProgramAdmissionConfigs(
				new ListProgramAdmissionConfigsQuery(ProgramAdmissionConfigStatus.OPEN, programId, 0, 20));

		// Verified via the stub above matching exact criteria — Mockito throws
		// an UnnecessaryStubbingException at verification time if the call
		// shape ever drifts from what listProgramAdmissionConfigs actually
		// passes through.
	}

	@Test
	void listProgramAdmissionConfigs_negativePageNormalizesToZero() {
		when(configRepository.search(new ProgramAdmissionConfigSearchCriteria(null, null, 0, 20)))
				.thenReturn(new ProgramAdmissionConfigSearchPage(List.of(), 0L, 0));

		ListProgramAdmissionConfigsResult result = useCase
				.listProgramAdmissionConfigs(new ListProgramAdmissionConfigsQuery(null, null, -5, 20));

		assertThat(result.page()).isZero();
	}

	@Test
	void listProgramAdmissionConfigs_nonPositiveSizeFallsBackToDefault() {
		when(configRepository.search(new ProgramAdmissionConfigSearchCriteria(null, null, 0, 20)))
				.thenReturn(new ProgramAdmissionConfigSearchPage(List.of(), 0L, 0));

		ListProgramAdmissionConfigsResult result = useCase
				.listProgramAdmissionConfigs(new ListProgramAdmissionConfigsQuery(null, null, 0, 0));

		assertThat(result.size()).isEqualTo(20);
	}

	@Test
	void listProgramAdmissionConfigs_oversizedSizeIsCappedAtMax() {
		when(configRepository.search(new ProgramAdmissionConfigSearchCriteria(null, null, 0, 100)))
				.thenReturn(new ProgramAdmissionConfigSearchPage(List.of(), 0L, 0));

		ListProgramAdmissionConfigsResult result = useCase
				.listProgramAdmissionConfigs(new ListProgramAdmissionConfigsQuery(null, null, 0, 500));

		assertThat(result.size()).isEqualTo(100);
	}
}
