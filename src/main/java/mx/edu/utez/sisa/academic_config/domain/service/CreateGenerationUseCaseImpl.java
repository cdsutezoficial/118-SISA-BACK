package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPeriod;
import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGenerationUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGenerationNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates a {@code Generation} (plan: {@code docs/plans/2026-07-20-generation-group.md}).
 * Enforces {@code planId} existence via direct {@link AcademicPlanRepository}
 * injection and {@code startPeriodId} existence via direct
 * {@link AcademicPeriodRepository} injection (design.md — Decision: direct
 * same-module dependencies, not new ports, mirroring
 * {@code CreateAcademicPlanUseCaseImpl}'s use of {@code AcademicProgramRepository}).
 * {@code programId} is resolved from {@code plan.getProgramId()} and
 * denormalized onto the new {@link Generation} (see {@link Generation}'s
 * class javadoc for the "why"). {@code number} uniqueness is enforced within
 * that resolved {@code programId}, NOT within {@code planId} — PO-confirmed
 * 2026-07-20 the counter is per-program, continuous across all of a program's
 * plan versions.
 */
public class CreateGenerationUseCaseImpl implements CreateGenerationUseCase {

	private final GenerationRepository generationRepository;

	private final AcademicPlanRepository planRepository;

	private final AcademicPeriodRepository periodRepository;

	public CreateGenerationUseCaseImpl(GenerationRepository generationRepository, AcademicPlanRepository planRepository,
			AcademicPeriodRepository periodRepository) {
		this.generationRepository = generationRepository;
		this.planRepository = planRepository;
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public GenerationResult createGeneration(CreateGenerationCommand command) {
		AcademicPlan plan = requirePlan(command.planId(), planRepository);
		AcademicPeriod startPeriod = requirePeriod(command.startPeriodId(), periodRepository);

		UUID programId = plan.getProgramId();
		if (generationRepository.findByProgramIdAndNumber(programId, command.number()).isPresent()) {
			throw new DuplicateGenerationNumberException(
					"Generation number already in use for this program: " + command.number());
		}

		Generation generation = new Generation(command.planId(), command.startPeriodId(), programId, command.number(),
				startPeriod.getYear());
		Generation saved = generationRepository.save(generation);

		return toResult(saved);
	}

	static AcademicPlan requirePlan(UUID planId, AcademicPlanRepository planRepository) {
		if (planId == null) {
			throw new PlanNotFoundException("Academic plan not found: null");
		}
		return planRepository.findById(planId)
				.orElseThrow(() -> new PlanNotFoundException("Academic plan not found: " + planId));
	}

	static AcademicPeriod requirePeriod(UUID periodId, AcademicPeriodRepository periodRepository) {
		if (periodId == null) {
			throw new PeriodNotFoundException("Academic period not found: null");
		}
		return periodRepository.findById(periodId)
				.orElseThrow(() -> new PeriodNotFoundException("Academic period not found: " + periodId));
	}

	static GenerationResult toResult(Generation generation) {
		return new GenerationResult(generation.getId(), generation.getPlanId(), generation.getStartPeriodId(),
				generation.getProgramId(), generation.getNumber(), generation.getCode(), generation.getStatus());
	}
}
