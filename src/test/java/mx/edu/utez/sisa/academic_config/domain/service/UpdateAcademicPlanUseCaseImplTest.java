package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateAcademicPlanUseCase.UpdateAcademicPlanCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePlanVersionException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidSocialServiceLevelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateAcademicPlanUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private UpdateAcademicPlanUseCaseImpl useCase;

	private UUID programId;
	private AcademicPlan planA;
	private UUID planAId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateAcademicPlanUseCaseImpl(planRepository);
		programId = UUID.randomUUID();
		planA = new AcademicPlan(programId, "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
				BigDecimal.valueOf(6.0), 3, false, null);
		planAId = UUID.randomUUID();
		ReflectionTestUtils.setField(planA, "id", planAId);
	}

	@Test
	void updatePlan_successfulUpdateLeavesStatusUnchanged() {
		when(planRepository.findById(planAId)).thenReturn(Optional.of(planA));
		when(planRepository.findByProgramIdAndVersion(programId, "2023-A")).thenReturn(Optional.empty());
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicPlanResult result = useCase.updatePlan(new UpdateAcademicPlanCommand(planAId, "2023-A",
				"Enero 2023", "TIT-002", LocalDate.of(2023, 1, 1), 7, BigDecimal.valueOf(7.0), 2, false, null));

		assertThat(result.version()).isEqualTo("2023-A");
		assertThat(result.status()).isEqualTo(PlanStatus.ACTIVE);
	}

	@Test
	void updatePlan_allowsKeepingItsOwnCurrentVersion() {
		when(planRepository.findById(planAId)).thenReturn(Optional.of(planA));
		when(planRepository.findByProgramIdAndVersion(programId, "2022-A")).thenReturn(Optional.of(planA));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicPlanResult result = useCase.updatePlan(new UpdateAcademicPlanCommand(planAId, "2022-A",
				"Nuevo periodo", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null));

		assertThat(result.validityPeriod()).isEqualTo("Nuevo periodo");
	}

	@Test
	void updatePlan_rejectsVersionConflictWithAnotherPlanSameProgram() {
		AcademicPlan planB = new AcademicPlan(programId, "2023-A", "Enero 2023", "TIT-002",
				LocalDate.of(2023, 1, 1), 7, BigDecimal.valueOf(7.0), 2, false, null);
		UUID planBId = UUID.randomUUID();
		ReflectionTestUtils.setField(planB, "id", planBId);
		when(planRepository.findById(planBId)).thenReturn(Optional.of(planB));
		when(planRepository.findByProgramIdAndVersion(programId, "2022-A")).thenReturn(Optional.of(planA));

		assertThatThrownBy(() -> useCase.updatePlan(new UpdateAcademicPlanCommand(planBId, "2022-A", "Enero 2023",
				"TIT-002", LocalDate.of(2023, 1, 1), 7, BigDecimal.valueOf(7.0), 2, false, null)))
				.isInstanceOf(DuplicatePlanVersionException.class);

		assertThat(planB.getVersion()).isEqualTo("2023-A");
	}

	@Test
	void updatePlan_acceptsSocialServiceMinLevelIdReferencingOwnPlanLevel() {
		PlanLevel level = planA.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		when(planRepository.findById(planAId)).thenReturn(Optional.of(planA));
		when(planRepository.findByProgramIdAndVersion(programId, "2022-A")).thenReturn(Optional.of(planA));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicPlanResult result = useCase.updatePlan(new UpdateAcademicPlanCommand(planAId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, true, levelId));

		assertThat(result.socialServiceMinLevelId()).isEqualTo(levelId);
		assertThat(result.requiresSocialService()).isTrue();
	}

	@Test
	void updatePlan_rejectsSocialServiceMinLevelIdReferencingAnotherPlansLevel() {
		AcademicPlan planB = new AcademicPlan(UUID.randomUUID(), "2022-B", "Septiembre 2022", "TIT-003",
				LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null);
		PlanLevel levelOfB = planB.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelOfBId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelOfB, "id", levelOfBId);
		when(planRepository.findById(planAId)).thenReturn(Optional.of(planA));
		when(planRepository.findByProgramIdAndVersion(programId, "2022-A")).thenReturn(Optional.of(planA));

		assertThatThrownBy(() -> useCase.updatePlan(new UpdateAcademicPlanCommand(planAId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, true,
				levelOfBId))).isInstanceOf(InvalidSocialServiceLevelException.class);
	}

	@Test
	void updatePlan_rejectsUnknownPlanId() {
		UUID unknownId = UUID.randomUUID();
		when(planRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updatePlan(new UpdateAcademicPlanCommand(unknownId, "2022-A",
				"Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null)))
				.isInstanceOf(AcademicPlanNotFoundException.class);
	}
}
