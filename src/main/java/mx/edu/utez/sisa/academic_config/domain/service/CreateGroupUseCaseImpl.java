package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates a {@code Group} (plan: {@code docs/plans/2026-07-20-generation-group.md},
 * "Group — diseño técnico resuelto (2026-07-23)"). Validates its three FKs in
 * the exact order the resolved design specifies:
 * <ol>
 * <li>{@code generationId} against {@link GenerationRepository} — a new
 * {@link GenerationReferenceNotFoundException} (400) if missing, distinct
 * from {@code GenerationNotFoundException} (404, reserved for
 * {@code GET/PUT /generations/{id}}).
 * <li>{@code planLevelId} against the {@link AcademicPlan} resolved via
 * {@code generation.getPlanId()}, using {@link AcademicPlan#hasLevel(UUID)}
 * (already public, already exercised intra-aggregate by
 * {@code UpdateAcademicPlanUseCaseImpl}) — reuses the already-existing
 * {@link PlanLevelNotFoundException} (404), no new type, no schema change.
 * <li>{@code periodId} against {@link AcademicPeriodRepository} — reuses the
 * already-existing {@code PeriodNotFoundException} (400) via
 * {@link CreateGenerationUseCaseImpl#requirePeriod}.
 * </ol>
 * {@code programId} is copied directly from {@code generation.getProgramId()}
 * — simpler than {@link Generation}'s own resolution, since {@code Generation}
 * already denormalizes it from {@code planId}, so no extra
 * {@code AcademicPlanRepository} round trip is needed just for this field
 * (plan's resolved design, step 2 — "simplificación respecto al plan
 * original").
 */
public class CreateGroupUseCaseImpl implements CreateGroupUseCase {

	private final GroupRepository groupRepository;

	private final GenerationRepository generationRepository;

	private final AcademicPlanRepository planRepository;

	private final AcademicPeriodRepository periodRepository;

	public CreateGroupUseCaseImpl(GroupRepository groupRepository, GenerationRepository generationRepository,
			AcademicPlanRepository planRepository, AcademicPeriodRepository periodRepository) {
		this.groupRepository = groupRepository;
		this.generationRepository = generationRepository;
		this.planRepository = planRepository;
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public GroupResult createGroup(CreateGroupCommand command) {
		Generation generation = requireGeneration(command.generationId(), generationRepository);
		AcademicPlan plan = CreateGenerationUseCaseImpl.requirePlan(generation.getPlanId(), planRepository);
		requireLevel(plan, command.planLevelId());
		CreateGenerationUseCaseImpl.requirePeriod(command.periodId(), periodRepository);

		Group group = new Group(command.generationId(), command.periodId(), command.planLevelId(),
				generation.getProgramId(), command.code(), command.maxCapacity(), command.shift());
		Group saved = groupRepository.save(group);

		return toResult(saved);
	}

	static Generation requireGeneration(UUID generationId, GenerationRepository generationRepository) {
		if (generationId == null) {
			throw new GenerationReferenceNotFoundException("Generation not found: null");
		}
		return generationRepository.findById(generationId)
				.orElseThrow(() -> new GenerationReferenceNotFoundException("Generation not found: " + generationId));
	}

	static void requireLevel(AcademicPlan plan, UUID planLevelId) {
		if (planLevelId == null || !plan.hasLevel(planLevelId)) {
			throw new PlanLevelNotFoundException("Plan level not found: " + planLevelId);
		}
	}

	static GroupResult toResult(Group group) {
		return new GroupResult(group.getId(), group.getGenerationId(), group.getPeriodId(), group.getPlanLevelId(),
				group.getProgramId(), group.getCode(), group.getMaxCapacity(), group.getShift(), group.getStatus());
	}
}
