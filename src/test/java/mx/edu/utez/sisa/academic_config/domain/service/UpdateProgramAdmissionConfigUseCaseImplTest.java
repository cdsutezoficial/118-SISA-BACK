package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.PeriodType;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateProgramAdmissionConfigUseCase.UpdateProgramAdmissionConfigCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramAdmissionConfigException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramAdmissionConfigNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramNotFoundException;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
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
class UpdateProgramAdmissionConfigUseCaseImplTest {

	private static final Instant OPENS_AT = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant CLOSES_AT = Instant.parse("2026-03-01T00:00:00Z");

	@Mock
	private ProgramAdmissionConfigRepository configRepository;

	@Mock
	private AcademicProgramRepository programRepository;

	@Mock
	private AcademicPeriodRepository periodRepository;

	@Mock
	private GenerationRepository generationRepository;

	private UpdateProgramAdmissionConfigUseCaseImpl useCase;

	private UUID configId;

	private UUID programId;

	private UUID periodId;

	private UUID targetGenerationId;

	@BeforeEach
	void setUp() {
		useCase = new UpdateProgramAdmissionConfigUseCaseImpl(configRepository, programRepository, periodRepository,
				generationRepository);
		configId = UUID.randomUUID();
		programId = UUID.randomUUID();
		periodId = UUID.randomUUID();
		targetGenerationId = UUID.randomUUID();
	}

	@Test
	void updateProgramAdmissionConfig_successfulUpdateChangesCatalogFields() {
		ProgramAdmissionConfig existing = existingConfig();
		when(configRepository.findById(configId)).thenReturn(Optional.of(existing));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));
		when(generationRepository.findById(targetGenerationId)).thenReturn(Optional.of(newGeneration()));
		when(configRepository.findByProgramIdAndPeriodId(programId, periodId)).thenReturn(Optional.empty());
		when(configRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ProgramAdmissionConfigResult result = useCase.updateProgramAdmissionConfig(
				new UpdateProgramAdmissionConfigCommand(configId, programId, periodId, targetGenerationId, false, 80,
						OPENS_AT, CLOSES_AT));

		assertThat(result.maxCandidates()).isEqualTo(80);
		assertThat(result.isOffered()).isFalse();
	}

	@Test
	void updateProgramAdmissionConfig_rejectsUnknownConfigId() {
		when(configRepository.findById(configId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateProgramAdmissionConfig(
				new UpdateProgramAdmissionConfigCommand(configId, programId, periodId, targetGenerationId, true, 50,
						OPENS_AT, CLOSES_AT)))
				.isInstanceOf(ProgramAdmissionConfigNotFoundException.class);

		verify(configRepository, never()).save(any());
	}

	@Test
	void updateProgramAdmissionConfig_rejectsNonExistentProgramId() {
		ProgramAdmissionConfig existing = existingConfig();
		when(configRepository.findById(configId)).thenReturn(Optional.of(existing));
		when(programRepository.findById(programId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateProgramAdmissionConfig(
				new UpdateProgramAdmissionConfigCommand(configId, programId, periodId, targetGenerationId, true, 50,
						OPENS_AT, CLOSES_AT)))
				.isInstanceOf(ProgramNotFoundException.class);

		verify(configRepository, never()).save(any());
	}

	@Test
	void updateProgramAdmissionConfig_rejectsNonExistentPeriodId() {
		ProgramAdmissionConfig existing = existingConfig();
		when(configRepository.findById(configId)).thenReturn(Optional.of(existing));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateProgramAdmissionConfig(
				new UpdateProgramAdmissionConfigCommand(configId, programId, periodId, targetGenerationId, true, 50,
						OPENS_AT, CLOSES_AT)))
				.isInstanceOf(PeriodNotFoundException.class);

		verify(configRepository, never()).save(any());
	}

	@Test
	void updateProgramAdmissionConfig_rejectsNonExistentTargetGenerationId() {
		ProgramAdmissionConfig existing = existingConfig();
		when(configRepository.findById(configId)).thenReturn(Optional.of(existing));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));
		when(generationRepository.findById(targetGenerationId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.updateProgramAdmissionConfig(
				new UpdateProgramAdmissionConfigCommand(configId, programId, periodId, targetGenerationId, true, 50,
						OPENS_AT, CLOSES_AT)))
				.isInstanceOf(GenerationReferenceNotFoundException.class);

		verify(configRepository, never()).save(any());
	}

	@Test
	void updateProgramAdmissionConfig_rejectsCollisionWithAnotherRecord() {
		ProgramAdmissionConfig existing = existingConfig();
		ProgramAdmissionConfig other = new ProgramAdmissionConfig(programId, periodId, targetGenerationId, true, 30,
				OPENS_AT, CLOSES_AT);
		ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
		when(configRepository.findById(configId)).thenReturn(Optional.of(existing));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));
		when(generationRepository.findById(targetGenerationId)).thenReturn(Optional.of(newGeneration()));
		when(configRepository.findByProgramIdAndPeriodId(programId, periodId)).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> useCase.updateProgramAdmissionConfig(
				new UpdateProgramAdmissionConfigCommand(configId, programId, periodId, targetGenerationId, true, 50,
						OPENS_AT, CLOSES_AT)))
				.isInstanceOf(DuplicateProgramAdmissionConfigException.class);

		verify(configRepository, never()).save(any());
	}

	@Test
	void updateProgramAdmissionConfig_ownCurrentProgramIdPeriodIdNeverCountsAsConflict() {
		ProgramAdmissionConfig existing = existingConfig();
		when(configRepository.findById(configId)).thenReturn(Optional.of(existing));
		when(programRepository.findById(programId)).thenReturn(Optional.of(newProgram()));
		when(periodRepository.findById(periodId)).thenReturn(Optional.of(newPeriod()));
		when(generationRepository.findById(targetGenerationId)).thenReturn(Optional.of(newGeneration()));
		when(configRepository.findByProgramIdAndPeriodId(programId, periodId)).thenReturn(Optional.of(existing));
		when(configRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ProgramAdmissionConfigResult result = useCase.updateProgramAdmissionConfig(
				new UpdateProgramAdmissionConfigCommand(configId, programId, periodId, targetGenerationId, true, 99,
						OPENS_AT, CLOSES_AT));

		assertThat(result.maxCandidates()).isEqualTo(99);
	}

	private ProgramAdmissionConfig existingConfig() {
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(programId, periodId, targetGenerationId, true, 50,
				OPENS_AT, CLOSES_AT);
		ReflectionTestUtils.setField(config, "id", configId);
		return config;
	}

	private AcademicProgram newProgram() {
		AcademicProgram program = new AcademicProgram(UUID.randomUUID(), "Ingenieria en Software",
				"Oferta " + UUID.randomUUID(), "COD-" + UUID.randomUUID().toString().substring(0, 8),
				AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc", null);
		ReflectionTestUtils.setField(program, "id", programId);
		return program;
	}

	private AcademicPeriod newPeriod() {
		AcademicPeriod period = new AcademicPeriod("Periodo 2026", 2026, 1, PeriodType.CUATRIMESTRAL,
				LocalDate.of(2026, 1, 5), LocalDate.of(2026, 4, 30), LocalDate.of(2025, 12, 1),
				LocalDate.of(2025, 12, 20));
		ReflectionTestUtils.setField(period, "id", periodId);
		return period;
	}

	private Generation newGeneration() {
		Generation generation = new Generation(UUID.randomUUID(), UUID.randomUUID(), programId, 7, 2026);
		ReflectionTestUtils.setField(generation, "id", targetGenerationId);
		return generation;
	}
}
