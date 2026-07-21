package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Generation (cohorte) aggregate root — a full standalone aggregate, same
 * architectural family as {@link AcademicPeriod}/{@code SubjectClassification}/
 * {@link AcademicDivision} (docs: {@code 02-config-academica.md} lines
 * 170-182; plan: {@code docs/plans/2026-07-20-generation-group.md}). Owns its
 * own table, repository, and controller.
 *
 * <p>
 * {@code programId} is a denormalized copy of {@code planId}'s owning
 * {@code AcademicPlan.programId} (design decision, plan §Execution Log:
 * resolved via direct {@code AcademicPlanRepository} lookup at use-case time,
 * same as {@code CreateAcademicProgramUseCaseImpl} resolving
 * {@code divisionId} — but ALSO copied onto this entity, mirroring the
 * documented precedent for {@code Group.programId} "denormalizado para
 * consultas": {@code GET /generations} takes a {@code programId} filter, and
 * without this column that filter would require a cross-table join that no
 * other {@code search} query in this module performs — every existing
 * {@code JpaRepository#search} filters on plain columns of the queried
 * entity itself (see {@code AcademicPlanJpaRepository#search}'s
 * {@code programId} filter, which works only because {@code programId} is a
 * direct column of {@code AcademicPlan}).
 *
 * <p>
 * {@code number} is a per-program sequential counter that NEVER resets and
 * NEVER repeats within the same {@code programId} (PO-confirmed 2026-07-20 —
 * a program CAN open more than one generation in the same calendar year, so
 * there is deliberately NO uniqueness on {@code (programId, year)}, only on
 * {@code (programId, number)}) — enforced by
 * {@code CreateGenerationUseCaseImpl}/{@code UpdateGenerationUseCaseImpl} via
 * {@code GenerationRepository}, not here (same split as
 * {@code AcademicPlan}'s {@code version} uniqueness).
 *
 * <p>
 * {@code code} is computed, never caller-supplied, format
 * {@code "{year of startPeriod}-{number}"} (e.g. {@code "2026-7"}) — the year
 * is purely informational (when the generation started), not part of any
 * reset rule.
 */
@Entity
@Table(name = "generation")
public class Generation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "plan_id", nullable = false)
	private UUID planId;

	@Column(name = "start_period_id", nullable = false)
	private UUID startPeriodId;

	/**
	 * Denormalized from {@code AcademicPlan.programId} — see class javadoc.
	 */
	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(nullable = false)
	private int number;

	@Column(nullable = false)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GenerationStatus status;

	protected Generation() {
		// JPA
	}

	/**
	 * @param planId          required — MUST reference an existing {@code AcademicPlan}, validated by
	 *                        {@code CreateGenerationUseCaseImpl}, not here
	 * @param startPeriodId   required — MUST reference an existing {@code AcademicPeriod}, validated by
	 *                        {@code CreateGenerationUseCaseImpl}, not here
	 * @param programId       resolved from {@code planId}'s owning {@code AcademicPlan.programId} by
	 *                        the use case (see class javadoc)
	 * @param number          the per-program sequential counter; uniqueness within {@code programId}
	 *                        is enforced by {@code CreateGenerationUseCaseImpl}, not here
	 * @param startPeriodYear {@code startPeriodId}'s {@code AcademicPeriod.year}, resolved by the use
	 *                        case and used only to compute {@link #code} — not stored as its own field
	 */
	public Generation(UUID planId, UUID startPeriodId, UUID programId, int number, int startPeriodYear) {
		this.planId = planId;
		this.startPeriodId = startPeriodId;
		this.programId = programId;
		this.number = number;
		this.code = computeCode(startPeriodYear, number);
		this.status = GenerationStatus.ACTIVE;
	}

	/**
	 * Updates the FK associations and {@code number} (PUT
	 * {@code /generations/{id}}), recomputing {@link #code}. {@code status} is
	 * deliberately absent — status transitions are the sole responsibility of
	 * {@link #activate()}/{@link #finish()}, same separation as
	 * {@code AcademicProgram#updateDetails}. {@code (programId, number)}
	 * uniqueness (excluding this record's own row) is revalidated by
	 * {@code UpdateGenerationUseCaseImpl}, not here.
	 */
	public void updateDetails(UUID planId, UUID startPeriodId, UUID programId, int number, int startPeriodYear) {
		this.planId = planId;
		this.startPeriodId = startPeriodId;
		this.programId = programId;
		this.number = number;
		this.code = computeCode(startPeriodYear, number);
	}

	/**
	 * Transitions to {@code ACTIVE} (simple 2-state toggle — see
	 * {@link GenerationStatus}). Idempotent — calling on an already-{@code ACTIVE}
	 * generation is a no-op, same convention as {@code AcademicDivision#activate}.
	 */
	public void activate() {
		this.status = GenerationStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code FINISHED}. Idempotent — calling on an
	 * already-{@code FINISHED} generation is a no-op. The record itself is
	 * never deleted.
	 */
	public void finish() {
		this.status = GenerationStatus.FINISHED;
	}

	private static String computeCode(int startPeriodYear, int number) {
		return startPeriodYear + "-" + number;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPlanId() {
		return planId;
	}

	public UUID getStartPeriodId() {
		return startPeriodId;
	}

	public UUID getProgramId() {
		return programId;
	}

	public int getNumber() {
		return number;
	}

	public String getCode() {
		return code;
	}

	public GenerationStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Generation that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
