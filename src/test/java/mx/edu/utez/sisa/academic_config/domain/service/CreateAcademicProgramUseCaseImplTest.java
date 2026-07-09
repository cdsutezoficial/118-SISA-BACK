package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.CreateAcademicProgramCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DivisionNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateOfferNameModalityException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramCodeException;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAcademicProgramUseCaseImplTest {

	@Mock
	private AcademicProgramRepository programRepository;
	@Mock
	private AcademicDivisionRepository divisionRepository;

	private CreateAcademicProgramUseCaseImpl useCase;

	private UUID divisionId;

	@BeforeEach
	void setUp() {
		useCase = new CreateAcademicProgramUseCaseImpl(programRepository, divisionRepository);
		divisionId = UUID.randomUUID();
	}

	@Test
	void createProgram_successfulCreationWithValidData() {
		when(divisionRepository.findById(divisionId))
				.thenReturn(Optional.of(new AcademicDivision("Sistemas", "SIS", "desc", null)));
		when(programRepository.findByCode("ISC-01")).thenReturn(Optional.empty());
		when(programRepository.findByOfferNameAndModality("Ingenieria en Software", ProgramModality.PRESENCIAL))
				.thenReturn(Optional.empty());
		when(programRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicProgramResult result = useCase.createProgram(new CreateAcademicProgramCommand(divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc", null));

		assertThat(result.status()).isEqualTo(ProgramStatus.ACTIVE);
		assertThat(result.divisionId()).isEqualTo(divisionId);
		assertThat(result.code()).isEqualTo("ISC-01");
	}

	@Test
	void createProgram_acceptsNullContinuityProgramId() {
		when(divisionRepository.findById(divisionId))
				.thenReturn(Optional.of(new AcademicDivision("Sistemas", "SIS", "desc", null)));
		when(programRepository.findByCode("ISC-01")).thenReturn(Optional.empty());
		when(programRepository.findByOfferNameAndModality("Ingenieria en Software", ProgramModality.PRESENCIAL))
				.thenReturn(Optional.empty());
		when(programRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicProgramResult result = useCase.createProgram(new CreateAcademicProgramCommand(divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc", null));

		assertThat(result.continuityProgramId()).isNull();
	}

	@Test
	void createProgram_rejectsMissingDivisionId() {
		assertThatThrownBy(() -> useCase.createProgram(new CreateAcademicProgramCommand(null,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc", null))).isInstanceOf(DivisionNotFoundException.class);

		verify(programRepository, never()).save(any());
	}

	@Test
	void createProgram_rejectsNonExistentDivisionId() {
		when(divisionRepository.findById(divisionId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.createProgram(new CreateAcademicProgramCommand(divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc", null))).isInstanceOf(DivisionNotFoundException.class);

		verify(programRepository, never()).save(any());
	}

	@Test
	void createProgram_rejectsDuplicateCode() {
		when(divisionRepository.findById(divisionId))
				.thenReturn(Optional.of(new AcademicDivision("Sistemas", "SIS", "desc", null)));
		AcademicProgram existing = new AcademicProgram(divisionId, "Otro", "Otro", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc", null);
		when(programRepository.findByCode("ISC-01")).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> useCase.createProgram(new CreateAcademicProgramCommand(divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc", null))).isInstanceOf(DuplicateProgramCodeException.class);

		verify(programRepository, never()).save(any());
	}

	@Test
	void createProgram_rejectsDuplicateOfferNameAndModality() {
		when(divisionRepository.findById(divisionId))
				.thenReturn(Optional.of(new AcademicDivision("Sistemas", "SIS", "desc", null)));
		when(programRepository.findByCode("ISC-02")).thenReturn(Optional.empty());
		AcademicProgram existing = new AcademicProgram(divisionId, "Ingenieria en Software", "Ingenieria en Software",
				"ISC-01", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null);
		when(programRepository.findByOfferNameAndModality("Ingenieria en Software", ProgramModality.PRESENCIAL))
				.thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> useCase.createProgram(new CreateAcademicProgramCommand(divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-02", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc", null))).isInstanceOf(DuplicateOfferNameModalityException.class);

		verify(programRepository, never()).save(any());
	}

	@Test
	void createProgram_sameOfferNameWithDifferentModalitySucceeds() {
		when(divisionRepository.findById(divisionId))
				.thenReturn(Optional.of(new AcademicDivision("Sistemas", "SIS", "desc", null)));
		when(programRepository.findByCode("ISC-02")).thenReturn(Optional.empty());
		when(programRepository.findByOfferNameAndModality("Ingenieria en Software", ProgramModality.MIXTA))
				.thenReturn(Optional.empty());
		when(programRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicProgramResult result = useCase.createProgram(new CreateAcademicProgramCommand(divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-02", AcademicLevel.INGENIERIA,
				ProgramModality.MIXTA, null, "desc", null));

		assertThat(result.modality()).isEqualTo(ProgramModality.MIXTA);
	}
}
