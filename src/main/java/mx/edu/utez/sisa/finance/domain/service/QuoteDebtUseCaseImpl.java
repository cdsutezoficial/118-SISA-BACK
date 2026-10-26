package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.model.CreditStatus;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.in.QuoteDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.ConceptNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import mx.edu.utez.sisa.finance.shared.exception.NoRateAvailableException;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Live quote of a charge (see {@link QuoteDebtUseCase}). Read-only: it never
 * writes the numbers it computes.
 */
public class QuoteDebtUseCaseImpl implements QuoteDebtUseCase {

	private final PaymentRepository paymentRepository;

	private final DebtQuoter debtQuoter;

	private final Clock clock;

	private final PaymentBenefitRepository paymentBenefitRepository;

	private final PaymentCreditRepository paymentCreditRepository;

	public QuoteDebtUseCaseImpl(PaymentRepository paymentRepository, DebtQuoter debtQuoter, Clock clock,
			PaymentBenefitRepository paymentBenefitRepository, PaymentCreditRepository paymentCreditRepository) {
		this.paymentRepository = paymentRepository;
		this.debtQuoter = debtQuoter;
		this.clock = clock;
		this.paymentBenefitRepository = paymentBenefitRepository;
		this.paymentCreditRepository = paymentCreditRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ChargeView> listCharges(UUID candidateId, UUID studentId) {
		List<DebtQuote> quotes = quoteAll(candidateId, studentId);
		List<PaymentCredit> credits = candidateId != null ? paymentCreditRepository.findByCandidateId(candidateId)
				: paymentCreditRepository.findByStudentId(studentId);
		LocalDate today = LocalDate.now(clock);
		return quotes.stream().map(quote -> {
			PaymentBenefit discount = paymentBenefitRepository.findByPaymentId(quote.paymentId()).stream()
					.filter(benefit -> benefit.getStatus() != BenefitStatus.CANCELLED)
					.findFirst().orElse(null);
			boolean chargeOpen = quote.status() == PaymentStatus.PENDING
					&& (quote.amountPaid() == null || quote.amountPaid().signum() == 0);
			boolean editable = discount != null && discount.isActive() && chargeOpen;
			BigDecimal credit = credits.stream()
					.filter(c -> quote.paymentId().equals(c.getOriginPaymentId()))
					.filter(c -> c.getStatus() != CreditStatus.CANCELLED)
					.map(PaymentCredit::getAmount)
					.reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
			return new ChargeView(quote, discount == null ? null : BenefitViews.toView(discount, today), editable,
					credit);
		}).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public DebtQuote quote(UUID paymentId, LocalDate onDate) {
		if (paymentId == null) {
			throw new InvalidPaymentDataException("paymentId is required");
		}
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
		return quote(payment, onDate == null ? LocalDate.now(clock) : onDate);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DebtQuote> quoteAll(UUID candidateId, UUID studentId) {
		if ((candidateId == null) == (studentId == null)) {
			throw new InvalidPaymentDataException("exactly one of candidateId/studentId is required");
		}
		List<Payment> payments = candidateId != null ? paymentRepository.findByCandidateId(candidateId)
				: paymentRepository.findByStudentId(studentId);
		LocalDate today = LocalDate.now(clock);
		return payments.stream().map(payment -> quoteOrExplain(payment, today)).toList();
	}

	/**
	 * Open charges are priced live; closed ones report what they were frozen
	 * with.
	 */
	DebtQuote quote(Payment payment, LocalDate onDate) {
		if (payment.isRepriceable()) {
			return debtQuoter.live(payment, onDate);
		}
		return debtQuoter.frozen(payment, debtQuoter.conceptName(payment));
	}

	private DebtQuote quoteOrExplain(Payment payment, LocalDate onDate) {
		try {
			return quote(payment, onDate);
		} catch (NoRateAvailableException | ConceptNotFoundException ex) {
			return new DebtQuote(payment.getId(), payment.getCandidateId(), payment.getStudentId(),
					payment.getConceptCode(), debtQuoter.conceptName(payment), payment.getStatus(), null, null, null,
					null, payment.getAmountPaid(), null, false, true, payment.getDueAt(),
					ex instanceof NoRateAvailableException
							? "El concepto no tiene una tarifa vigente para esta carrera."
							: "El concepto de pago ya no existe en el catálogo.");
		}
	}
}
