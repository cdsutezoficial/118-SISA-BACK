package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateLevelNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateSubjectCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelHasSubjectsException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelInUseException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.SubjectNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Study plan (Plan de Estudios) aggregate root — the third capability of the
 * {@code academic_config} bounded context (spec: "Academic Plan
 * Management"). Owns the ACTIVE/INACTIVE lifecycle, mirroring
 * {@link AcademicDivision}/{@link AcademicProgram} — there is deliberately no
 * hard-delete operation. Unlike Division/Program, this aggregate also owns a
 * child graph, {@link PlanLevel} → {@link Subject} (design.md — Decision:
 * "Child persistence — JPA composition"): children have no independent
 * repository or controller, and this class is the sole boundary through
 * which they are created, changed, or removed. {@code version} is unique per
 * {@code programId} (not globally) — enforced at the use-case layer via
 * {@code AcademicPlanRepository}, not here.
 */
@Entity
@Table(name = "academic_plan", uniqueConstraints = @UniqueConstraint(columnNames = { "program_id", "version" }))
public class AcademicPlan {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "program_id", nullable = false)
	private UUID programId;

	@Column(nullable = false)
	private String version;

	@Column(name = "validity_period", nullable = false)
	private String validityPeriod;

	@Column(name = "titulation_key", nullable = false)
	private String titulationKey;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "total_levels", nullable = false)
	private int totalLevels;

	@Column(name = "min_passing_grade", nullable = false, precision = 3, scale = 1)
	private BigDecimal minPassingGrade;

	@Column(name = "max_extraordinary_exams_per_period", nullable = false)
	private int maxExtraordinaryExamsPerPeriod;

	@Column(name = "requires_social_service", nullable = false)
	private boolean requiresSocialService;

	@Column(name = "social_service_min_level_id")
	private UUID socialServiceMinLevelId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PlanStatus status;

	@OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("levelNumber ASC")
	private List<PlanLevel> levels = new ArrayList<>();

	protected AcademicPlan() {
		// JPA
	}

	/**
	 * @param socialServiceMinLevelId MUST be {@code null} at creation regardless of
	 *                                {@code requiresSocialService} — no {@link PlanLevel} can exist yet
	 *                                for a plan that does not exist yet (spec: "Rejects
	 *                                requiresSocialService=true with a non-null socialServiceMinLevelId
	 *                                at creation"). That rule, along with programId existence, version
	 *                                uniqueness, and the minPassingGrade range, is enforced by
	 *                                {@code CreateAcademicPlanUseCaseImpl}, not this constructor —
	 *                                mirrors {@code AcademicProgram}'s "dumb entity" precedent.
	 */
	public AcademicPlan(UUID programId, String version, String validityPeriod, String titulationKey,
			LocalDate effectiveFrom, int totalLevels, BigDecimal minPassingGrade, int maxExtraordinaryExamsPerPeriod,
			boolean requiresSocialService, UUID socialServiceMinLevelId) {
		this.programId = programId;
		this.version = version;
		this.validityPeriod = validityPeriod;
		this.titulationKey = titulationKey;
		this.effectiveFrom = effectiveFrom;
		this.totalLevels = totalLevels;
		this.minPassingGrade = minPassingGrade;
		this.maxExtraordinaryExamsPerPeriod = maxExtraordinaryExamsPerPeriod;
		this.requiresSocialService = requiresSocialService;
		this.socialServiceMinLevelId = socialServiceMinLevelId;
		this.status = PlanStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code ACTIVE} (spec: "Reactivate an inactive plan").
	 * Idempotent — calling on an already-{@code ACTIVE} plan is a no-op.
	 */
	public void activate() {
		this.status = PlanStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code INACTIVE} (spec: "Deactivate an active plan").
	 * Idempotent — calling on an already-{@code INACTIVE} plan is a no-op.
	 * The record itself is never deleted.
	 */
	public void deactivate() {
		this.status = PlanStatus.INACTIVE;
	}

	/**
	 * Updates the catalog fields (spec: "Update Academic Plan"). {@code status}
	 * and {@code programId} are deliberately untouched — status transitions
	 * are the sole responsibility of {@link #activate()}/{@link #deactivate()},
	 * and moving a plan to a different program is not supported (spec:
	 * "programId MUST NOT be changeable via update"). The version-uniqueness
	 * and socialServiceMinLevelId-ownership rules are enforced by
	 * {@code UpdateAcademicPlanUseCaseImpl} (using {@link #hasLevel(UUID)}),
	 * not here.
	 */
	public void updateDetails(String version, String validityPeriod, String titulationKey, LocalDate effectiveFrom,
			int totalLevels, BigDecimal minPassingGrade, int maxExtraordinaryExamsPerPeriod,
			boolean requiresSocialService, UUID socialServiceMinLevelId) {
		this.version = version;
		this.validityPeriod = validityPeriod;
		this.titulationKey = titulationKey;
		this.effectiveFrom = effectiveFrom;
		this.totalLevels = totalLevels;
		this.minPassingGrade = minPassingGrade;
		this.maxExtraordinaryExamsPerPeriod = maxExtraordinaryExamsPerPeriod;
		this.requiresSocialService = requiresSocialService;
		this.socialServiceMinLevelId = socialServiceMinLevelId;
	}

	/**
	 * Adds a {@link PlanLevel} to this plan (spec: "Add and Update Plan
	 * Level"). Rejects a {@code levelNumber} already used by another level of
	 * this same plan. The {@code [1, totalLevels]} range check is enforced by
	 * {@code AddPlanLevelUseCaseImpl}, not here.
	 */
	public PlanLevel addLevel(int levelNumber, PlanLevelType type, String description) {
		if (hasLevelNumber(levelNumber, null)) {
			throw new DuplicateLevelNumberException("Level number already used in this plan: " + levelNumber);
		}
		PlanLevel level = new PlanLevel(this, levelNumber, type, description);
		levels.add(level);
		return level;
	}

	/**
	 * Updates an existing {@link PlanLevel} owned by this plan. Rejects an
	 * unknown {@code levelId} or a {@code levelNumber} already used by
	 * another level of this same plan.
	 */
	public void updateLevel(UUID levelId, int levelNumber, PlanLevelType type, String description) {
		PlanLevel level = findLevel(levelId)
				.orElseThrow(() -> new PlanLevelNotFoundException("Plan level not found: " + levelId));
		if (hasLevelNumber(levelNumber, levelId)) {
			throw new DuplicateLevelNumberException("Level number already used in this plan: " + levelNumber);
		}
		level.updateDetails(levelNumber, type, description);
	}

	/**
	 * Removes a {@link PlanLevel} owned by this plan (spec: "Add and Update
	 * Plan Level"). Rejects an unknown {@code levelId}, a level that still
	 * has {@link Subject} children (must be emptied first), or a level
	 * currently referenced by {@link #socialServiceMinLevelId}.
	 */
	public void removeLevel(UUID levelId) {
		PlanLevel level = findLevel(levelId)
				.orElseThrow(() -> new PlanLevelNotFoundException("Plan level not found: " + levelId));
		if (!level.getSubjects().isEmpty()) {
			throw new PlanLevelHasSubjectsException("Plan level still has subjects: " + levelId);
		}
		if (levelId.equals(socialServiceMinLevelId)) {
			throw new PlanLevelInUseException(
					"Plan level is referenced as socialServiceMinLevelId and cannot be removed: " + levelId);
		}
		levels.remove(level);
	}

	/**
	 * Adds a {@link Subject} under the {@link PlanLevel} identified by
	 * {@code levelId} (spec: "Add and Update Subject"). Rejects a
	 * {@code levelId} that does not belong to this plan (including one that
	 * belongs to a different plan entirely — spec: "Rejects planLevelId from
	 * a different plan") and a {@code code} already used by another subject
	 * of this same plan, across any of its levels.
	 */
	public Subject addSubject(UUID levelId, String code, String name, int credits, int weeklyHours,
			int evaluationUnits, int displayOrder, SubjectType type, boolean isRetakeable, UUID classificationId) {
		PlanLevel level = findLevel(levelId)
				.orElseThrow(() -> new PlanLevelNotFoundException("Plan level not found: " + levelId));
		if (hasSubjectCode(code, null)) {
			throw new DuplicateSubjectCodeException("Subject code already used in this plan: " + code);
		}
		Subject subject = new Subject(level, this.id, code, name, credits, weeklyHours, evaluationUnits, displayOrder,
				type, isRetakeable, classificationId);
		level.addSubject(subject);
		return subject;
	}

	/**
	 * Updates an existing {@link Subject} owned by this plan. Rejects an
	 * unknown {@code subjectId} or a {@code code} already used by another
	 * subject of this same plan.
	 */
	public void updateSubject(UUID subjectId, String code, String name, int credits, int weeklyHours,
			int evaluationUnits, int displayOrder, SubjectType type, boolean isRetakeable, UUID classificationId) {
		Subject subject = findSubject(subjectId)
				.orElseThrow(() -> new SubjectNotFoundException("Subject not found: " + subjectId));
		if (hasSubjectCode(code, subjectId)) {
			throw new DuplicateSubjectCodeException("Subject code already used in this plan: " + code);
		}
		subject.updateDetails(code, name, credits, weeklyHours, evaluationUnits, displayOrder, type, isRetakeable,
				classificationId);
	}

	/**
	 * Removes a {@link Subject} owned by this plan from its {@link PlanLevel}.
	 * Rejects an unknown {@code subjectId}.
	 */
	public void removeSubject(UUID subjectId) {
		for (PlanLevel level : levels) {
			Optional<Subject> found = level.getSubjects().stream()
					.filter(subject -> subject.getId() != null && subject.getId().equals(subjectId)).findFirst();
			if (found.isPresent()) {
				level.removeSubject(found.get());
				return;
			}
		}
		throw new SubjectNotFoundException("Subject not found: " + subjectId);
	}

	/**
	 * Queried by {@code UpdateAcademicPlanUseCaseImpl} to validate that a
	 * candidate {@code socialServiceMinLevelId} belongs to this same plan
	 * (spec: "Rejects socialServiceMinLevelId referencing a PlanLevel from a
	 * different plan").
	 */
	public boolean hasLevel(UUID levelId) {
		return findLevel(levelId).isPresent();
	}

	private boolean hasLevelNumber(int levelNumber, UUID excludingLevelId) {
		return levels.stream().anyMatch(level -> level.getLevelNumber() == levelNumber
				&& (excludingLevelId == null || !excludingLevelId.equals(level.getId())));
	}

	private boolean hasSubjectCode(String code, UUID excludingSubjectId) {
		return allSubjects().anyMatch(subject -> subject.getCode().equals(code)
				&& (excludingSubjectId == null || !excludingSubjectId.equals(subject.getId())));
	}

	private Optional<PlanLevel> findLevel(UUID levelId) {
		return levels.stream().filter(level -> level.getId() != null && level.getId().equals(levelId)).findFirst();
	}

	private Optional<Subject> findSubject(UUID subjectId) {
		return allSubjects().filter(subject -> subject.getId() != null && subject.getId().equals(subjectId))
				.findFirst();
	}

	private Stream<Subject> allSubjects() {
		return levels.stream().flatMap(level -> level.getSubjects().stream());
	}

	public UUID getId() {
		return id;
	}

	public UUID getProgramId() {
		return programId;
	}

	public String getVersion() {
		return version;
	}

	public String getValidityPeriod() {
		return validityPeriod;
	}

	public String getTitulationKey() {
		return titulationKey;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public int getTotalLevels() {
		return totalLevels;
	}

	public BigDecimal getMinPassingGrade() {
		return minPassingGrade;
	}

	public int getMaxExtraordinaryExamsPerPeriod() {
		return maxExtraordinaryExamsPerPeriod;
	}

	public boolean isRequiresSocialService() {
		return requiresSocialService;
	}

	public UUID getSocialServiceMinLevelId() {
		return socialServiceMinLevelId;
	}

	public PlanStatus getStatus() {
		return status;
	}

	public List<PlanLevel> getLevels() {
		return Collections.unmodifiableList(levels);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AcademicPlan that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
