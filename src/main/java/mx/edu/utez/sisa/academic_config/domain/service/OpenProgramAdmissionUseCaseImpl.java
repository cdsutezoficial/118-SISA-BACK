package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramAdmissionConfigException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates a {@code ProgramAdmissionConfig} (plan:
 * {@code docs/plans/2026-07-28-program-admission-config.md}). Validates its
 * three FKs, reusing existing 400 exceptions already established by prior
 * aggregates rather than inventing new ones — same
 * "reuse-first-new-only-if-missing" precedent as {@code CreateGroupUseCaseImpl}:
 * <ul>
 * <li>{@code programId} against {@link AcademicProgramRepository} — reuses
 * {@link ProgramNotFoundException} (400), already established by
 * {@code CreateAcademicPlanUseCaseImpl} for the exact same "bad FK, not a
 * resource-not-found" shape.
 * <li>{@code periodId} (the DESTINATION period) against
 * {@link AcademicPeriodRepository} — reuses {@link PeriodNotFoundException}
 * (400) via {@link CreateGenerationUseCaseImpl#requirePeriod}.
 * <li>{@code targetGenerationId} against {@link GenerationRepository} —
 * reuses {@link GenerationReferenceNotFoundException} (400) via
 * {@link CreateGroupUseCaseImpl#requireGeneration}, already created during
 * the Group work for this exact "reference to Generation" shape.
 * </ul>
 * {@code (programId, periodId)} uniqueness is enforced afterward
 * (cross-record check, needs repository access — same split as
 * {@code Generation}'s {@code (programId, number)} uniqueness).
 * {@code maxCandidates > 0}/{@code closesAt > opensAt} are validated inside
 * the {@link ProgramAdmissionConfig} constructor itself (pure same-entity
 * invariants). {@code status} always defaults to {@code OPEN} and
 * {@code selectionStatus} to {@code IN_REVIEW}, both set by the constructor,
 * never accepted as caller input.
 */
public class OpenProgramAdmissionUseCaseImpl implements OpenProgramAdmissionUseCase {

	private final ProgramAdmissionConfigRepository configRepository;

	private final AcademicProgramRepository programRepository;

	private final AcademicPeriodRepository periodRepository;

	private final GenerationRepository generationRepository;

	public OpenProgramAdmissionUseCaseImpl(ProgramAdmissionConfigRepository configRepository,
			AcademicProgramRepository programRepository, AcademicPeriodRepository periodRepository,
			GenerationRepository generationRepository) {
		this.configRepository = configRepository;
		this.programRepository = programRepository;
		this.periodRepository = periodRepository;
		this.generationRepository = generationRepository;
	}

	@Override
	@Transactional
	public ProgramAdmissionConfigResult openProgramAdmission(OpenProgramAdmissionCommand command) {
		requireProgram(command.programId(), programRepository);
		CreateGenerationUseCaseImpl.requirePeriod(command.periodId(), periodRepository);
		CreateGroupUseCaseImpl.requireGeneration(command.targetGenerationId(), generationRepository);

		configRepository.findByProgramIdAndPeriodId(command.programId(), command.periodId()).ifPresent(found -> {
			throw new DuplicateProgramAdmissionConfigException("Program admission config already exists for programId="
					+ command.programId() + ", periodId=" + command.periodId());
		});

		ProgramAdmissionConfig config = new ProgramAdmissionConfig(command.programId(), command.periodId(),
				command.targetGenerationId(), command.isOffered(), command.maxCandidates(), command.opensAt(),
				command.closesAt());
		ProgramAdmissionConfig saved = configRepository.save(config);

		return toResult(saved);
	}

	static AcademicProgram requireProgram(UUID programId, AcademicProgramRepository programRepository) {
		if (programId == null) {
			throw new ProgramNotFoundException("Academic program not found: null");
		}
		return programRepository.findById(programId)
				.orElseThrow(() -> new ProgramNotFoundException("Academic program not found: " + programId));
	}

	static ProgramAdmissionConfigResult toResult(ProgramAdmissionConfig config) {
		return new ProgramAdmissionConfigResult(config.getId(), config.getProgramId(), config.getPeriodId(),
				config.getTargetGenerationId(), config.isOffered(), config.getMaxCandidates(), config.getOpensAt(),
				config.getClosesAt(), config.getStatus(), config.getSelectionStatus());
	}
}
