package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase.GenerationResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGenerationNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Updates an existing {@code Generation}'s FK associations and {@code number},
 * recomputing {@code code}. Applies the same {@code planId}/{@code startPeriodId}
 * existence and {@code (programId, number)} uniqueness rules as creation,
 * except a generation's own current {@code number} never counts as a
 * conflict against itself — same self-update rule as
 * {@code UpdateAcademicPeriodUseCaseImpl}'s {@code (year, periodNumber)}
 * revalidation.
 */
public class UpdateGenerationUseCaseImpl implements UpdateGenerationUseCase {

	private final GenerationRepository generationRepository;

	private final AcademicPlanRepository planRepository;

	private final AcademicPeriodRepository periodRepository;

	public UpdateGenerationUseCaseImpl(GenerationRepository generationRepository, AcademicPlanRepository planRepository,
			AcademicPeriodRepository periodRepository) {
		this.generationRepository = generationRepository;
		this.planRepository = planRepository;
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public GenerationResult updateGeneration(UpdateGenerationCommand command) {
		Generation generation = generationRepository.findById(command.generationId()).orElseThrow(
				() -> new GenerationNotFoundException("Generation not found: " + command.generationId()));

		AcademicPlan plan = CreateGenerationUseCaseImpl.requirePlan(command.planId(), planRepository);
		AcademicPeriod startPeriod = CreateGenerationUseCaseImpl.requirePeriod(command.startPeriodId(), periodRepository);

		UUID programId = plan.getProgramId();
		generationRepository.findByProgramIdAndNumber(programId, command.number())
				.filter(found -> !found.getId().equals(generation.getId())).ifPresent(found -> {
					throw new DuplicateGenerationNumberException(
							"Generation number already in use for this program: " + command.number());
				});

		generation.updateDetails(command.planId(), command.startPeriodId(), programId, command.number(),
				startPeriod.getYear());
		Generation saved = generationRepository.save(generation);

		return CreateGenerationUseCaseImpl.toResult(saved);
	}
}
