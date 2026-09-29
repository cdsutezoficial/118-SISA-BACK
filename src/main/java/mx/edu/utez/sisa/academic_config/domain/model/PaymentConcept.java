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
 * has no {@code code} field at all, so {@code name} is deliberately NOT
 * unique and there is no secondary unique business key either (plan section
 * 4 — the domain doc does not document a uniqueness constraint for this
 * catalog).
 *
 * <p>
 * {@code description}/{@code policies} are mapped as {@code TEXT} columns
 * (rather than the module's usual bare {@code @Column String}, e.g.
 * {@code PlanLevel#description}) because the plan explicitly types them as
 * "Text — rich text", which can exceed the default {@code VARCHAR(255)}
 * Hibernate would otherwise generate.
 */
@Entity
@Table(name = "payment_concept")
public class PaymentConcept {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(columnDefinition = "TEXT")
	private String policies;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentConceptType type;

	@Column(nullable = false)
	private boolean isTuition;

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

	@ElementCollection
	@CollectionTable(name = "payment_concept_program", joinColumns = @JoinColumn(name = "concept_id"))
	@Column(name = "program_id", nullable = false)
	private List<UUID> programIds = new ArrayList<>();

	protected PaymentConcept() {
		// JPA
	}

	/**
	 * Backward-compatible convenience constructor for the original 11 fields
	 * (still used by the existing test suite). Delegates with empty/null for
	 * every field introduced by the
	 * {@code 2026-09-19-payment-concept-extension.md} plan.
	 */
	public PaymentConcept(String name, String description, String policies, PaymentConceptType type,
			boolean isTuition, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil) {
		this(name, description, policies, type, isTuition, isStandalone, maxPerStudent, maxPerPeriod,
				requiresValidation, availableFrom, availableUntil, null, null, false, null, false, false, null,
				List.of(), List.of());
	}

	public PaymentConcept(String name, String description, String policies, PaymentConceptType type,
			boolean isTuition, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil, UUID areaId,
			BigDecimal cost, boolean isExternal, BigDecimal costExternal, boolean isAccumulable,
			boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds, List<UUID> programIds) {
		this.name = name;
		this.description = description;
		this.policies = policies;
		this.type = type;
		this.isTuition = isTuition;
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
		this.programIds = programIds == null ? new ArrayList<>() : new ArrayList<>(programIds);
		this.status = PaymentConceptStatus.ACTIVE;
	}

	/**
	 * Updates the catalog fields (Update use case). {@code status} is
	 * deliberately absent: status transitions are the sole responsibility of
	 * {@code ChangePaymentConceptStatusUseCase}, same separation as
	 * {@code SubjectClassification#updateDetails}.
	 */
	public void updateDetails(String name, String description, String policies, PaymentConceptType type,
			boolean isTuition, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil) {
		updateDetails(name, description, policies, type, isTuition, isStandalone, maxPerStudent, maxPerPeriod,
				requiresValidation, availableFrom, availableUntil, this.areaId, this.cost, this.isExternal,
				this.costExternal, this.isAccumulable, this.isMulticoncept, this.quotaLimit, this.linkedConceptIds,
				this.programIds);
	}

	/**
	 * Updates the catalog fields including the extension fields added by
	 * {@code 2026-09-19-payment-concept-extension.md}. {@code status} is
	 * deliberately absent. The collections are mutated in place (never
	 * re-assigned) so Hibernate tracks the change on the managed entity.
	 */
	public void updateDetails(String name, String description, String policies, PaymentConceptType type,
			boolean isTuition, boolean isStandalone, Integer maxPerStudent, Integer maxPerPeriod,
			boolean requiresValidation, LocalDate availableFrom, LocalDate availableUntil, UUID areaId,
			BigDecimal cost, boolean isExternal, BigDecimal costExternal, boolean isAccumulable,
			boolean isMulticoncept, Integer quotaLimit, List<UUID> linkedConceptIds, List<UUID> programIds) {
		this.name = name;
		this.description = description;
		this.policies = policies;
		this.type = type;
		this.isTuition = isTuition;
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
		this.programIds.clear();
		if (programIds != null) {
			this.programIds.addAll(programIds);
		}
	}

	/**
	 * Transitions to {@code ACTIVE}. Idempotent — calling on an
	 * already-{@code ACTIVE} concept is a no-op, same convention as
	 * {@code SubjectClassification#activate}.
	 */
	public void activate() {
		this.status = PaymentConceptStatus.ACTIVE;
	}

	/**
	 * Transitions to {@code INACTIVE}. Idempotent — calling on an
	 * already-{@code INACTIVE} concept is a no-op. The record itself is never
	 * deleted, same convention as {@code SubjectClassification#deactivate}.
	 */
	public void deactivate() {
		this.status = PaymentConceptStatus.INACTIVE;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
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

	public boolean isTuition() {
		return isTuition;
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

	public List<UUID> getProgramIds() {
		return programIds;
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
