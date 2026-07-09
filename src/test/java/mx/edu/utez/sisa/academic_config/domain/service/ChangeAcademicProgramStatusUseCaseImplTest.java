package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicProgramStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicProgramNotFoundException;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeAcademicProgramStatusUseCaseImplTest {

	@Mock
	private AcademicProgramRepository programRepository;

	private ChangeAcademicProgramStatusUseCaseImpl useCase;

	private AcademicProgram program;
	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeAcademicProgramStatusUseCaseImpl(programRepository);
		program = new AcademicProgram(UUID.randomUUID(), "Ingenieria en Software", "Ingenieria en Software",
				"ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null);
		programId = UUID.randomUUID();
		ReflectionTestUtils.setField(program, "id", programId);
	}

	@Test
	void changeStatus_deactivatesAnActiveProgram() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(program));
		when(programRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicProgramResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), programId, ProgramStatus.INACTIVE));

		assertThat(result.status()).isEqualTo(ProgramStatus.INACTIVE);
	}

	@Test
	void changeStatus_reactivatesAnInactiveProgram() {
		program.deactivate();
		when(programRepository.findById(programId)).thenReturn(Optional.of(program));
		when(programRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicProgramResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), programId, ProgramStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(ProgramStatus.ACTIVE);
	}

	@Test
	void changeStatus_rejectsUnknownProgramId() {
		UUID unknownId = UUID.randomUUID();
		when(programRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), unknownId, ProgramStatus.ACTIVE)))
				.isInstanceOf(AcademicProgramNotFoundException.class);
	}
}
