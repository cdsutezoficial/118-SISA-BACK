package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdateGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGroupCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.GroupNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates an existing {@code Group}'s FK associations and catalog fields,
 * applying the same {@code generationId}/{@code planLevelId}/{@code periodId}
 * validation chain as {@link CreateGroupUseCaseImpl} (plan: "Group — diseño
 * técnico resuelto (2026-07-23)"). {@code programId} is re-resolved from the
 * (possibly changed) {@code generationId} rather than left untouched — a
 * {@code Group} can move to a different generation, and its denormalized
 * {@code programId} must always reflect the currently-associated generation,
 * same "re-resolve on every write" rule as {@code UpdateGenerationUseCaseImpl}.
 */
public class UpdateGroupUseCaseImpl implements UpdateGroupUseCase {

	private final GroupRepository groupRepository;

	private final GenerationRepository generationRepository;

	private final AcademicPlanRepository planRepository;

	private final AcademicPeriodRepository periodRepository;

	public UpdateGroupUseCaseImpl(GroupRepository groupRepository, GenerationRepository generationRepository,
			AcademicPlanRepository planRepository, AcademicPeriodRepository periodRepository) {
		this.groupRepository = groupRepository;
		this.generationRepository = generationRepository;
		this.planRepository = planRepository;
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public GroupResult updateGroup(UpdateGroupCommand command) {
		Group group = groupRepository.findById(command.groupId())
				.orElseThrow(() -> new GroupNotFoundException("Group not found: " + command.groupId()));

		Generation generation = CreateGroupUseCaseImpl.requireGeneration(command.generationId(), generationRepository);
		AcademicPlan plan = CreateGenerationUseCaseImpl.requirePlan(generation.getPlanId(), planRepository);
		PlanLevel planLevel = CreateGroupUseCaseImpl.requireLevel(plan, command.planLevelId());
		CreateGenerationUseCaseImpl.requirePeriod(command.periodId(), periodRepository);

		// Normalizar antes de comprobar, por el mismo motivo que en create.
		String code = GroupTextNormalizer.code(command.code());
		CreateGroupUseCaseImpl.requireCodeMatchesLevel(code, planLevel);
		// La unicidad se revalida excluyendo esta misma fila: cambiar de
		// generación puede traer un código que ya existe en la de destino, y
		// quedarse en la misma puede no cambiar nada. Mismo autocambio que en
		// UpdateGenerationUseCaseImpl.
		groupRepository.findByGenerationIdAndCode(command.generationId(), code)
				.filter(found -> !found.getId().equals(group.getId())).ifPresent(found -> {
					throw new DuplicateGroupCodeException("Group code already in use for this generation: " + code);
				});

		group.updateDetails(command.generationId(), command.periodId(), command.planLevelId(),
				generation.getProgramId(), code, command.maxCapacity(), command.shift());
		Group saved = groupRepository.save(group);

		return CreateGroupUseCaseImpl.toResult(saved);
	}
}
