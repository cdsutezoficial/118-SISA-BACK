package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicDivision;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicProgramUseCase.AcademicProgramResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicProgramUseCase.UpdateAcademicProgramCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicDivisionRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicProgramNotFoundException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateAcademicProgramUseCaseImplTest {

	@Mock
	private AcademicProgramRepository programRepository;
	@Mock
	private AcademicDivisionRepository divisionRepository;

	private UpdateAcademicProgramUseCaseImpl useCase;

	private UUID divisionId;
	private AcademicProgram programA;
	private UUID programAId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateAcademicProgramUseCaseImpl(programRepository, divisionRepository);
		divisionId = UUID.randomUUID();
		programA = new AcademicProgram(divisionId, "Ingenieria en Software", "Ingenieria en Software", "ISC-01",
				AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc");
		programAId = UUID.randomUUID();
		ReflectionTestUtils.setField(programA, "id", programAId);
	}

	@Test
	void updateProgram_successfulUpdateLeavesStatusUnchanged() {
		when(programRepository.findById(programAId)).thenReturn(Optional.of(programA));
		when(divisionRepository.findById(divisionId))
				.thenReturn(Optional.of(new AcademicDivision("Sistemas", "SIS", "desc", null)));
		when(programRepository.findByCode("ISC-02")).thenReturn(Optional.empty());
		when(programRepository.findByOfferNameAndModality("Ingenieria en Software", ProgramModality.MIXTA))
				.thenReturn(Optional.empty());
		when(programRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicProgramResult result = useCase.updateProgram(new UpdateAcademicProgramCommand(programAId, divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-02", AcademicLevel.INGENIERIA,
				ProgramModality.MIXTA, null, "nueva desc"));

		assertThat(result.code()).isEqualTo("ISC-02");
		assertThat(result.modality()).isEqualTo(ProgramModality.MIXTA);
		assertThat(result.status()).isEqualTo(ProgramStatus.ACTIVE);
	}

	@Test
	void updateProgram_allowsKeepingItsOwnCurrentValues() {
		when(programRepository.findById(programAId)).thenReturn(Optional.of(programA));
		when(divisionRepository.findById(divisionId))
				.thenReturn(Optional.of(new AcademicDivision("Sistemas", "SIS", "desc", null)));
		when(programRepository.findByCode("ISC-01")).thenReturn(Optional.of(programA));
		when(programRepository.findByOfferNameAndModality("Ingenieria en Software", ProgramModality.PRESENCIAL))
				.thenReturn(Optional.of(programA));
		when(programRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicProgramResult result = useCase.updateProgram(new UpdateAcademicProgramCommand(programAId, divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "nueva desc"));

		assertThat(result.description()).isEqualTo("nueva desc");
	}

	@Test
	void updateProgram_rejectsCodeConflictWithAnotherProgram() {
		AcademicProgram programB = new AcademicProgram(divisionId, "Ingenieria Industrial", "Ingenieria Industrial",
				"ISC-02", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc");
		UUID programBId = UUID.randomUUID();
		ReflectionTestUtils.setField(programB, "id", programBId);
		when(programRepository.findById(programBId)).thenReturn(Optional.of(programB));
		when(divisionRepository.findById(divisionId))
				.thenReturn(Optional.of(new AcademicDivision("Sistemas", "SIS", "desc", null)));
		when(programRepository.findByCode("ISC-01")).thenReturn(Optional.of(programA));

		assertThatThrownBy(() -> useCase.updateProgram(new UpdateAcademicProgramCommand(programBId, divisionId,
				"Ingenieria Industrial", "Ingenieria Industrial", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc"))).isInstanceOf(DuplicateProgramCodeException.class);

		assertThat(programB.getCode()).isEqualTo("ISC-02");
	}

	@Test
	void updateProgram_rejectsOfferNameModalityConflictWithAnotherProgram() {
		AcademicProgram programB = new AcademicProgram(divisionId, "Ingenieria Industrial", "Ingenieria Industrial",
				"ISC-02", AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc");
		UUID programBId = UUID.randomUUID();
		ReflectionTestUtils.setField(programB, "id", programBId);
		when(programRepository.findById(programBId)).thenReturn(Optional.of(programB));
		when(divisionRepository.findById(divisionId))
				.thenReturn(Optional.of(new AcademicDivision("Sistemas", "SIS", "desc", null)));
		when(programRepository.findByCode("ISC-02")).thenReturn(Optional.of(programB));
		when(programRepository.findByOfferNameAndModality("Ingenieria en Software", ProgramModality.PRESENCIAL))
				.thenReturn(Optional.of(programA));

		assertThatThrownBy(() -> useCase.updateProgram(new UpdateAcademicProgramCommand(programBId, divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-02", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc")))
				.isInstanceOf(DuplicateOfferNameModalityException.class);

		assertThat(programB.getOfferName()).isEqualTo("Ingenieria Industrial");
	}

	@Test
	void updateProgram_rejectsMissingOrNonExistentDivisionId() {
		when(programRepository.findById(programAId)).thenReturn(Optional.of(programA));
		when(divisionRepository.findById(divisionId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateProgram(new UpdateAcademicProgramCommand(programAId, divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc"))).isInstanceOf(DivisionNotFoundException.class);
	}

	@Test
	void updateProgram_rejectsUnknownProgramId() {
		UUID unknownId = UUID.randomUUID();
		when(programRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateProgram(new UpdateAcademicProgramCommand(unknownId, divisionId,
				"Ingenieria en Software", "Ingenieria en Software", "ISC-01", AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, "desc"))).isInstanceOf(AcademicProgramNotFoundException.class);
	}
}
