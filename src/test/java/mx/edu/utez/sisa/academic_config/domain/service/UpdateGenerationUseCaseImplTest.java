package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGenerationUseCase.UpdateGenerationCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGenerationNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanNotFoundException;
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
class UpdateGenerationUseCaseImplTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	@Mock
	private GenerationRepository generationRepository;

	@Mock
	private AcademicPlanRepository planRepository;

	@Mock
	private AcademicPeriodRepository periodRepository;

	private UpdateGenerationUseCaseImpl useCase;

	private UUID generationId;

	private UUID planId;

	private UUID startPeriodId;

	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateGenerationUseCaseImpl(generationRepository, planRepository, periodRepository);
		generationId = UUID.randomUUID();
		planId = UUID.randomUUID();
		startPeriodId = UUID.randomUUID();
		programId = UUID.randomUUID();
	}

	@Test
	void updateGeneration_successfulUpdateRecomputesCode() {
		Generation existing = existingGeneration(7);
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(existing));
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.of(newPeriod(2027)));
		when(generationRepository.findByProgramIdAndNumber(programId, 8)).thenReturn(Optional.empty());
		when(generationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GenerationResult result = useCase
				.updateGeneration(new UpdateGenerationCommand(generationId, planId, startPeriodId, 8));

		assertThat(result.number()).isEqualTo(8);
		assertThat(result.code()).isEqualTo("2027-8");
	}

	@Test
	void updateGeneration_rejectsUnknownGenerationId() {
		when(generationRepository.findById(generationId)).thenReturn(Optional.empty());

		assertThatThrownBy(
				() -> useCase.updateGeneration(new UpdateGenerationCommand(generationId, planId, startPeriodId, 7)))
				.isInstanceOf(GenerationNotFoundException.class);

		verify(generationRepository, never()).save(any());
	}

	@Test
	void updateGeneration_rejectsNonExistentPlanId() {
		Generation existing = existingGeneration(7);
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(existing));
		when(planRepository.findById(planId)).thenReturn(Optional.empty());

		assertThatThrownBy(
				() -> useCase.updateGeneration(new UpdateGenerationCommand(generationId, planId, startPeriodId, 7)))
				.isInstanceOf(PlanNotFoundException.class);

		verify(generationRepository, never()).save(any());
	}

	@Test
	void updateGeneration_rejectsNonExistentStartPeriodId() {
		Generation existing = existingGeneration(7);
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(existing));
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.empty());

		assertThatThrownBy(
				() -> useCase.updateGeneration(new UpdateGenerationCommand(generationId, planId, startPeriodId, 7)))
				.isInstanceOf(PeriodNotFoundException.class);

		verify(generationRepository, never()).save(any());
	}

	@Test
	void updateGeneration_rejectsDuplicateNumberFromAnotherRecordInSameProgram() {
		Generation existing = existingGeneration(7);
		Generation conflicting = new Generation(planId, startPeriodId, programId, 9, 2026);
		ReflectionTestUtils.setField(conflicting, "id", UUID.randomUUID());
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(existing));
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.of(newPeriod(2026)));
		when(generationRepository.findByProgramIdAndNumber(programId, 9)).thenReturn(Optional.of(conflicting));

		assertThatThrownBy(
				() -> useCase.updateGeneration(new UpdateGenerationCommand(generationId, planId, startPeriodId, 9)))
				.isInstanceOf(DuplicateGenerationNumberException.class);

		verify(generationRepository, never()).save(any());
	}

	@Test
	void updateGeneration_selfUpdateWithUnchangedNumberSucceeds() {
		// The record's own current row must never be treated as a conflict
		// against itself — same self-update rule as
		// UpdateAcademicPeriodUseCaseImpl's (year, periodNumber) revalidation.
		Generation existing = existingGeneration(7);
		when(generationRepository.findById(generationId)).thenReturn(Optional.of(existing));
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.of(newPeriod(2026)));
		when(generationRepository.findByProgramIdAndNumber(programId, 7)).thenReturn(Optional.of(existing));
		when(generationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GenerationResult result = useCase
				.updateGeneration(new UpdateGenerationCommand(generationId, planId, startPeriodId, 7));

		assertThat(result.number()).isEqualTo(7);
		assertThat(result.code()).isEqualTo("2026-7");
	}

	private Generation existingGeneration(int number) {
		Generation generation = new Generation(planId, startPeriodId, programId, number, 2026);
		ReflectionTestUtils.setField(generation, "id", generationId);
		return generation;
	}

	private AcademicPlan newPlan() {
		return new AcademicPlan(programId, "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1), 6,
				BigDecimal.valueOf(6.0), 3, false, null);
	}

	private static AcademicPeriod newPeriod(int year) {
		return new AcademicPeriod("Periodo " + year, year, 1, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START,
				ENROLLMENT_END);
	}
}
