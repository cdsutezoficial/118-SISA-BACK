package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.CreateGenerationCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGenerationNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanNotFoundException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateGenerationUseCaseImplTest {

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

	private CreateGenerationUseCaseImpl useCase;

	private UUID planId;

	private UUID startPeriodId;

	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new CreateGenerationUseCaseImpl(generationRepository, planRepository, periodRepository);
		planId = UUID.randomUUID();
		startPeriodId = UUID.randomUUID();
		programId = UUID.randomUUID();
	}

	@Test
	void createGeneration_successfulCreationComputesCodeFromStartPeriodYear() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.of(newPeriod(2026)));
		when(generationRepository.findByProgramIdAndNumber(programId, 7)).thenReturn(Optional.empty());
		when(generationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GenerationResult result = useCase.createGeneration(new CreateGenerationCommand(planId, startPeriodId, 7));

		assertThat(result.status()).isEqualTo(GenerationStatus.ACTIVE);
		assertThat(result.programId()).isEqualTo(programId);
		assertThat(result.number()).isEqualTo(7);
		assertThat(result.code()).isEqualTo("2026-7");
	}

	@Test
	void createGeneration_ignoresClientSuppliedCodeAndAlwaysComputesItServerSide() {
		// The command has no `code` field at all — this test documents that
		// contract: the result's code is derived purely from startPeriod's
		// year + number, never accepted as input.
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.of(newPeriod(2030)));
		when(generationRepository.findByProgramIdAndNumber(programId, 1)).thenReturn(Optional.empty());
		when(generationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GenerationResult result = useCase.createGeneration(new CreateGenerationCommand(planId, startPeriodId, 1));

		assertThat(result.code()).isEqualTo("2030-1");
	}

	@Test
	void createGeneration_rejectsMissingPlanId() {
		assertThatThrownBy(() -> useCase.createGeneration(new CreateGenerationCommand(null, startPeriodId, 1)))
				.isInstanceOf(PlanNotFoundException.class);

		verify(generationRepository, never()).save(any());
	}

	@Test
	void createGeneration_rejectsNonExistentPlanId() {
		when(planRepository.findById(planId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.createGeneration(new CreateGenerationCommand(planId, startPeriodId, 1)))
				.isInstanceOf(PlanNotFoundException.class);

		verify(generationRepository, never()).save(any());
	}

	@Test
	void createGeneration_rejectsMissingStartPeriodId() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));

		assertThatThrownBy(() -> useCase.createGeneration(new CreateGenerationCommand(planId, null, 1)))
				.isInstanceOf(PeriodNotFoundException.class);

		verify(generationRepository, never()).save(any());
	}

	@Test
	void createGeneration_rejectsNonExistentStartPeriodId() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.createGeneration(new CreateGenerationCommand(planId, startPeriodId, 1)))
				.isInstanceOf(PeriodNotFoundException.class);

		verify(generationRepository, never()).save(any());
	}

	@Test
	void createGeneration_rejectsDuplicateNumberWithinSameProgram() {
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.of(newPeriod(2026)));
		Generation existing = new Generation(planId, startPeriodId, programId, 7, 2026);
		when(generationRepository.findByProgramIdAndNumber(programId, 7)).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> useCase.createGeneration(new CreateGenerationCommand(planId, startPeriodId, 7)))
				.isInstanceOf(DuplicateGenerationNumberException.class);

		verify(generationRepository, never()).save(any());
	}

	@Test
	void createGeneration_allowsMultipleGenerationsForSameProgramInSameCalendarYear() {
		// PO-confirmed 2026-07-20: a program CAN open more than one generation
		// in the same calendar year (September intake, then a January
		// follow-up, then September again) — there is NO uniqueness on
		// (programId, year), only on (programId, number). Regression guard: if
		// a (programId, year) uniqueness check is ever added by mistake, this
		// test must fail.
		UUID otherPlanId = UUID.randomUUID();
		UUID otherStartPeriodId = UUID.randomUUID();
		when(planRepository.findById(planId)).thenReturn(Optional.of(newPlan()));
		when(planRepository.findById(otherPlanId)).thenReturn(Optional.of(newPlan()));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.of(newPeriod(2026)));
		when(periodRepository.findById(otherStartPeriodId)).thenReturn(Optional.of(newPeriod(2026)));
		when(generationRepository.findByProgramIdAndNumber(programId, 7)).thenReturn(Optional.empty());
		when(generationRepository.findByProgramIdAndNumber(programId, 8)).thenReturn(Optional.empty());
		when(generationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GenerationResult first = useCase.createGeneration(new CreateGenerationCommand(planId, startPeriodId, 7));
		GenerationResult second = useCase
				.createGeneration(new CreateGenerationCommand(otherPlanId, otherStartPeriodId, 8));

		assertThat(first.code()).isEqualTo("2026-7");
		assertThat(second.code()).isEqualTo("2026-8");
		assertThat(first.programId()).isEqualTo(programId);
		assertThat(second.programId()).isEqualTo(programId);
		verify(generationRepository, times(2)).save(any());
	}

	@Test
	void createGeneration_sameNumberAcrossDifferentProgramsSucceeds() {
		UUID otherProgramId = UUID.randomUUID();
		UUID otherPlanId = UUID.randomUUID();
		AcademicPlan otherPlan = new AcademicPlan(otherProgramId, "2022-A", "Septiembre 2022", "TIT-001",
				LocalDate.of(2022, 9, 1), 6, BigDecimal.valueOf(6.0), 3, false, null);
		when(planRepository.findById(otherPlanId)).thenReturn(Optional.of(otherPlan));
		when(periodRepository.findById(startPeriodId)).thenReturn(Optional.of(newPeriod(2026)));
		when(generationRepository.findByProgramIdAndNumber(otherProgramId, 7)).thenReturn(Optional.empty());
		when(generationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		GenerationResult result = useCase.createGeneration(new CreateGenerationCommand(otherPlanId, startPeriodId, 7));

		assertThat(result.programId()).isEqualTo(otherProgramId);
		assertThat(result.number()).isEqualTo(7);
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
