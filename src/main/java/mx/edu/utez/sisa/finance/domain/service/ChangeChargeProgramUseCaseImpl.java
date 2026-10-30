package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.model.PaymentChannel;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.ChangeChargeProgramUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase.DebtQuote;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptRateQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.ConceptNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.NoRateAvailableException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotPayableException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Career change on a charge (see {@link ChangeChargeProgramUseCase}).
 *
 * <p>The paid case never rewrites the paid charge: it keeps its program, its
 * tariff and its abonos as history. The money moves as saldo a favor — the
 * same {@link PaymentCredit} and {@link PaymentEntryMethod#CREDIT} abono that
 * {@link ApplyCreditToPaymentUseCaseImpl} books — so the new charge shows its
 * real tariff, what was carried over and what is left.
 */
public class ChangeChargeProgramUseCaseImpl implements ChangeChargeProgramUseCase {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final PaymentRepository paymentRepository;

	private final PaymentEntryRepository paymentEntryRepository;

	private final PaymentCreditRepository paymentCreditRepository;

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final ConceptQueryPort conceptQueryPort;

	private final ConceptRateQueryPort conceptRateQueryPort;

	private final BenefitResolver benefitResolver;

	private final PaymentQuoter paymentQuoter;

	private final PaymentSettlementDecider settlementDecider;

	private final Clock clock;

	public ChangeChargeProgramUseCaseImpl(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentCreditRepository paymentCreditRepository,
			PaymentBenefitRepository paymentBenefitRepository, ConceptQueryPort conceptQueryPort,
			ConceptRateQueryPort conceptRateQueryPort, BenefitResolver benefitResolver, PaymentQuoter paymentQuoter,
			PaymentSettlementDecider settlementDecider, Clock clock) {
		this.paymentRepository = paymentRepository;
		this.paymentEntryRepository = paymentEntryRepository;
		this.paymentCreditRepository = paymentCreditRepository;
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.conceptQueryPort = conceptQueryPort;
		this.conceptRateQueryPort = conceptRateQueryPort;
		this.benefitResolver = benefitResolver;
		this.paymentQuoter = paymentQuoter;
		this.settlementDecider = settlementDecider;
		this.clock = clock;
	}

	// ── Unpaid: the same charge moves ────────────────────────────────────────

	@Override
	@Transactional(readOnly = true)
	public DebtQuote previewReassign(UUID paymentId, String conceptCode, UUID programId) {
		Payment payment = requirePayment(paymentId);
		requireRepriceable(payment);
		ConceptQueryPort.ConceptView concept = requireConcept(conceptCode);
		ConceptRateQueryPort.ActiveRate rate = requireRate(concept, programId);
		PaymentBenefit benefit = benefitResolver.resolve(benefitsThatMove(payment), LocalDate.now(clock),
				payment.getPeriodId(), payment.getGenerationId()).orElse(null);
		PaymentQuoter.Quotation quotation = paymentQuoter.quote(rate.amount(), benefit);
		return new DebtQuote(payment.getId(), payment.getCandidateId(), payment.getStudentId(), concept.code(),
				concept.name(), payment.getStatus(), rate.id(), quotation.original(), quotation.discount(),
				quotation.due(), payment.getAmountPaid(), BenefitSummaries.from(quotation.benefit()),
				quotation.exempt(), true, payment.getDueAt(), null);
	}

	@Override
	@Transactional
	public void reassign(UUID paymentId, String conceptCode, UUID programId) {
		Payment payment = paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
		requireRepriceable(payment);
		ConceptQueryPort.ConceptView concept = requireConcept(conceptCode);
		requireRate(concept, programId);
		// A discount granted for the old concept (no charge) would stop resolving
		// once the charge carries the new one: tie it to the charge so it follows.
		for (PaymentBenefit benefit : benefitsThatMove(payment)) {
			if (benefit.getPaymentId() == null) {
				benefit.linkToPayment(payment.getId());
				paymentBenefitRepository.save(benefit);
			}
		}
		payment.reassignProgram(concept.code(), programId);
		paymentRepository.save(payment);
	}

	/** The discounts the charge carries today: its own, and the concept's that no charge took. */
	private List<PaymentBenefit> benefitsThatMove(Payment payment) {
		List<PaymentBenefit> benefits = new ArrayList<>(paymentBenefitRepository.findActiveByPaymentId(payment.getId()));
		List<PaymentBenefit> byConcept = payment.getCandidateId() != null
				? paymentBenefitRepository.findActiveByCandidateAndConcept(payment.getCandidateId(),
						payment.getConceptCode())
				: paymentBenefitRepository.findActiveByStudentAndConcept(payment.getStudentId(),
						payment.getConceptCode());
		byConcept.stream().filter(benefit -> benefit.getPaymentId() == null).forEach(benefits::add);
		return benefits;
	}

	// ── Paid: a new charge, paid with what was already paid ──────────────────

	@Override
	@Transactional(readOnly = true)
	public RebillOutcome previewRebill(UUID fromPaymentId, String conceptCode, UUID programId) {
		Payment from = requirePayment(fromPaymentId);
		ConceptRateQueryPort.ActiveRate rate = requireRate(requireConcept(conceptCode), programId);
		BigDecimal alreadyPaid = carriedOver(from);
		BigDecimal otherCredit = availableCredits(from).stream().map(PaymentCredit::getAmount)
				.reduce(ZERO, BigDecimal::add);
		BigDecimal pool = alreadyPaid.add(otherCredit);
		BigDecimal applied = pool.min(rate.amount());
		BigDecimal toPay = rate.amount().subtract(applied);
		return new RebillOutcome(null, rate.id(), rate.amount(), alreadyPaid, otherCredit, applied, toPay,
				pool.subtract(applied), toPay.signum() == 0 ? PaymentStatus.PAID
						: applied.signum() > 0 ? PaymentStatus.PARTIAL : PaymentStatus.PENDING);
	}

	@Override
	@Transactional
	public RebillOutcome rebill(RebillCommand command) {
		Payment from = paymentRepository.findByIdForUpdate(command.fromPaymentId())
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + command.fromPaymentId()));
		ConceptQueryPort.ConceptView concept = requireConcept(command.conceptCode());
		ConceptRateQueryPort.ActiveRate rate = requireRate(concept, command.programId());
		Instant now = Instant.now(clock);

		BigDecimal alreadyPaid = carriedOver(from);
		BigDecimal otherCredit = availableCredits(from).stream().map(PaymentCredit::getAmount)
				.reduce(ZERO, BigDecimal::add);
		if (!isSettled(from)) {
			// A previous career-change charge still unpaid: it is replaced, and what
			// it had received (saldo a favor carried over) goes back to the person.
			from.cancel();
			paymentRepository.save(from);
		}
		if (alreadyPaid.signum() > 0) {
			paymentCreditRepository.save(new PaymentCredit(from.getCandidateId(), from.getStudentId(), from.getId(),
					null, alreadyPaid, now));
		}

		Payment charge = new Payment(from.getCandidateId(), from.getStudentId(), concept.code(), from.getPeriodId(),
				from.getGenerationId(), command.programId(), null, null, null, PaymentStatus.PENDING,
				from.getOrigin(), command.dueAt(), now);
		charge.assignReference(command.reference());
		// The new tariff with no discount: the one there was was used on the
		// paid charge. Fixed now, so the difference is the one staff saw.
		charge.reprice(rate.amount(), rate.amount(), null, rate.id(), PaymentStatus.PENDING);
		charge.lockPrice();
		charge = paymentRepository.save(charge);

		BigDecimal applied = applyCredits(charge, command.actingUserId(), now);
		BigDecimal paid = paymentEntryRepository.sumAmountByPaymentId(charge.getId());
		charge.settle(paid, settlementDecider.decide(charge.getAmountDue(), paid));
		charge = paymentRepository.save(charge);

		BigDecimal pool = alreadyPaid.add(otherCredit);
		return new RebillOutcome(charge.getId(), rate.id(), rate.amount(), alreadyPaid, otherCredit, applied,
				charge.getAmountDue().subtract(paid).max(ZERO), pool.subtract(applied), charge.getStatus());
	}

	/**
	 * Consumes the person's available saldo a favor (oldest first, the one just
	 * created included) against the new charge, with one {@code CREDIT} abono.
	 */
	private BigDecimal applyCredits(Payment charge, UUID actingUserId, Instant now) {
		BigDecimal remaining = charge.getAmountDue();
		BigDecimal applied = ZERO;
		List<PaymentCredit> credits = (charge.getCandidateId() != null
				? paymentCreditRepository.findAvailableByCandidateId(charge.getCandidateId())
				: paymentCreditRepository.findAvailableByStudentId(charge.getStudentId())).stream()
				.sorted(Comparator.comparing(PaymentCredit::getCreatedAt)).toList();
		for (PaymentCredit listed : credits) {
			if (remaining.signum() <= 0) {
				break;
			}
			PaymentCredit credit = paymentCreditRepository.findByIdForUpdate(listed.getId()).orElse(null);
			if (credit == null || !credit.isAvailable()) {
				continue;
			}
			BigDecimal take = remaining.min(credit.getAmount());
			if (take.compareTo(credit.getAmount()) == 0) {
				credit.applyTo(charge.getId());
			} else {
				credit.reduceAmount(take);
			}
			paymentCreditRepository.save(credit);
			applied = applied.add(take);
			remaining = remaining.subtract(take);
		}
		if (applied.signum() > 0) {
			paymentEntryRepository.save(new PaymentEntry(charge.getId(), applied, PaymentEntryMethod.CREDIT,
					PaymentChannel.CASHIER, null, null, null, charge.getReferenceNumber(), null, false, actingUserId,
					null, now, null));
		}
		return applied;
	}

	/**
	 * The money that moves with the payer: what the charge being left received.
	 * A charge paid with a 100 % discount received $0 — the discount was used.
	 */
	private static BigDecimal carriedOver(Payment from) {
		if (from.getStatus() == PaymentStatus.CANCELLED) {
			throw new PaymentNotPayableException("El adeudo " + from.getId() + " está cancelado.");
		}
		return from.getAmountPaid() == null ? ZERO : from.getAmountPaid();
	}

	/** The saldo a favor the person already had, before this change adds any. */
	private List<PaymentCredit> availableCredits(Payment from) {
		return from.getCandidateId() != null ? paymentCreditRepository.findAvailableByCandidateId(from.getCandidateId())
				: paymentCreditRepository.findAvailableByStudentId(from.getStudentId());
	}

	private static boolean isSettled(Payment payment) {
		return payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.EXEMPT;
	}

	private Payment requirePayment(UUID paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
	}

	private static void requireRepriceable(Payment payment) {
		if (!payment.isRepriceable()) {
			throw new PaymentNotPayableException(
					"El adeudo " + payment.getId() + " ya no se puede reasignar (estado " + payment.getStatus() + ").");
		}
	}

	private ConceptQueryPort.ConceptView requireConcept(String conceptCode) {
		return conceptQueryPort.findByCode(conceptCode)
				.orElseThrow(() -> new ConceptNotFoundException("Concept not found: " + conceptCode));
	}

	private ConceptRateQueryPort.ActiveRate requireRate(ConceptQueryPort.ConceptView concept, UUID programId) {
		return conceptRateQueryPort.findActiveRate(concept.id(), programId).orElseThrow(
				() -> new NoRateAvailableException("No active rate for concept=" + concept.code() + " program="
						+ programId));
	}
}
