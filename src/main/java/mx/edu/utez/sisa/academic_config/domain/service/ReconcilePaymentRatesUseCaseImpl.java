package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentRateDraft;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicPeriodRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentRateRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentRateException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentRateDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptReferenceNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.PeriodNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.ProgramNotFoundException;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reconciles a concept's complete rate set against what is stored. The whole
 * payload is validated before anything is written, so a rejected call leaves the
 * catalog exactly as it was — with the previous per-rate POST there was no such
 * point, because a failure on the fifth of nine requests had already committed
 * four.
 *
 * <p>
 * The validation steps are ordered cheapest first and the first three never
 * touch the database:
 * <ol>
 * <li>duplicates inside the payload, via a {@code Set} on the combination key
 * <li>amount present and &gt; 0
 * <li>destination programs exist
 * </ol>
 * Only then do the concept's own rules apply, and only then is anything written.
 *
 * <p>
 * Wired as a bean in {@code UseCaseConfig} and injected into
 * {@code CreatePaymentConceptUseCaseImpl}, because a {@code PERIODIC_QUOTA}
 * cannot legally exist unpriced and its rates therefore arrive on the create
 * command. Injecting the interface rather than the class is what makes the
 * create path and the dedicated rate endpoint share one implementation instead
 * of drifting apart — and it is what makes this class's own {@code @Transactional}
 * apply when {@code CreatePaymentConceptUseCaseImpl} calls it, so a rejected
 * rate set rolls the concept back with it.
 */
public class ReconcilePaymentRatesUseCaseImpl implements ReconcilePaymentRatesUseCase {

	private final PaymentRateRepository paymentRateRepository;

	private final PaymentConceptRepository paymentConceptRepository;

	private final AcademicProgramRepository programRepository;

	private final AcademicPeriodRepository periodRepository;

	private final PaymentQuotaCoverageChecker coverageChecker;

	private final Clock clock;

	public ReconcilePaymentRatesUseCaseImpl(PaymentRateRepository paymentRateRepository,
			PaymentConceptRepository paymentConceptRepository, AcademicProgramRepository programRepository,
			AcademicPeriodRepository periodRepository, PaymentQuotaCoverageChecker coverageChecker, Clock clock) {
		this.paymentRateRepository = paymentRateRepository;
		this.paymentConceptRepository = paymentConceptRepository;
		this.programRepository = programRepository;
		this.periodRepository = periodRepository;
		this.coverageChecker = coverageChecker;
		this.clock = clock;
	}

	@Override
	@Transactional
	public List<PaymentRateResult> reconcileRates(ReconcilePaymentRatesCommand command) {
		PaymentConcept concept = requireConcept(command.conceptId());
		List<PaymentRateDraft> requested = command.rates() == null ? List.of() : command.rates();

		Map<DestinationKey, BigDecimal> desired = validatePayload(requested);
		requireReferencesExist(desired.keySet());
		validateConceptRules(concept, desired);

		LocalDateTime now = LocalDateTime.now(clock);
		List<PaymentRateResult> saved = new ArrayList<>();

		for (Map.Entry<DestinationKey, BigDecimal> entry : desired.entrySet()) {
			DestinationKey key = entry.getKey();
			BigDecimal amount = entry.getValue();
			Optional<PaymentRate> current = paymentRateRepository.findActive(command.conceptId(), key.programId(),
					key.level(), key.periodId());

			if (current.isPresent() && current.get().getAmount().compareTo(amount) == 0) {
				// Unchanged price: leave the row alone. Writing a new row here
				// would manufacture history for a save that changed nothing, and
				// that is what makes createdAt useless as "when did this price
				// last change" — the answer would be "whenever anyone hit save".
				saved.add(toResult(current.get()));
				continue;
			}

			current.ifPresent(PaymentRate::deactivate);
			saved.add(toResult(paymentRateRepository.save(
					new PaymentRate(command.conceptId(), key.programId(), key.level(), amount, key.periodId(), now))));
		}

		retireAbsentRates(concept, desired.keySet());
		return saved;
	}

	/**
	 * Destinations stored for the concept that the caller did not send.
	 *
	 * <p>
	 * For a {@code PERIODIC_QUOTA} this is unreachable — the completeness check
	 * already refused the call — so one of these rows can only deactivate
	 * because its program stopped being ACTIVE. For every other concept type the
	 * caller genuinely can withdraw a price, and doing so deactivates rather
	 * than deletes, which keeps the history answerable.
	 */
	private void retireAbsentRates(PaymentConcept concept, Set<DestinationKey> desired) {
		for (PaymentRate stored : paymentRateRepository.findHistoryByConceptId(concept.getId())) {
			if (stored.getStatus() == PaymentRateStatus.INACTIVE) {
				continue;
			}
			DestinationKey key = new DestinationKey(stored.getProgramId(), stored.getLevel(), stored.getPeriodId());
			if (!desired.contains(key)) {
				stored.deactivate();
				paymentRateRepository.save(stored);
			}
		}
	}

