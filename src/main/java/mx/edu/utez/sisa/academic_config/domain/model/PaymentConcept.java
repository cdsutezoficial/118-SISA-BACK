package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentConceptDataException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Payment concept catalog aggregate root — Fase 1 of 4 of "Conceptos de
 * Pago" (source: {@code 02-config-academica.md} lines 234-252,
 * {@code docs/plans/2026-07-28-payment-concept.md}). Lives in
 * {@code academic_config} even though the requirement is filed under Módulo
 * 7 (Pagos y Finanzas) — the real Finance bounded context ({@code Payment},
 * {@code PaymentBenefit}, {@code Debt}) needs {@code Student} to exist first
 * and is not part of this phase. {@code PaymentRate} (the concept's pricing
 * history) is Fase 2 and does not exist yet.
 *
 * <p>
 * Unlike {@link SubjectClassification} (its closest structural sibling —
 * simple aggregate, no children, idempotent status toggle), this aggregate
 * has no hard-delete operation and {@code name} is deliberately NOT unique.
 * It does have a {@code code}, which is required and unique
 * case-insensitively.
 *
 * <p>
 * {@code code} exists for two reasons, and the second is the one that makes it
 * a human decision rather than a generated value. First, a concept catalog
 * needs a stable handle for config and support that does not change when the
 * display name is reworded. Second — and this is why the field is captured by
 * hand and never synthesized — {@code payment_benefit.scope_concept_code}
 * ({@code er-08-pagos.md}) references a concept by this string as a free-text
 * foreign key, deliberately not a FK, because scholarships can also point at
 * {@code ADMISSION_FICHA}/{@code INDUCTION_COURSE} concepts owned by other
 * bounded contexts. Somebody writes that configuration by hand, so the code has
 * to be something a person chose and meant; a server-generated one would be
 * regenerated-looking, unauditable, and would break those references silently.
 *
 * <p>
 * {@code levelNumber} is required for {@link PaymentConceptType#PERIODIC_QUOTA}
 * and MUST be null otherwise — see {@link PaymentConceptType} for why the
 * recurring quota is one concept per student level rather than one concept with
 * a flag. The pairing is enforced by {@link #validateLevelNumber} inside this
 * aggregate rather than in the use case layer, because it is a pure invariant
 * between two of this entity's own fields and needs no repository — the same
 * split {@code AcademicPeriod}'s date-range check and {@code GradeScale}'s
 * {@code validateEntries} already make in this module.
 *
 * <p>
 * {@code description}/{@code policies} are mapped as {@code TEXT} columns
 * (rather than the module's usual bare {@code @Column String}, e.g.
 * {@code PlanLevel#description}) because the plan explicitly types them as
 * "Text — rich text", which can exceed the default {@code VARCHAR(255)}
 * Hibernate would otherwise generate.
 */
@Entity
@Table(name = "payment_concept",
		uniqueConstraints = @UniqueConstraint(name = "uk_payment_concept_active_level", columnNames = "active_level"))
public class PaymentConcept {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	/**
	 * Stable handle for configuration and cross-context references, unique
	 * case-insensitively. Captured by hand — see the class Javadoc for why.
	 */
	@Column(nullable = false, unique = true)
	private String code;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(columnDefinition = "TEXT")
	private String policies;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentConceptType type;

	/**
	 * The student level this concept prices, matching
	 * {@code PlanLevel.levelNumber}. Deliberately a plain integer rather than a
	 * {@code planLevelId}: {@code PlanLevel} rows belong to a specific
	 * {@code AcademicPlan} of a specific {@code AcademicProgram}, but a
	 * recurring quota's level is institution-wide — "second semester" is the
	 * same for every career — and coupling it to one plan would make the same
	 * concept unreachable from the other nine.
	 */
	@Column(name = "level_number")
	private Integer levelNumber;

	@Column(nullable = false)
	private boolean isStandalone;

	private Integer maxPerStudent;

	private Integer maxPerPeriod;

	@Column(nullable = false)
	private boolean requiresValidation;

	private LocalDate availableFrom;

	private LocalDate availableUntil;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentConceptStatus status;

	/**
	 * {@code levelNumber} while this concept is an ACTIVE {@code PERIODIC_QUOTA},
	 * {@code null} otherwise. Never set by a caller — always recomputed from
	 * {@code type}, {@code levelNumber} and {@code status}.
	 *
	 * <p>
	 * Exists only to carry one database-level fact that Java cannot enforce: "at
	 * most one active quota per level". The use case checks that with a
	 * read-then-write, which two concurrent requests can both pass, so without
	 * this column the catalog could end up with two live quotas for one level and
	 * the tuition lookup would have two defensible prices for the same student.
	 *
	 * <p>
	 * MySQL 8 has no partial indexes, so {@code UNIQUE (type, status,
	 * level_number)} would be wrong — it would also refuse the second INACTIVE
	 * quota of a level, and history is not optional here. A unique index over a
	 * nullable column works instead: MySQL treats {@code NULL} values as
	 * distinct, so any number of rows may carry {@code NULL} while exactly one
	 * may carry a given level number. Hence a derived column rather than the
	 * three real ones.
	 *
	 * <p>
	 * Maintained in {@link #syncActiveLevel()} at every mutation point rather
	 * than by a database trigger or {@code @Formula}: {@code @Formula} columns
	 * are never created by {@code ddl-auto}, so the schema and the entity would
	 * disagree on a fresh database, which is the case that matters least until it
	 * suddenly matters a lot.
	 *
	 * <p>
	 * The unique constraint is declared on the table rather than on the field so
	 * it gets the stable name {@code uk_payment_concept_active_level}. MySQL
	 * reports a violation by index name and Hibernate would otherwise invent an
	 * unreadable one, leaving
	 * {@code PaymentConceptRepositoryAdapter} unable to tell this collision apart
	 * from any other constraint failure.
	 */
	@Column(name = "active_level", insertable = true, updatable = true)
	private Integer activeLevel;

	private void syncActiveLevel() {
		this.activeLevel = (type == PaymentConceptType.PERIODIC_QUOTA && status == PaymentConceptStatus.ACTIVE)
				? levelNumber
				: null;
	}

	/**
	 * Optional grouping area (frontend-first field, plan
	 * {@code 2026-09-19-payment-concept-extension.md}). Nullable so rows
	 * created before {@link PaymentArea} existed stay valid; when present it
	 * MUST reference an existing {@code PaymentArea}, validated by the use
	 * cases, not here.
	 */
	@Column(name = "area_id")
	private UUID areaId;

	/**
	 * Informative base amount; the per-program/level/period effective price
	 * lives in {@link PaymentRate}. Nullable for rows created before this
	 * field existed.
	 */
	@Column(precision = 12, scale = 2)
	private BigDecimal cost;

	@Column(name = "is_external", nullable = false)
	private boolean isExternal;

	@Column(name = "cost_external", precision = 12, scale = 2)
	private BigDecimal costExternal;

	@Column(name = "is_accumulable", nullable = false)
	private boolean isAccumulable;

	@Column(name = "is_multiconcept", nullable = false)
	private boolean isMulticoncept;

	@Column(name = "quota_limit")
	private Integer quotaLimit;

	@ElementCollection
	@CollectionTable(name = "payment_concept_linked_concept", joinColumns = @JoinColumn(name = "concept_id"))
	@Column(name = "linked_concept_id", nullable = false)
	private List<UUID> linkedConceptIds = new ArrayList<>();

	protected PaymentConcept() {
		// JPA
	}

	/**
	 * Convenience constructor for the catalog fields alone (still used by the
	 * existing test suite). Delegates with empty/null for every extension
	 * field. {@code isTuition} is absent on purpose: it was replaced by
	 * {@link PaymentConceptType#PERIODIC_QUOTA} plus {@code levelNumber}.
	 */
	public PaymentConcept(String name, String code, String description, String policies, PaymentConceptType type,
			Integer levelNumber, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil) {
		this(name, code, description, policies, type, levelNumber, isStandalone, maxPerStudent, maxPerPeriod,
				requiresValidation, availableFrom, availableUntil, null, null, false, null, false, false, null,
				List.of());
	}

	public PaymentConcept(String name, String code, String description, String policies, PaymentConceptType type,
			Integer levelNumber, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil, UUID areaId,
			BigDecimal cost, boolean isExternal, BigDecimal costExternal, boolean isAccumulable,
			boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds) {
		validateLevelNumber(type, levelNumber);
		this.name = name;
		this.code = code;
		this.description = description;
		this.policies = policies;
		this.type = type;
		this.levelNumber = levelNumber;
		this.isStandalone = isStandalone;
		this.maxPerStudent = maxPerStudent;
		this.maxPerPeriod = maxPerPeriod;
		this.requiresValidation = requiresValidation;
		this.availableFrom = availableFrom;
		this.availableUntil = availableUntil;
		this.areaId = areaId;
		this.cost = cost;
		this.isExternal = isExternal;
		this.costExternal = costExternal;
		this.isAccumulable = isAccumulable;
		this.isMulticoncept = isMulticoncept;
		this.quotaLimit = quotaLimit;
		this.linkedConceptIds = linkedConceptIds == null ? new ArrayList<>() : new ArrayList<>(linkedConceptIds);
		this.status = PaymentConceptStatus.ACTIVE;
		syncActiveLevel();
	}

	/**
	 * The bidirectional {@code type}/{@code levelNumber} pairing: a recurring
	 * quota must name the level it prices, and nothing else may. Enforced here
	 * rather than in the use cases because it needs no repository access, so
	 * every path that builds or mutates this entity gets it — including tests
	 * and any future caller.
	 */
	private static void validateLevelNumber(PaymentConceptType type, Integer levelNumber) {
		if (type == null) {
			throw new InvalidPaymentConceptDataException("type is required");
		}
		if (type.requiresLevelNumber()) {
			if (levelNumber == null) {
				throw new InvalidPaymentConceptDataException(
						"levelNumber is required for a PERIODIC_QUOTA concept: type=" + type);
			}
			if (levelNumber < 1) {
				throw new InvalidPaymentConceptDataException(
						"levelNumber must be greater than zero: " + levelNumber);
			}
		} else if (levelNumber != null) {
			throw new InvalidPaymentConceptDataException(
					"levelNumber only applies to a PERIODIC_QUOTA concept, but type=" + type);
		}
	}

	/**
	 * Updates the catalog fields (Update use case). {@code status} is
	 * deliberately absent: status transitions are the sole responsibility of
	 * {@code ChangePaymentConceptStatusUseCase}, same separation as
	 * {@code SubjectClassification#updateDetails}.
	 */
	public void updateDetails(String name, String code, String description, String policies, PaymentConceptType type,
			Integer levelNumber, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil) {
		updateDetails(name, code, description, policies, type, levelNumber, isStandalone, maxPerStudent,
				maxPerPeriod, requiresValidation, availableFrom, availableUntil, this.areaId, this.cost, this.isExternal,
				this.costExternal, this.isAccumulable, this.isMulticoncept, this.quotaLimit, this.linkedConceptIds);
	}

	/**
	 * Updates the catalog fields including the extension fields added by
	 * {@code 2026-09-19-payment-concept-extension.md}. {@code status} is
	 * deliberately absent. The collections are mutated in place (never
	 * re-assigned) so Hibernate tracks the change on the managed entity.
	 */
	public void updateDetails(String name, String code, String description, String policies, PaymentConceptType type,
			Integer levelNumber, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil, UUID areaId,
			BigDecimal cost, boolean isExternal, BigDecimal costExternal, boolean isAccumulable,
			boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds) {
		validateLevelNumber(type, levelNumber);
		this.name = name;
		this.code = code;
		this.description = description;
		this.policies = policies;
		this.type = type;
		this.levelNumber = levelNumber;
		this.isStandalone = isStandalone;
		this.maxPerStudent = maxPerStudent;
		this.maxPerPeriod = maxPerPeriod;
		this.requiresValidation = requiresValidation;
		this.availableFrom = availableFrom;
		this.availableUntil = availableUntil;
		this.areaId = areaId;
		this.cost = cost;
		this.isExternal = isExternal;
		this.costExternal = costExternal;
		this.isAccumulable = isAccumulable;
		this.isMulticoncept = isMulticoncept;
		this.quotaLimit = quotaLimit;
		this.linkedConceptIds.clear();
		if (linkedConceptIds != null) {
			this.linkedConceptIds.addAll(linkedConceptIds);
		}
		// Recomputed, not adjusted: a retype ENROLLMENT -> PERIODIC_QUOTA has to
		// claim the level and PERIODIC_QUOTA -> ENROLLMENT has to release it, and
		// reading the old value to decide which would get one of the two wrong.
		syncActiveLevel();
	}

	/**
	 * Transitions to {@code ACTIVE}. Idempotent — calling on an
	 * already-{@code ACTIVE} concept is a no-op, same convention as
	 * {@code SubjectClassification#activate}.
	 */
	public void activate() {
		this.status = PaymentConceptStatus.ACTIVE;
		syncActiveLevel();
	}

	/**
	 * Transitions to {@code INACTIVE}. Idempotent — calling on an
	 * already-{@code INACTIVE} concept is a no-op. The record itself is never
	 * deleted, same convention as {@code SubjectClassification#deactivate}.
	 *
	 * <p>
	 * Releasing the level is what lets another concept take it, so this is also
	 * the escape hatch for a catalog that already holds a duplicate: deactivate
	 * one of them and the unique index will accept the replacement.
	 */
	public void deactivate() {
		this.status = PaymentConceptStatus.INACTIVE;
		syncActiveLevel();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public String getPolicies() {
		return policies;
	}

	public PaymentConceptType getType() {
		return type;
	}

	public Integer getLevelNumber() {
		return levelNumber;
	}

	public boolean isStandalone() {
		return isStandalone;
	}

	public Integer getMaxPerStudent() {
		return maxPerStudent;
	}

	public Integer getMaxPerPeriod() {
		return maxPerPeriod;
	}

	public boolean isRequiresValidation() {
		return requiresValidation;
	}

	public LocalDate getAvailableFrom() {
		return availableFrom;
	}

	public LocalDate getAvailableUntil() {
		return availableUntil;
	}

	public PaymentConceptStatus getStatus() {
		return status;
	}

	public UUID getAreaId() {
		return areaId;
	}

	public BigDecimal getCost() {
		return cost;
	}

	public boolean isExternal() {
		return isExternal;
	}

	public BigDecimal getCostExternal() {
		return costExternal;
	}

	public boolean isAccumulable() {
		return isAccumulable;
	}

	public boolean isMulticoncept() {
		return isMulticoncept;
	}

	public Integer getQuotaLimit() {
		return quotaLimit;
	}

	public List<UUID> getLinkedConceptIds() {
		return linkedConceptIds;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PaymentConcept that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
