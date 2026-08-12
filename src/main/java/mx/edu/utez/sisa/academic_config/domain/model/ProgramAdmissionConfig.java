package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidProgramAdmissionConfigDataException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ProgramAdmissionConfig aggregate root — a full standalone aggregate, same
 * architectural family as {@link Generation}/{@link AcademicPeriod} (docs:
 * {@code 02-config-academica.md} lines 205-226; plan:
 * {@code docs/plans/2026-07-28-program-admission-config.md}). Owns its own
 * table, repository, and controller. This is the config layer for the future
 * "Admisión" module's ticket-sales window per program — it does NOT belong to
 * the {@code admission} bounded context itself ({@code Candidate},
 * {@code AdmissionPayment}, etc., none of which exist yet); it lives in
 * {@code academic_config} exactly like {@code PaymentConcept} does for
 * Módulo 7 (Pagos).
 *
 * <p>
 * {@code periodId} is the DESTINATION period — the one the accepted
 * candidates will enroll into — NOT the period during which ticket sales run;
 * the sales window is exclusively {@link #opensAt}/{@link #closesAt}, which
 * can span months before {@code periodId} even starts (plan §4).
 *
 * <p>
 * {@code selectionStatus} is ALWAYS created as {@link SelectionStatus#IN_REVIEW}
 * and there is deliberately NO use case in this phase that changes it to
 * {@link SelectionStatus#PUBLISHED} — that transition belongs to
 * {@code PublishAdmissionResultsUseCase}, part of the future {@code admission}
 * bounded context (plan §3/§9, explicit scoping decision). No setter/mutator
 * for {@code selectionStatus} exists on this class for that reason.
 *
 * <p>
 * {@code maxCandidates > 0} and {@code closesAt} strictly after
 * {@code opensAt} are pure invariants of this entity's own fields — no
 * repository access needed — so they are validated here in the constructor/
 * {@link #updateDetails}, mirroring {@link AcademicPeriod}'s
 * {@code validateDateRanges} precedent (as opposed to
 * {@code PaymentConcept}'s {@code maxPerStudent}/{@code maxPerPeriod}
 * independent per-field checks, which stay in the use-case layer).
 * {@code (programId, periodId)} uniqueness, by contrast, DOES need repository
 * access (cross-record), so it stays in
 * {@code OpenProgramAdmissionUseCaseImpl}/{@code UpdateProgramAdmissionConfigUseCaseImpl}
 * — same split as {@code Generation}'s {@code (programId, number)}
 * uniqueness.
 */
@Entity
@Table(name = "program_admission_config")
public class ProgramAdmissionConfig {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(name = "period_id", nullable = false)
	private UUID periodId;

	@Column(name = "target_generation_id", nullable = false)
	private UUID targetGenerationId;

	@Column(name = "is_offered", nullable = false)
	private boolean isOffered;

	@Column(name = "max_candidates", nullable = false)
	private int maxCandidates;

	@Column(name = "opens_at", nullable = false)
	private Instant opensAt;

	@Column(name = "closes_at", nullable = false)
	private Instant closesAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProgramAdmissionConfigStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "selection_status", nullable = false)
	private SelectionStatus selectionStatus;

	protected ProgramAdmissionConfig() {
		// JPA
	}

	/**
	 * @param programId          required — MUST reference an existing {@code AcademicProgram},
	 *                           validated by {@code OpenProgramAdmissionUseCaseImpl}, not here
	 * @param periodId           required — MUST reference an existing {@code AcademicPeriod} (the
	 *                           DESTINATION period), validated by
	 *                           {@code OpenProgramAdmissionUseCaseImpl}, not here
	 * @param targetGenerationId required — MUST reference an existing {@code Generation}, validated
	 *                           by {@code OpenProgramAdmissionUseCaseImpl}, not here
	 * @param isOffered          caller-supplied, equivalent to the current SISA's "ofertada" flag
	 * @param maxCandidates      MUST be greater than 0
	 * @param opensAt            ticket-sales window opening instant
	 * @param closesAt           MUST be strictly after {@code opensAt}
	 */
	public ProgramAdmissionConfig(UUID programId, UUID periodId, UUID targetGenerationId, boolean isOffered,
			int maxCandidates, Instant opensAt, Instant closesAt) {
		validate(maxCandidates, opensAt, closesAt);
		this.programId = programId;
		this.periodId = periodId;
		this.targetGenerationId = targetGenerationId;
		this.isOffered = isOffered;
		this.maxCandidates = maxCandidates;
		this.opensAt = opensAt;
		this.closesAt = closesAt;
		this.status = ProgramAdmissionConfigStatus.OPEN;
		this.selectionStatus = SelectionStatus.IN_REVIEW;
	}

	/**
	 * Updates the catalog fields (PUT {@code /program-admission-configs/{id}}).
	 * {@code status} is deliberately absent — status transitions are the sole
	 * responsibility of {@link #open()}/{@link #close()}, same separation as
	 * {@code Generation#updateDetails}. {@code selectionStatus} is ALSO
	 * deliberately absent and never touched — see class javadoc.
	 * {@code (programId, periodId)} uniqueness (excluding this record's own
	 * row) is revalidated by {@code UpdateProgramAdmissionConfigUseCaseImpl},
	 * not here.
	 */
	public void updateDetails(UUID programId, UUID periodId, UUID targetGenerationId, boolean isOffered,
			int maxCandidates, Instant opensAt, Instant closesAt) {
		validate(maxCandidates, opensAt, closesAt);
		this.programId = programId;
		this.periodId = periodId;
		this.targetGenerationId = targetGenerationId;
		this.isOffered = isOffered;
		this.maxCandidates = maxCandidates;
		this.opensAt = opensAt;
		this.closesAt = closesAt;
	}

	/**
	 * Transitions to {@code OPEN} (simple 2-state toggle — see
	 * {@link ProgramAdmissionConfigStatus}). Idempotent — calling on an
	 * already-{@code OPEN} config is a no-op, same convention as
	 * {@code Generation#activate}. Never touches {@link #selectionStatus}.
	 */
	public void open() {
		this.status = ProgramAdmissionConfigStatus.OPEN;
	}

	/**
	 * Transitions to {@code CLOSED}. Idempotent — calling on an
	 * already-{@code CLOSED} config is a no-op. The record itself is never
	 * deleted. Never touches {@link #selectionStatus}.
	 */
	public void close() {
		this.status = ProgramAdmissionConfigStatus.CLOSED;
	}

	/**
	 * Plan §4/§7: {@code maxCandidates > 0} and {@code closesAt} strictly after
	 * {@code opensAt}, mirroring {@link AcademicPeriod#validateDateRanges}.
	 */
	private static void validate(int maxCandidates, Instant opensAt, Instant closesAt) {
		if (maxCandidates <= 0) {
			throw new InvalidProgramAdmissionConfigDataException(
					"maxCandidates must be greater than 0: " + maxCandidates);
		}
		if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt)) {
			throw new InvalidProgramAdmissionConfigDataException(
					"closesAt must be after opensAt: opensAt=" + opensAt + ", closesAt=" + closesAt);
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getProgramId() {
		return programId;
	}

	public UUID getPeriodId() {
		return periodId;
	}

	public UUID getTargetGenerationId() {
		return targetGenerationId;
	}

	public boolean isOffered() {
		return isOffered;
	}

	public int getMaxCandidates() {
		return maxCandidates;
	}

	public Instant getOpensAt() {
		return opensAt;
	}

	public Instant getClosesAt() {
		return closesAt;
	}

	public ProgramAdmissionConfigStatus getStatus() {
		return status;
	}

	public SelectionStatus getSelectionStatus() {
		return selectionStatus;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ProgramAdmissionConfig that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
