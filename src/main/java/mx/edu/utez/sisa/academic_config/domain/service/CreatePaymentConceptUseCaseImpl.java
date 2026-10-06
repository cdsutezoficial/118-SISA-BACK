package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentConceptCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentQuotaLevelException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentConceptDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptReferenceNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Creates a {@code PaymentConcept} catalog entry (plan section 5 —
 * {@code CreatePaymentConceptUseCase}, the exact name already documented in
 * {@code 02-config-academica.md} line 299). Enforces the
 * {@code maxPerStudent}/{@code maxPerPeriod} &gt; 0 and
 * {@code availableFrom <= availableUntil} range rules (plan section 4 —
 * inferred, marked for correction).
 *
 * <p>
 * The extension fields ({@code areaId}, {@code cost}, {@code costExternal},
 * {@code isExternal}, {@code isAccumulable}, {@code isMulticoncept},
 * {@code quotaLimit}, {@code linkedConceptIds}) are validated here (plan
 * {@code 2026-09-19-payment-concept-extension.md} §3): scalar rules raise
 * {@link InvalidPaymentConceptDataException} and cross-aggregate references
 * ({@code PaymentArea}, {@code PaymentConcept}) raise
 * {@link PaymentConceptReferenceNotFoundException} — same precedent as
 * {@code CreateAcademicProgramUseCaseImpl} validating {@code divisionId}
 * against {@code AcademicDivision}.
 *
 * <p>
 * The {@code type}/{@code levelNumber} pairing is checked inside
 * {@link PaymentConcept} itself (pure two-field invariant, no repository), and
 * "at most one active recurring quota per level" is checked here because it
 * spans records.
 *
 * <p>
 * Rates are reconciled in the same transaction, right after the concept is
 * persisted. For a {@code PERIODIC_QUOTA} this is mandatory rather than
 * convenient: the type is illegal without a price for every active program, and
 * reconciling in a follow-up request would leave a window where a student of
 * that level has a quota concept that prices nothing. Both use cases share one
 * {@code @Transactional} boundary, so a rejected rate set rolls the concept
 * back with it.
 *
 * <p>There is deliberately no {@code programIds}: which programs a concept
 * charges for is stated by its rates, so a concept's scope is the single set of
 * rows the pricing query already reads rather than a second list that has to be
 * kept in agreement with it.
 */
public class CreatePaymentConceptUseCaseImpl implements CreatePaymentConceptUseCase {

	private final PaymentConceptRepository paymentConceptRepository;

	private final PaymentAreaRepository paymentAreaRepository;

	private final ReconcilePaymentRatesUseCase reconcilePaymentRatesUseCase;

	public CreatePaymentConceptUseCaseImpl(PaymentConceptRepository paymentConceptRepository,
			PaymentAreaRepository paymentAreaRepository, ReconcilePaymentRatesUseCase reconcilePaymentRatesUseCase) {
		this.paymentConceptRepository = paymentConceptRepository;
		this.paymentAreaRepository = paymentAreaRepository;
		this.reconcilePaymentRatesUseCase = reconcilePaymentRatesUseCase;
	}

	@Override
	@Transactional
	public PaymentConceptResult createPaymentConcept(CreatePaymentConceptCommand command) {
		validate(command.maxPerStudent(), command.maxPerPeriod(), command.availableFrom(), command.availableUntil(),
				command.cost(), command.isExternal(), command.costExternal(), command.quotaLimit());
		validateReferences(command.areaId(), command.linkedConceptIds(), null, paymentAreaRepository,
				paymentConceptRepository);
		validateCode(command.code(), null, paymentConceptRepository);
		validateQuotaLevel(command.type(), command.levelNumber(), null, paymentConceptRepository);

		PaymentConcept concept = new PaymentConcept(command.name(), command.code(), command.description(),
				command.policies(), command.type(), command.levelNumber(), command.isStandalone(),
				command.maxPerStudent(), command.maxPerPeriod(), command.requiresValidation(), command.availableFrom(),
				command.availableUntil(), command.areaId(), command.cost(), command.isExternal(),
				command.costExternal(), command.isAccumulable(), command.isMulticoncept(), command.quotaLimit(),
				command.linkedConceptIds());
		PaymentConcept saved = paymentConceptRepository.save(concept);

		reconcilePaymentRatesUseCase.reconcileRates(
				new ReconcilePaymentRatesUseCase.ReconcilePaymentRatesCommand(saved.getId(), command.rates()));

		return toResult(saved);
	}

	/**
	 * {@code code} uniqueness, case-insensitive, with {@code excludingId} letting
	 * an update exclude the row being edited so an unchanged re-save is not a
	 * collision with itself.
	 */
	static void validateCode(String code, UUID excludingId, PaymentConceptRepository paymentConceptRepository) {
		if (code == null || code.isBlank()) {
			throw new InvalidPaymentConceptDataException("code is required");
		}
		if (paymentConceptRepository.findByCode(code.trim(), excludingId).isPresent()) {
			throw new DuplicatePaymentConceptCodeException("A payment concept already uses this code: " + code);
		}
	}