	/**
	 * Collapses the payload to one entry per destination, rejecting anything that
	 * would make the outcome ambiguous.
	 */
	private Map<DestinationKey, BigDecimal> validatePayload(List<PaymentRateDraft> requested) {
		Map<DestinationKey, BigDecimal> desired = new LinkedHashMap<>();
		Set<DestinationKey> seen = new LinkedHashSet<>();

		for (PaymentRateDraft draft : requested) {
			DestinationKey key = new DestinationKey(draft.programId(), draft.level(), draft.periodId());

			if (!seen.add(key)) {
				throw new DuplicatePaymentRateException(
						"The rate set prices this destination more than once: programId=" + draft.programId() + ", level="
								+ draft.level() + ", periodId=" + draft.periodId());
			}
			if (draft.amount() == null || draft.amount().compareTo(BigDecimal.ZERO) <= 0) {
				throw new InvalidPaymentRateDataException(
						"amount must be greater than zero: programId=" + draft.programId() + ", level=" + draft.level()
								+ ", amount=" + draft.amount());
			}
			desired.put(key, draft.amount());
		}
		return desired;
	}

	/**
	 * Rules that depend on the concept itself, and are the reason one rate set
	 * cannot be treated like another.
	 */
	private void validateConceptRules(PaymentConcept concept, Map<DestinationKey, BigDecimal> desired) {
		if (concept.getType() != PaymentConceptType.PERIODIC_QUOTA) {
			return;
		}

		for (DestinationKey key : desired.keySet()) {
			// A null program is the general rung for ENROLLMENT concepts, where
			// "every program at no extra price" is meaningful. For PERIODIC_QUOTA it
			// is meaningless — the completeness check below counts one row per
			// active program, so a null-program row could never satisfy it and would
			// only sit in the set as a row nobody is charged. Rejected outright so the
			// caller gets the reason instead of an NPE from the completeness report.
			if (key.programId() == null) {
				throw new InvalidPaymentRateDataException(
						"A PERIODIC_QUOTA rate must name the program it prices; there is no general rate for a quota: level="
								+ key.level() + ", periodId=" + key.periodId());
			}
			if (key.periodId() != null) {
				throw new InvalidPaymentRateDataException(
						"A PERIODIC_QUOTA rate cannot be scoped to a single period; it is priced per level and applies to every period of the cycle: programId="
								+ key.programId() + ", periodId=" + key.periodId());
			}
			if (key.level() != null) {
				throw new InvalidPaymentRateDataException(
						"A PERIODIC_QUOTA rate is priced per program only; its level comes from the concept's levelNumber, not from the rate: programId="
								+ key.programId() + ", level=" + key.level());
			}
		}

		// Completeness is the same rule the type-change guard applies, so it is
		// asked rather than restated here. The desired set is reduced to program
		// ids first because every quota rate is program-scoped with level and
		// period null, which the loop above just enforced.
		coverageChecker.requireCompleteCoverage(
				desired.keySet().stream().map(DestinationKey::programId).collect(Collectors.toSet()));
	}

	private void requireReferencesExist(Set<DestinationKey> destinations) {
		for (DestinationKey key : destinations) {
			if (key.programId() != null && programRepository.findById(key.programId()).isEmpty()) {
				throw new ProgramNotFoundException("Academic program not found: " + key.programId());
			}
			if (key.periodId() != null && periodRepository.findById(key.periodId()).isEmpty()) {
				throw new PeriodNotFoundException("Academic period not found: " + key.periodId());
			}
		}
	}

	private PaymentConcept requireConcept(UUID conceptId) {
		if (conceptId == null) {
			throw new PaymentConceptReferenceNotFoundException("Payment concept not found: " + conceptId);
		}
		return paymentConceptRepository.findById(conceptId)
				.orElseThrow(() -> new PaymentConceptReferenceNotFoundException(
						"Payment concept not found: " + conceptId));
	}

	static PaymentRateResult toResult(PaymentRate rate) {
		return new PaymentRateResult(rate.getId(), rate.getConceptId(), rate.getProgramId(), rate.getLevel(),
				rate.getAmount(), rate.getPeriodId(), rate.getStatus(), rate.getCreatedAt());
	}

	/**
	 * The rate table's business key. A {@code record} rather than three fields
	 * passed around separately, because "did the caller mention this
	 * destination?" has to be answered by equality and comparing nullable fields
	 * by hand is where {@code null} mistakes live — a null {@code level} means
	 * "this exact level-less row", not "any level".
	 */
	private record DestinationKey(UUID programId, AcademicLevel level, UUID periodId) {
	}
}