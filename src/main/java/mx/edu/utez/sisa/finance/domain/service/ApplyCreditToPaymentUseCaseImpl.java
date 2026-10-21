package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntryMethod;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.ApplyCreditToPaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.BenefitSummary;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.CreditNotApplicableException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Applies a person's saldo a favor to one adeudo (R12, Fase 2 §5.1). Runs in
 * one transaction under a pessimistic lock on the adeudo and, per credit, on
 * the credit row, so two concurrent applications cannot both spend the same
 * balance. Credits are consumed oldest-first and partially when the remaining
 * balance is smaller than the credit; the leftover stays {@code AVAILABLE}.
 *
 * <p>The application writes one {@link PaymentEntryMethod#CREDIT} entry for
 * the total consumed and re-runs the settlement decider, so a credit can fully
 * or partially settle an adeudo exactly like a cash abono.
 */
public class ApplyCreditToPaymentUseCaseImpl implements ApplyCreditToPaymentUseCase {

	private final PaymentRepository paymentRepository;

	private final PaymentEntryRepository paymentEntryRepository;

	private final PaymentCreditRepository paymentCreditRepository;

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final PaymentSettlementDecider settlementDecider;

	private final Clock clock;

	public ApplyCreditToPaymentUseCaseImpl(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentCreditRepository paymentCreditRepository,
			PaymentBenefitRepository paymentBenefitRepository, PaymentSettlementDecider settlementDecider,
			Clock clock) {
		this.paymentRepository = paymentRepository;
		this.paymentEntryRepository = paymentEntryRepository;
		this.paymentCreditRepository = paymentCreditRepository;
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.settlementDecider = settlementDecider;
		this.clock = clock;
	}

	@Override
	@Transactional
	public CreditApplicationResult applyCredit(ApplyCreditCommand command) {
		if (command.paymentId() == null) {
			throw new InvalidPaymentDataException("paymentId is required");
		}
		if (command.channel() == null) {
			throw new InvalidPaymentDataException("channel is required");
		}
		Payment payment = paymentRepository.findByIdForUpdate(command.paymentId())
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + command.paymentId()));
		if (!payment.getStatus().acceptsEntries()) {
			throw new CreditNotApplicableException(
					"Payment does not accept abonos in status " + payment.getStatus() + ": " + payment.getId());
		}
		BigDecimal remaining = payment.getAmountDue().subtract(paymentEntryRepository.sumAmountByPaymentId(payment.getId()));
		if (remaining.signum() <= 0) {
			throw new CreditNotApplicableException("Payment has no remaining balance: " + payment.getId());
		}

		BigDecimal applied = BigDecimal.ZERO.setScale(2);
		List<UUID> appliedCreditIds = new ArrayList<>();
		List<PaymentCredit> credits = (payment.getCandidateId() != null
				? paymentCreditRepository.findAvailableByCandidateId(payment.getCandidateId())
				: paymentCreditRepository.findAvailableByStudentId(payment.getStudentId())).stream()
				.sorted(Comparator.comparing(PaymentCredit::getCreatedAt)).toList();
		for (PaymentCredit candidate : credits) {
			if (remaining.signum() <= 0) {
				break;
			}
			PaymentCredit credit = paymentCreditRepository.findByIdForUpdate(candidate.getId()).orElse(null);
			if (credit == null || !credit.isAvailable()) {
				continue;
			}
			BigDecimal take = remaining.min(credit.getAmount());
			if (take.signum() <= 0) {
				continue;
			}
			if (take.compareTo(credit.getAmount()) == 0) {
				credit.applyTo(payment.getId());
			} else {
				credit.reduceAmount(take);
			}
			paymentCreditRepository.save(credit);
			applied = applied.add(take);
			remaining = remaining.subtract(take);
			appliedCreditIds.add(credit.getId());
		}
		if (applied.signum() <= 0) {
			throw new CreditNotApplicableException("No available saldo a favor for anchor: "
					+ (payment.getCandidateId() != null ? payment.getCandidateId() : payment.getStudentId()));
		}

		PaymentEntry entry = new PaymentEntry(payment.getId(), applied, PaymentEntryMethod.CREDIT, command.channel(),
				null, null, null, null, null, false, command.actingUserId(), null, Instant.now(clock), null);
		paymentEntryRepository.save(entry);

		BigDecimal paidAfter = paymentEntryRepository.sumAmountByPaymentId(payment.getId());
		PaymentStatus newStatus = settlementDecider.decide(payment.getAmountDue(), paidAfter);
		payment.settle(paidAfter, newStatus);
		Payment saved = paymentRepository.save(payment);

		BenefitSummary benefit = saved.getBenefitId() == null ? null
				: BenefitSummaries.from(paymentBenefitRepository.findById(saved.getBenefitId()).orElse(null));
		return new CreditApplicationResult(CreatePaymentUseCaseImpl.toResult(saved, benefit), applied,
				appliedCreditIds);
	}
}
