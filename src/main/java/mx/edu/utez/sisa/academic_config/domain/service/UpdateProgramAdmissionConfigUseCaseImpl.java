package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.domain.port.in.OpenProgramAdmissionUseCase.ProgramAdmissionConfigResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateProgramAdmissionConfigUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.ProgramAdmissionConfigRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateProgramAdmissionConfigException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramAdmissionConfigNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code ProgramAdmissionConfig}'s catalog fields.
 * Applies the same three-FK existence and {@code (programId, periodId)}
 * uniqueness rules as creation, except a config's own current
 * {@code (programId, periodId)} never counts as a conflict against itself —
 * same self-update rule as {@code UpdateGenerationUseCaseImpl}'s
 * {@code (programId, number)} revalidation. Never touches {@code status} or
 * {@code selectionStatus} (plan §3/§5).
 */
public class UpdateProgramAdmissionConfigUseCaseImpl implements UpdateProgramAdmissionConfigUseCase {

	private final ProgramAdmissionConfigRepository configRepository;

	private final AcademicProgramRepository programRepository;

	private final AcademicPeriodRepository periodRepository;

	private final GenerationRepository generationRepository;

	public UpdateProgramAdmissionConfigUseCaseImpl(ProgramAdmissionConfigRepository configRepository,
			AcademicProgramRepository programRepository, AcademicPeriodRepository periodRepository,
			GenerationRepository generationRepository) {
		this.configRepository = configRepository;
		this.programRepository = programRepository;
		this.periodRepository = periodRepository;
		this.generationRepository = generationRepository;
	}

	@Override
	@Transactional
	public ProgramAdmissionConfigResult updateProgramAdmissionConfig(UpdateProgramAdmissionConfigCommand command) {
		ProgramAdmissionConfig config = configRepository.findById(command.configId()).orElseThrow(
				() -> new ProgramAdmissionConfigNotFoundException(
						"Program admission config not found: " + command.configId()));

		OpenProgramAdmissionUseCaseImpl.requireProgram(command.programId(), programRepository);
		CreateGenerationUseCaseImpl.requirePeriod(command.periodId(), periodRepository);
		CreateGroupUseCaseImpl.requireGeneration(command.targetGenerationId(), generationRepository);

		configRepository.findByProgramIdAndPeriodId(command.programId(), command.periodId())
				.filter(found -> !found.getId().equals(config.getId())).ifPresent(found -> {
					throw new DuplicateProgramAdmissionConfigException(
							"Program admission config already exists for programId=" + command.programId()
									+ ", periodId=" + command.periodId());
				});

		config.updateDetails(command.programId(), command.periodId(), command.targetGenerationId(),
				command.isOffered(), command.maxCandidates(), command.opensAt(), command.closesAt());
		ProgramAdmissionConfig saved = configRepository.save(config);

		return OpenProgramAdmissionUseCaseImpl.toResult(saved);
	}
}
