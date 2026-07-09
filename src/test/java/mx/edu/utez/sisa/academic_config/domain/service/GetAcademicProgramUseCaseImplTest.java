package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAcademicProgramUseCaseImplTest {

	@Mock
	private AcademicProgramRepository programRepository;

	private GetAcademicProgramUseCaseImpl useCase;

	private AcademicProgram program;
	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new GetAcademicProgramUseCaseImpl(programRepository);
		program = new AcademicProgram(UUID.randomUUID(), "Ingenieria en Software", "Ingenieria en Software",
				"ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null);
		programId = UUID.randomUUID();
		ReflectionTestUtils.setField(program, "id", programId);
	}

	@Test
	void getById_returnsTheProgramWhenItExists() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(program));

		AcademicProgramResult result = useCase.getById(programId);

		assertThat(result.id()).isEqualTo(programId);
		assertThat(result.name()).isEqualTo("Ingenieria en Software");
		assertThat(result.code()).isEqualTo("ISC-01");
	}

	@Test
	void getById_rejectsUnknownProgramId() {
		UUID unknownId = UUID.randomUUID();
		when(programRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(AcademicProgramNotFoundException.class);
	}
}
