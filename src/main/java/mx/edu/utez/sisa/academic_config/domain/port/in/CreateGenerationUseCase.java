package mx.edu.utez.sisa.academic_config.domain.port.in;

import mx.edu.utez.sisa.academic_config.domain.model.GenerationStatus;

import java.util.UUID;

/**
 * Creates a {@code Generation} (plan: {@code docs/plans/2026-07-20-generation-group.md}).
 * Role authorization (ADMIN or SERVICIOS_ESCOLARES) is enforced by
 * {@code SecurityFilterConfig}, not here — same convention as
 * {@code CreateAcademicPeriodUseCase}. {@code code} is NEVER accepted from
 * the caller — it is computed server-side from {@code startPeriodId}'s year
 * and {@code number}. Always defaults {@code status} to
 * {@link GenerationStatus#ACTIVE}.
 */
public interface CreateGenerationUseCase {

	GenerationResult createGeneration(CreateGenerationCommand command);

	/**
	 * @param planId        required — MUST reference an existing {@code AcademicPlan}
	 * @param startPeriodId required — MUST reference an existing {@code AcademicPeriod}
	 * @param number        MUST be unique within the program that {@code planId} belongs to (plan §3 —
	 *                       PO-confirmed 2026-07-20: NOT scoped by calendar year, a program can open
	 *                       more than one generation in the same year)
	 */
	record CreateGenerationCommand(UUID planId, UUID startPeriodId, int number) {
	}

	record GenerationResult(UUID id, UUID planId, UUID startPeriodId, UUID programId, int number, String code,
			GenerationStatus status) {
	}
}
