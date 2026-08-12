package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.academic_config.domain.model.SelectionStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeProgramAdmissionConfigStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramAdmissionConfigNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeProgramAdmissionConfigStatusUseCaseImplTest {

	private static final Instant OPENS_AT = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant CLOSES_AT = Instant.parse("2026-03-01T00:00:00Z");

	@Mock
	private ProgramAdmissionConfigRepository configRepository;

	private ChangeProgramAdmissionConfigStatusUseCaseImpl useCase;

	private UUID configId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeProgramAdmissionConfigStatusUseCaseImpl(configRepository);
		configId = UUID.randomUUID();
	}

	@Test
	void changeStatus_toClosedSucceeds() {
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT);
		when(configRepository.findById(configId)).thenReturn(Optional.of(config));
		when(configRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ProgramAdmissionConfigResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), configId, ProgramAdmissionConfigStatus.CLOSED));

		assertThat(result.status()).isEqualTo(ProgramAdmissionConfigStatus.CLOSED);
	}

	@Test
	void changeStatus_backToOpenSucceeds() {
		// Both directions are always valid — unlike AcademicPeriod's strict
		// sequence, there is no "terminal" state here.
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT);
		config.close();
		when(configRepository.findById(configId)).thenReturn(Optional.of(config));
		when(configRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ProgramAdmissionConfigResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), configId, ProgramAdmissionConfigStatus.OPEN));

		assertThat(result.status()).isEqualTo(ProgramAdmissionConfigStatus.OPEN);
	}

	@Test
	void changeStatus_neverTouchesSelectionStatus() {
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), true, 50, OPENS_AT, CLOSES_AT);
		when(configRepository.findById(configId)).thenReturn(Optional.of(config));
		when(configRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ProgramAdmissionConfigResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), configId, ProgramAdmissionConfigStatus.CLOSED));

		assertThat(result.selectionStatus()).isEqualTo(SelectionStatus.IN_REVIEW);
	}

	@Test
	void changeStatus_throwsWhenConfigNotFound() {
		when(configRepository.findById(configId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), configId, ProgramAdmissionConfigStatus.CLOSED)))
				.isInstanceOf(ProgramAdmissionConfigNotFoundException.class);
	}
}
