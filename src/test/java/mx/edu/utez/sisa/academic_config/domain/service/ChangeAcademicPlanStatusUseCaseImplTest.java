package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangeAcademicPlanStatusUseCase.ChangeStatusCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.AcademicPlanResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
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
class ChangeAcademicPlanStatusUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private ChangeAcademicPlanStatusUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;

	@BeforeEach
	void setUp() {
		useCase = new ChangeAcademicPlanStatusUseCaseImpl(planRepository);
		plan = new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1),
				6, BigDecimal.valueOf(6.0), 3, false, null);
		planId = UUID.randomUUID();
		ReflectionTestUtils.setField(plan, "id", planId);
	}

	@Test
	void changeStatus_deactivatesAnActivePlan() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicPlanResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), planId, PlanStatus.INACTIVE));

		assertThat(result.status()).isEqualTo(PlanStatus.INACTIVE);
	}

	@Test
	void changeStatus_reactivatesAnInactivePlanIndependentlyOfOtherPlansUnderSameProgram() {
		plan.deactivate();
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicPlanResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), planId, PlanStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(PlanStatus.ACTIVE);
	}

	@Test
	void changeStatus_rejectsUnknownPlanId() {
		UUID unknownId = UUID.randomUUID();
		when(planRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), unknownId, PlanStatus.ACTIVE)))
				.isInstanceOf(AcademicPlanNotFoundException.class);
	}
}
