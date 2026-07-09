package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.CreateAcademicPlanCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidSocialServiceLevelException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramNotFoundException;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateAcademicPlanUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;
	@Mock
	private AcademicProgramRepository programRepository;

	private CreateAcademicPlanUseCaseImpl useCase;

	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new CreateAcademicPlanUseCaseImpl(planRepository, programRepository);
		programId = UUID.randomUUID();
	}

	@Test
	void createPlan_successfulCreationWithValidData() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(planRepository.findByProgramIdAndVersion(programId, "2022-A")).thenReturn(Optional.empty());
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicPlanResult result = useCase.createPlan(new CreateAcademicPlanCommand(programId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null));

		assertThat(result.status()).isEqualTo(PlanStatus.ACTIVE);
		assertThat(result.programId()).isEqualTo(programId);
		assertThat(result.version()).isEqualTo("2022-A");
		assertThat(result.levels()).isEmpty();
	}

	@Test
	void createPlan_rejectsMissingProgramId() {
		assertThatThrownBy(() -> useCase.createPlan(new CreateAcademicPlanCommand(null, "2022-A", "Septiembre 2022",
				"TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null)))
				.isInstanceOf(ProgramNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void createPlan_rejectsNonExistentProgramId() {
		when(programRepository.findById(programId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.createPlan(new CreateAcademicPlanCommand(programId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null)))
				.isInstanceOf(ProgramNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void createPlan_rejectsDuplicateVersionWithinSameProgram() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		AcademicPlan existing = new AcademicPlan(programId, "2022-A", "Septiembre 2022", "TIT-001",
				LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null);
		when(planRepository.findByProgramIdAndVersion(programId, "2022-A")).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> useCase.createPlan(new CreateAcademicPlanCommand(programId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null)))
				.isInstanceOf(mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePlanVersionException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void createPlan_sameVersionAcrossDifferentProgramsSucceeds() {
		UUID otherProgramId = UUID.randomUUID();
		when(programRepository.findById(otherProgramId)).thenReturn(Optional.of(newProgram()));
		when(planRepository.findByProgramIdAndVersion(otherProgramId, "2022-A")).thenReturn(Optional.empty());
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicPlanResult result = useCase.createPlan(new CreateAcademicPlanCommand(otherProgramId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null));

		assertThat(result.programId()).isEqualTo(otherProgramId);
		assertThat(result.version()).isEqualTo("2022-A");
	}

	@Test
	void createPlan_rejectsRequiresSocialServiceTrueWithNonNullSocialServiceMinLevelId() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));

		assertThatThrownBy(() -> useCase.createPlan(new CreateAcademicPlanCommand(programId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, true,
				UUID.randomUUID()))).isInstanceOf(InvalidSocialServiceLevelException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void createPlan_rejectsRequiresSocialServiceFalseWithNonNullSocialServiceMinLevelId() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));

		assertThatThrownBy(() -> useCase.createPlan(new CreateAcademicPlanCommand(programId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false,
				UUID.randomUUID()))).isInstanceOf(InvalidSocialServiceLevelException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void createPlan_rejectsOutOfRangeMinPassingGrade() {
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));

		assertThatThrownBy(() -> useCase.createPlan(new CreateAcademicPlanCommand(programId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(70), 3, false, null)))
				.isInstanceOf(IllegalArgumentException.class);

		verify(planRepository, never()).save(any());
	}

	private AcademicProgram newProgram() {
		return new AcademicProgram(UUID.randomUUID(), "Ingenieria en Software", "Ingenieria en Software", "ISC-01",
				AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null);
	}
}
