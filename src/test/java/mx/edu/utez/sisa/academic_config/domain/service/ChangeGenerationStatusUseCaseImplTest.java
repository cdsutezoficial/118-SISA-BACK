package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeGenerationStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeGenerationStatusUseCaseImplTest {

	@Mock
	private GenerationRepository generationRepository;

	private ChangeGenerationStatusUseCaseImpl useCase;

	private UUID generationId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeGenerationStatusUseCaseImpl(generationRepository);
		generationId = UUID.randomUUID();
	}

	@Test
	void changeStatus_toFinishedSucceeds() {
		Generation generation = new Generation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 7, 2026);
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(generation));
		when(generationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GenerationResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), generationId, GenerationStatus.FINISHED));

		assertThat(result.status()).isEqualTo(GenerationStatus.FINISHED);
	}

	@Test
	void changeStatus_backToActiveSucceeds() {
		// Both directions are always valid — unlike AcademicPeriod's strict
		// sequence, there is no "terminal" state here.
		Generation generation = new Generation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 7, 2026);
		generation.finish();
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(generation));
		when(generationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GenerationResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), generationId, GenerationStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(GenerationStatus.ACTIVE);
	}

	@Test
	void changeStatus_throwsWhenGenerationNotFound() {
		when(generationRepository.findById(generationId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), generationId, GenerationStatus.FINISHED)))
				.isInstanceOf(GenerationNotFoundException.class);
	}
}
