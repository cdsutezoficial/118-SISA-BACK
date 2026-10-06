package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase.GroupResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupsBulkUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupsBulkUseCase.CreateGroupsBulkCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupsBulkUseCase.CreateGroupsBulkResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.PreviewGroupCodesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.PreviewGroupCodesUseCase.PreviewGroupCodesQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.PreviewGroupCodesUseCase.PreviewGroupCodesResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.NotEnoughGroupCodesException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implements both the bulk creation and its preview, because they share the
 * whole reference-resolution prologue: same generation lookup, same plan, same
 * level, same {@code GroupCodeSequence}. Splitting them into two classes would
 * duplicate that prologue and give the preview a chance of drifting from what
 * the create actually does — which is precisely the bug the preview exists to
 * prevent.
 *
 * <p>
 * The whole batch is one {@code @Transactional} method, so a failure on the
 * 7th of 20 groups leaves none of them persisted. That is the whole point of
 * the endpoint: 20 separate POSTs from the UI would leave 6 orphans behind if
 * the 7th hit a duplicate.
 *
 * <p>
 * Concurrency: two simultaneous bulk requests for the same generation both read
 * the same set of used codes and both allocate "3A". The Java-level duplicate
 * check cannot see the other transaction's uncommitted row, so correctness
 * rests on the {@code uk_academic_groups_generation_code} constraint — with the
 * caveat, documented on {@code Group}, that
 * {@code spring.jpa.hibernate.ddl-auto=update} does not add that constraint to
 * a table that already exists. The {@link DataIntegrityViolationException} catch
 * below is what turns that residual race into a 409 instead of a 500. It is a
 * last line of defence, not the primary mechanism: without the constraint, two
 * concurrent batches would both succeed and leave duplicate codes.
 */
public class CreateGroupsBulkUseCaseImpl
		implements CreateGroupsBulkUseCase, PreviewGroupCodesUseCase {

	private final GroupRepository groupRepository;

	private final GenerationRepository generationRepository;

	private final AcademicPlanRepository planRepository;

	private final AcademicPeriodRepository periodRepository;

	public CreateGroupsBulkUseCaseImpl(GroupRepository groupRepository, GenerationRepository generationRepository,
			AcademicPlanRepository planRepository, AcademicPeriodRepository periodRepository) {
		this.groupRepository = groupRepository;
		this.generationRepository = generationRepository;
		this.planRepository = planRepository;
		this.periodRepository = periodRepository;
	}

	@Override
	@Transactional
	public CreateGroupsBulkResult createGroupsBulk(CreateGroupsBulkCommand command) {
		Generation generation = CreateGroupUseCaseImpl.requireGeneration(command.generationId(), generationRepository);
		AcademicPlan plan = CreateGenerationUseCaseImpl.requirePlan(generation.getPlanId(), planRepository);
		CreateGroupUseCaseImpl.requireLevel(plan, command.planLevelId());
		CreateGenerationUseCaseImpl.requirePeriod(command.periodId(), periodRepository);

		String levelPrefix = resolveLevelPrefix(plan, command.planLevelId());
		List<String> codes = GroupCodeSequence.nextCodes(levelPrefix,
				groupRepository.findCodesByGenerationId(command.generationId()), command.quantity());

		List<GroupResult> created = new ArrayList<>(codes.size());
		try {
			for (String code : codes) {
				Group group = new Group(command.generationId(), command.periodId(), command.planLevelId(),
						generation.getProgramId(), code, command.maxCapacity(), command.shift());
				// `save` (not `saveAll`) so a mid-batch failure names the row that
				// lost the race; the transaction still unwinds the whole batch.
				created.add(CreateGroupUseCaseImpl.toResult(groupRepository.save(group)));
			}
		} catch (DataIntegrityViolationException ex) {
			// Another transaction took one of these letters between the read above
			// and this insert. Rethrown as the module's own conflict so the web
			// layer answers 409 instead of leaking a 500 — see the class javadoc
			// for why this is a safety net and not the main mechanism.
			throw new NotEnoughGroupCodesException(
					"Concurrent bulk creation claimed one of the codes for generation " + command.generationId());
		}
		return new CreateGroupsBulkResult(created);
	}

	@Override
	@Transactional(readOnly = true)
	public PreviewGroupCodesResult previewGroupCodes(PreviewGroupCodesQuery query) {
		Generation generation = CreateGroupUseCaseImpl.requireGeneration(query.generationId(), generationRepository);
		AcademicPlan plan = CreateGenerationUseCaseImpl.requirePlan(generation.getPlanId(), planRepository);
		CreateGroupUseCaseImpl.requireLevel(plan, query.planLevelId());

		String levelPrefix = resolveLevelPrefix(plan, query.planLevelId());
		return new PreviewGroupCodesResult(levelPrefix, GroupCodeSequence.nextCodes(levelPrefix,
				groupRepository.findCodesByGenerationId(query.generationId()), query.quantity()));
	}

	/**
	 * The numeric prefix of every generated code: the level's own number. Reads
	 * {@code getLevels()} rather than a new {@code findLevel} on
	 * {@code AcademicPlan} because that one is private and the list is already
	 * in memory — the level was just validated by {@code requireLevel}, so the
	 * lookup cannot miss.
	 */
	private static String resolveLevelPrefix(AcademicPlan plan, UUID planLevelId) {
		// `PlanLevelNotFoundException`, no `NotEnoughGroupCodesException`: aunque la
		// rama es inalcanzable (requireLevel ya validó), un nivel que no exista es
		// un problema de referencia, no de faltan letras.
		PlanLevel level = plan.getLevels().stream().filter(l -> l.getId().equals(planLevelId)).findFirst()
				.orElseThrow(() -> new PlanLevelNotFoundException("Plan level not found: " + planLevelId));
		return String.valueOf(level.getLevelNumber());
	}
}