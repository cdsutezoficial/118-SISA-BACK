package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicPlan;
import mx.edu.utez.sisa.academic_config.domain.model.Generation;
import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.PlanLevel;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreateGroupUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPlanRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GenerationRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGroupCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.GenerationReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.GroupCodeLevelMismatchException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * {@code generation.getPlanId()}, using {@link AcademicPlan#getLevels()}
 * (the level is needed anyway — its number is what the group's {@code code}
 * must start with, see {@link #requireCodeMatchesLevel}) — reuses the
 * already-existing {@link PlanLevelNotFoundException} (404), no new type, no
 * schema change.
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
		PlanLevel planLevel = requireLevel(plan, command.planLevelId());
		CreateGenerationUseCaseImpl.requirePeriod(command.periodId(), periodRepository);

		// Normalizar ANTES de comprobar el duplicado, no después: la búsqueda es
		// por igualdad sobre `code`, así que comparar contra el valor en
		// minúsculas dejaria pasar un "3a" junto a un "3A" ya guardado, y el
		// conflicto lo detectaria después la restricción única de la tabla, con un
		// 500 en vez de un 409. Ver GroupTextNormalizer. La canonicalización de
		// ceros a la izquierda también ocurre aquí, por el mismo motivo: "03A" y
		// "3A" son la misma clave y las dos comparaciones de aguas abajo tienen
		// que ver el mismo valor.
		String code = GroupTextNormalizer.code(command.code());
		requireCodeMatchesLevel(code, planLevel);
		requireUniqueCode(command.generationId(), code, groupRepository);

		Group group = new Group(command.generationId(), command.periodId(), command.planLevelId(),
				generation.getProgramId(), code, command.maxCapacity(), command.shift());
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

	/**
	/**
	 * Unicidad de {@code (generationId, code)}. Se comprueba en Java además de por
	 * la restricción de la tabla: la restricción es la que resuelve la carrera
	 * entre dos altas simultáneas —imposible de cerrar en Java, porque una
	 * transacción no ve la fila no confirmada de la otra—, pero el mensaje del 409
	 * tiene que ser el del módulo y no el de Hibernate, y eso lo da esta
	 * comprobación.
	 */
	static void requireUniqueCode(UUID generationId, String code, GroupRepository groupRepository) {
		if (groupRepository.findByGenerationIdAndCode(generationId, code).isPresent()) {
			throw new DuplicateGroupCodeException("Group code already in use for this generation: " + code);
		}
	}

	static PlanLevel requireLevel(AcademicPlan plan, UUID planLevelId) {
		if (planLevelId == null) {
			throw new PlanLevelNotFoundException("Plan level not found: null");
		}
		return plan.getLevels().stream().filter(level -> planLevelId.equals(level.getId())).findFirst()
				.orElseThrow(() -> new PlanLevelNotFoundException("Plan level not found: " + planLevelId));
	}

	/** Contrato que comparten el DTO ({@code ^\p{N}+\p{L}$}) y el form. */
	private static final Pattern GROUP_CODE = Pattern.compile("^(\\p{N}+)\\p{L}$");

	/**
	 * La clave del grupo debe <em>describir</em> el nivel elegido: sus dígitos
	 * tienen que ser el número de ese nivel ({@code 3A} para el Nivel 3). Sin
	 * esta regla, elegir Nivel 3 y escribir {@code 5A} creaba un grupo del nivel
	 * 3 con la clave de otro nivel, y {@code 3A} y {@code 03A} abrían dos
	 * espacios de letras distintos para el mismo nivel.
	 *
	 * <p>Los dígitos se comparan como texto después de quitar ceros, no con
	 * {@link Integer#parseInt(String)}: {@code ^\p{N}+$} no limita el número de
	 * dígitos, y un valor largo haría troncar el parseo con un
	 * {@code NumberFormatException} (500) en lugar de responder 400.
	 *
	 * <p>Por HTTP es alcanzable sólo desde un valor que el DTO ya acepta
	 * ({@code 5A} es patrón válido), de ahí que la comprobación viva en el caso
	 * de uso y no en la anotación.
	 */
	static void requireCodeMatchesLevel(String code, PlanLevel planLevel) {
		Matcher matcher = GROUP_CODE.matcher(code == null ? "" : code);
		String digits = matcher.matches() ? matcher.group(1).replaceFirst("^0+", "") : "";
		if (!digits.equals(String.valueOf(planLevel.getLevelNumber()))) {
			throw new GroupCodeLevelMismatchException(
					"Group code '" + code + "' does not describe plan level " + planLevel.getLevelNumber());
		}
	}

	static GroupResult toResult(Group group) {
		return new GroupResult(group.getId(), group.getGenerationId(), group.getPeriodId(), group.getPlanLevelId(),
				group.getProgramId(), group.getCode(), group.getMaxCapacity(), group.getShift(), group.getStatus());
	}
}
