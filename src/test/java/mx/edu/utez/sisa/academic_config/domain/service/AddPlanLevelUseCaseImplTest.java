package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.port.in.AddPlanLevelUseCase.AddPlanLevelCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.PlanLevelResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateLevelNumberException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddPlanLevelUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private AddPlanLevelUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;

	@BeforeEach
	void setUp() {
		useCase = new AddPlanLevelUseCaseImpl(planRepository);
		plan = new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
				BigDecimal.valueOf(6.0), 3, false, null);
		planId = UUID.randomUUID();
		ReflectionTestUtils.setField(plan, "id", planId);
	}

	@Test
	void addLevel_successfulAddition() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PlanLevelResult result = useCase
				.addLevel(new AddPlanLevelCommand(planId, 3, PlanLevelType.REGULAR, "Tercer cuatrimestre"));

		assertThat(result.levelNumber()).isEqualTo(3);
		assertThat(result.type()).isEqualTo(PlanLevelType.REGULAR);
		assertThat(result.description()).isEqualTo("Tercer cuatrimestre");
		assertThat(plan.getLevels()).hasSize(1);
	}

	@Test
	void addLevel_rejectsLevelNumberOutsideTotalLevelsRange() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

		assertThatThrownBy(() -> useCase.addLevel(new AddPlanLevelCommand(planId, 7, PlanLevelType.REGULAR, null)))
				.isInstanceOf(IllegalArgumentException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void addLevel_rejectsDuplicateLevelNumberWithinTheSamePlan() {
		plan.addLevel(3, PlanLevelType.REGULAR, null);
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

		assertThatThrownBy(
				() -> useCase.addLevel(new AddPlanLevelCommand(planId, 3, PlanLevelType.INTERNSHIP, null)))
				.isInstanceOf(DuplicateLevelNumberException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void addLevel_rejectsUnknownPlanId() {
		UUID unknownId = UUID.randomUUID();
		when(planRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.addLevel(new AddPlanLevelCommand(unknownId, 1, PlanLevelType.REGULAR, null)))
				.isInstanceOf(AcademicPlanNotFoundException.class);

		verify(planRepository, never()).save(any());
	}
}
