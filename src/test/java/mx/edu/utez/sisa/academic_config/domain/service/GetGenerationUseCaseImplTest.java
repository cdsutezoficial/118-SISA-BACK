package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.Generation;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetGenerationUseCaseImplTest {

	@Mock
	private GenerationRepository generationRepository;

	private GetGenerationUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new GetGenerationUseCaseImpl(generationRepository);
	}

	@Test
	void getById_returnsResultWhenFound() {
		UUID generationId = UUID.randomUUID();
		Generation generation = new Generation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 7, 2026);
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(generation));

		GenerationResult result = useCase.getById(generationId);

		assertThat(result.code()).isEqualTo("2026-7");
	}

	@Test
	void getById_throwsWhenNotFound() {
		UUID unknownId = UUID.randomUUID();
		when(generationRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(GenerationNotFoundException.class);
	}
}
