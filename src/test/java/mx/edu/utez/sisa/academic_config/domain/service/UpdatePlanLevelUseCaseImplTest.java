package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevelType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateAcademicPlanUseCase.PlanLevelResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePlanLevelUseCase.UpdatePlanLevelCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.AcademicPlanNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
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
class UpdatePlanLevelUseCaseImplTest {

	@Mock
	private AcademicPlanRepository planRepository;

	private UpdatePlanLevelUseCaseImpl useCase;

	private AcademicPlan plan;
	private UUID planId;
	private PlanLevel level;
	private UUID levelId;

	@BeforeEach
	void setUp() {
		useCase = new UpdatePlanLevelUseCaseImpl(planRepository);
		plan = new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
				BigDecimal.valueOf(6.0), 3, false, null);
		planId = UUID.randomUUID();
		ReflectionTestUtils.setField(plan, "id", planId);
		level = plan.addLevel(1, PlanLevelType.REGULAR, "Primer cuatrimestre");
		levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
	}

	@Test
	void updateLevel_successfulUpdate() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		when(planRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PlanLevelResult result = useCase.updateLevel(
				new UpdatePlanLevelCommand(planId, levelId, 1, PlanLevelType.INTERNSHIP, "Cuatrimestre actualizado"));

		assertThat(result.type()).isEqualTo(PlanLevelType.INTERNSHIP);
		assertThat(result.description()).isEqualTo("Cuatrimestre actualizado");
	}

	@Test
	void updateLevel_rejectsUnknownLevelId() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
		UUID unknownLevelId = UUID.randomUUID();

		assertThatThrownBy(() -> useCase.updateLevel(
				new UpdatePlanLevelCommand(planId, unknownLevelId, 1, PlanLevelType.REGULAR, null)))
				.isInstanceOf(PlanLevelNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void updateLevel_rejectsUnknownPlanId() {
		UUID unknownPlanId = UUID.randomUUID();
		when(planRepository.findById(unknownPlanId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.updateLevel(new UpdatePlanLevelCommand(unknownPlanId, levelId, 1, PlanLevelType.REGULAR, null)))
				.isInstanceOf(AcademicPlanNotFoundException.class);

		verify(planRepository, never()).save(any());
	}

	@Test
	void updateLevel_rejectsLevelNumberOutsideTotalLevelsRange() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

		assertThatThrownBy(() -> useCase
				.updateLevel(new UpdatePlanLevelCommand(planId, levelId, 99, PlanLevelType.REGULAR, null)))
				.isInstanceOf(InvalidPlanDataException.class);

		verify(planRepository, never()).save(any());
	}
}
