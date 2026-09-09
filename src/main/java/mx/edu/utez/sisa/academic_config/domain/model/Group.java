package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.Objects;
import java.util.UUID;

/**
 * Group (grupo) aggregate root — a full standalone aggregate, same
 * architectural family as {@link Generation}/{@link AcademicPeriod} (docs:
 * {@code 02-config-academica.md} lines 183-201; plan:
 * {@code docs/plans/2026-07-20-generation-group.md}, "Group — diseño técnico
 * resuelto (2026-07-23)"). Owns its own table, repository, and controller.
 *
 * <p>
 * Table name is deliberately {@code "academic_groups"}, not {@code "group"}
 * or {@code "groups"} — both collide with SQL syntax across target engines
 * ({@code GROUP} in H2/ANSI SQL, and {@code groups} in MySQL 8), so the
 * physical table name is prefixed instead of relying on quoting.
 *
 * <p>
 * {@code programId} is a denormalized copy of {@code generationId}'s owning
 * {@code Generation.programId} (plan §"Group — diseño técnico resuelto":
 * "{@code Group.programId} se copia directo de
 * {@code generation.getProgramId()}" — simpler than {@link Generation}'s own
 * resolution, since {@code Generation} already denormalizes {@code programId}
 * from its {@code planId}, so no {@code AcademicPlanRepository} round trip is
 * needed just for this field).
 *
 * <p>
 * {@code planLevelId} is validated cross-aggregate via
 * {@code AcademicPlan.hasLevel(UUID)} (already public, already used
 * intra-aggregate by {@code UpdateAcademicPlanUseCaseImpl} for
 * {@code socialServiceMinLevelId}) — reused here without any new method or
 * schema change, resolving the plan's one previously-open technical question.
 *
 * <p>
 * Unlike {@link Generation}'s {@code code} (computed, unique per program),
 * {@code Group.code} (e.g. "3A", "3B") is caller-supplied and carries NO
 * uniqueness rule in this slice — not specified by the plan's resolved
 * design, so none is invented here.
 */
@Entity
@Table(name = "academic_groups")
public class Group {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "generation_id", nullable = false)
	private UUID generationId;

	@Column(name = "period_id", nullable = false)
	private UUID periodId;

	@Column(name = "plan_level_id", nullable = false)
	private UUID planLevelId;

	/**
	 * Denormalized from {@code Generation.programId} — see class javadoc.
	 */
	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(nullable = false)
	private String code;

	@Column(name = "max_capacity", nullable = false)
	private int maxCapacity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Shift shift;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GroupStatus status;

	protected Group() {
		// JPA
	}

	/**
	 * @param generationId required — MUST reference an existing {@code Generation}, validated by
	 *                     {@code CreateGroupUseCaseImpl}, not here
	 * @param periodId     required — MUST reference an existing {@code AcademicPeriod}, validated by
	 *                     {@code CreateGroupUseCaseImpl}, not here
	 * @param planLevelId  required — MUST belong to the {@code AcademicPlan} that
	 *                     {@code generationId}'s generation was opened against
	 *                     ({@code AcademicPlan.hasLevel(UUID)}), validated by
	 *                     {@code CreateGroupUseCaseImpl}, not here
	 * @param programId    resolved from {@code generationId}'s owning {@code Generation.programId} by
	 *                     the use case (see class javadoc) — never accepted as caller input
	 * @param code         caller-supplied, e.g. "3A" — no uniqueness rule in this slice
	 * @param maxCapacity  caller-supplied, no range validation specified by the resolved design
	 * @param shift        {@link Shift#MORNING}, {@link Shift#AFTERNOON}, or {@link Shift#MIXED}
	 */
	public Group(UUID generationId, UUID periodId, UUID planLevelId, UUID programId, String code, int maxCapacity,
			Shift shift) {
		this.generationId = generationId;
		this.periodId = periodId;
		this.planLevelId = planLevelId;
		this.programId = programId;
		this.code = code;
		this.maxCapacity = maxCapacity;
		this.shift = shift;
		this.status = GroupStatus.OPEN;
	}

	/**
	 * Updates the FK associations and catalog fields (PUT
	 * {@code /groups/{id}}). {@code status} is deliberately absent — status
	 * transitions are the sole responsibility of {@link #open()}/
	 * {@link #close()}, same separation as {@code Generation#updateDetails}.
	 * All FK/level revalidation is performed by
	 * {@code UpdateGroupUseCaseImpl}, not here.
	 */
	public void updateDetails(UUID generationId, UUID periodId, UUID planLevelId, UUID programId, String code,
			int maxCapacity, Shift shift) {
		this.generationId = generationId;
		this.periodId = periodId;
		this.planLevelId = planLevelId;
		this.programId = programId;
		this.code = code;
		this.maxCapacity = maxCapacity;
		this.shift = shift;
	}

	/**
	 * Transitions to {@code OPEN} (simple 2-state toggle — see
	 * {@link GroupStatus}). Idempotent — calling on an already-{@code OPEN}
	 * group is a no-op, same convention as {@code Generation#activate}.
	 */
	public void open() {
		this.status = GroupStatus.OPEN;
	}

	/**
	 * Transitions to {@code CLOSED}. Idempotent — calling on an
	 * already-{@code CLOSED} group is a no-op. The record itself is never
	 * deleted.
	 */
	public void close() {
		this.status = GroupStatus.CLOSED;
	}

	public UUID getId() {
		return id;
	}

	public UUID getGenerationId() {
		return generationId;
	}

	public UUID getPeriodId() {
		return periodId;
	}

	public UUID getPlanLevelId() {
		return planLevelId;
	}

	public UUID getProgramId() {
		return programId;
	}

	public String getCode() {
		return code;
	}

	public int getMaxCapacity() {
		return maxCapacity;
	}

	public Shift getShift() {
		return shift;
	}

	public GroupStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Group that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