	/**
	 * "One ACTIVE recurring quota per level." A second one would leave the
	 * tuition lookup with two defensible prices for the same student, so this is
	 * refused rather than disambiguated.
	 */
	static void validateQuotaLevel(PaymentConceptType type, Integer levelNumber, UUID excludingId,
			PaymentConceptRepository paymentConceptRepository) {
		if (!type.requiresLevelNumber()) {
			return;
		}
		if (paymentConceptRepository.findActiveByTypeAndLevelNumber(type, levelNumber, excludingId).isPresent()) {
			throw new DuplicatePaymentQuotaLevelException(
					"An active recurring quota already exists for this level: levelNumber=" + levelNumber);
		}
	}

	/**
	 * Package-visible (not {@code private}) so
	 * {@code UpdatePaymentConceptUseCaseImpl} can reuse the same range checks
	 * without duplicating them — unlike
	 * {@code CreateAcademicPlanUseCaseImpl}/{@code UpdateAcademicPlanUseCaseImpl},
	 * which duplicate their {@code minPassingGrade} range check inline in each
	 * class.
	 */
	static void validate(Integer maxPerStudent, Integer maxPerPeriod, LocalDate availableFrom, LocalDate availableUntil,
			BigDecimal cost, boolean isExternal, BigDecimal costExternal, Integer quotaLimit) {
		if (maxPerStudent != null && maxPerStudent <= 0) {
			throw new InvalidPaymentConceptDataException("maxPerStudent must be greater than 0: " + maxPerStudent);
		}
		if (maxPerPeriod != null && maxPerPeriod <= 0) {
			throw new InvalidPaymentConceptDataException("maxPerPeriod must be greater than 0: " + maxPerPeriod);
		}
		if (availableFrom != null && availableUntil != null && availableFrom.isAfter(availableUntil)) {
			throw new InvalidPaymentConceptDataException(
					"availableFrom must not be after availableUntil: " + availableFrom + " > " + availableUntil);
		}
		if (cost != null && cost.signum() < 0) {
			throw new InvalidPaymentConceptDataException("cost must not be negative: " + cost);
		}
		if (isExternal && costExternal == null) {
			throw new InvalidPaymentConceptDataException("costExternal is required when isExternal is true");
		}
		if (costExternal != null && costExternal.signum() < 0) {
			throw new InvalidPaymentConceptDataException("costExternal must not be negative: " + costExternal);
		}
		if (quotaLimit != null && quotaLimit <= 0) {
			throw new InvalidPaymentConceptDataException("quotaLimit must be greater than 0: " + quotaLimit);
		}
	}

	/**
	 * Validates the cross-aggregate references carried by the extension
	 * fields. {@code selfId} is {@code null} on Create (the concept id does
	 * not exist yet) and the concept's own id on Update (a concept cannot
	 * link to itself).
	 */
	static void validateReferences(UUID areaId, List<UUID> linkedConceptIds, UUID selfId,
			PaymentAreaRepository paymentAreaRepository, PaymentConceptRepository paymentConceptRepository) {
		if (areaId != null && paymentAreaRepository.findById(areaId).isEmpty()) {
			throw new PaymentConceptReferenceNotFoundException("Payment area not found: " + areaId);
		}
		validateIds(linkedConceptIds, "linkedConceptIds");
		if (linkedConceptIds != null) {
			for (UUID linkedConceptId : linkedConceptIds) {
				if (selfId != null && selfId.equals(linkedConceptId)) {
					throw new InvalidPaymentConceptDataException(
							"A payment concept cannot be linked to itself: " + linkedConceptId);
				}
				if (paymentConceptRepository.findById(linkedConceptId).isEmpty()) {
					throw new PaymentConceptReferenceNotFoundException(
							"Linked payment concept not found: " + linkedConceptId);
				}
			}
		}
	}

	private static void validateIds(List<UUID> ids, String field) {
		if (ids == null) {
			return;
		}
		HashSet<UUID> unique = new HashSet<>();
		for (UUID id : ids) {
			if (id == null) {
				throw new InvalidPaymentConceptDataException(field + " must not contain null ids");
			}
			if (!unique.add(id)) {
				throw new InvalidPaymentConceptDataException(field + " must not contain duplicate ids");
			}
		}
	}

	static PaymentConceptResult toResult(PaymentConcept concept) {
		return new PaymentConceptResult(concept.getId(), concept.getName(), concept.getCode(),
				concept.getDescription(), concept.getPolicies(), concept.getType(), concept.getLevelNumber(),
				concept.isStandalone(), concept.getMaxPerStudent(), concept.getMaxPerPeriod(),
				concept.isRequiresValidation(), concept.getAvailableFrom(), concept.getAvailableUntil(),
				concept.getStatus(), concept.getAreaId(), concept.getCost(), concept.isExternal(),
				concept.getCostExternal(), concept.isAccumulable(), concept.isMulticoncept(),
				concept.getQuotaLimit(), List.copyOf(concept.getLinkedConceptIds()));
	}
}
