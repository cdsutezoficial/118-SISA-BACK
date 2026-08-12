package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramAdmissionConfigNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProgramAdmissionConfigUseCaseImplTest {

	@Mock
	private ProgramAdmissionConfigRepository configRepository;

	private GetProgramAdmissionConfigUseCaseImpl useCase;

	private UUID configId;

	@BeforeEach
	void setUp() {
		useCase = new GetProgramAdmissionConfigUseCaseImpl(configRepository);
		configId = UUID.randomUUID();
	}

	@Test
	void getById_returnsResultWhenFound() {
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), true, 50, Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-03-01T00:00:00Z"));
		ReflectionTestUtils.setField(config, "id", configId);
		when(configRepository.findById(configId)).thenReturn(Optional.of(config));

		ProgramAdmissionConfigResult result = useCase.getById(configId);

		assertThat(result.id()).isEqualTo(configId);
		assertThat(result.maxCandidates()).isEqualTo(50);
	}

	@Test
	void getById_throwsWhenNotFound() {
		when(configRepository.findById(configId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(configId)).isInstanceOf(ProgramAdmissionConfigNotFoundException.class);
	}
}
