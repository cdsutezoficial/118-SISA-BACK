package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
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
 * {@code 02-config-academica.md} line 299). No uniqueness check on
 * {@code name} — deliberately, per plan section 4, same criterion already
 * used in {@code CreateSubjectClassificationUseCaseImpl}. Enforces the
 * {@code maxPerStudent}/{@code maxPerPeriod} > 0 and
 * {@code availableFrom <= availableUntil} range rules (plan section 4 —
 * inferred, marked for correction).
 *
 * <p>
 * The extension fields ({@code areaId}, {@code cost}, {@code costExternal},
 * {@code isExternal}, {@code isAccumulable}, {@code isMulticoncept},
 * {@code quotaLimit}, {@code linkedConceptIds}, {@code programIds}) are
 * validated here (plan {@code 2026-09-19-payment-concept-extension.md} §3):
 * scalar rules raise {@link InvalidPaymentConceptDataException} and cross-
 * aggregate references ({@code PaymentArea}, {@code AcademicProgram},
 * {@code PaymentConcept}) raise {@link PaymentConceptReferenceNotFoundException}
 * — same precedent as {@code CreateAcademicProgramUseCaseImpl} validating
 * {@code divisionId} against {@code AcademicDivision}.
 */
public class CreatePaymentConceptUseCaseImpl implements CreatePaymentConceptUseCase {

	private final PaymentConceptRepository paymentConceptRepository;

	private final PaymentAreaRepository paymentAreaRepository;

	private final AcademicProgramRepository academicProgramRepository;

	public CreatePaymentConceptUseCaseImpl(PaymentConceptRepository paymentConceptRepository,
			PaymentAreaRepository paymentAreaRepository, AcademicProgramRepository academicProgramRepository) {
		this.paymentConceptRepository = paymentConceptRepository;
		this.paymentAreaRepository = paymentAreaRepository;
		this.academicProgramRepository = academicProgramRepository;
	}

	@Override
	@Transactional
	public PaymentConceptResult createPaymentConcept(CreatePaymentConceptCommand command) {
		validate(command.maxPerStudent(), command.maxPerPeriod(), command.availableFrom(), command.availableUntil(),
				command.cost(), command.isExternal(), command.costExternal(), command.quotaLimit());
		validateReferences(command.areaId(), command.programIds(), command.linkedConceptIds(), null,
				paymentAreaRepository, academicProgramRepository, paymentConceptRepository);

		PaymentConcept concept = new PaymentConcept(command.name(), command.description(), command.policies(),
				command.type(), command.isTuition(), command.isStandalone(), command.maxPerStudent(),
				command.maxPerPeriod(), command.requiresValidation(), command.availableFrom(),
				command.availableUntil(), command.areaId(), command.cost(), command.isExternal(),
				command.costExternal(), command.isAccumulable(), command.isMulticoncept(), command.quotaLimit(),
				command.linkedConceptIds(), command.programIds());
		PaymentConcept saved = paymentConceptRepository.save(concept);

		return toResult(saved);
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
	static void validateReferences(UUID areaId, List<UUID> programIds, List<UUID> linkedConceptIds, UUID selfId,
			PaymentAreaRepository paymentAreaRepository, AcademicProgramRepository academicProgramRepository,
			PaymentConceptRepository paymentConceptRepository) {
		if (areaId != null && paymentAreaRepository.findById(areaId).isEmpty()) {
			throw new PaymentConceptReferenceNotFoundException("Payment area not found: " + areaId);
		}
		validateIds(programIds, "programIds");
		if (programIds != null) {
			for (UUID programId : programIds) {
				if (academicProgramRepository.findById(programId).isEmpty()) {
					throw new PaymentConceptReferenceNotFoundException("Academic program not found: " + programId);
				}
			}
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
		return new PaymentConceptResult(concept.getId(), concept.getName(), concept.getDescription(),
				concept.getPolicies(), concept.getType(), concept.isTuition(), concept.isStandalone(),
				concept.getMaxPerStudent(), concept.getMaxPerPeriod(), concept.isRequiresValidation(),
				concept.getAvailableFrom(), concept.getAvailableUntil(), concept.getStatus(), concept.getAreaId(),
				concept.getCost(), concept.isExternal(), concept.getCostExternal(), concept.isAccumulable(),
				concept.isMulticoncept(), concept.getQuotaLimit(), List.copyOf(concept.getLinkedConceptIds()),
				List.copyOf(concept.getProgramIds()));
	}
}
