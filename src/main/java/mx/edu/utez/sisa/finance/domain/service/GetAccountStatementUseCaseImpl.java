package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.port.in.GetAccountStatementUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.GetPaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.BenefitSummary;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PersonQueryPort.PersonView;
import mx.edu.utez.sisa.finance.shared.exception.PaymentNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.PersonNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Estado de cuenta interactor (Fase 1 §5.3): every adeudo of a
 * candidato/estudiante with its abonos and its applied discount, plus the
 * running balance. Also serves the single-adeudo detail
 * ({@link GetPaymentUseCase}) — one mapping for both.
 *
 * <p>Read-only. The balance is computed, never stored: it is
 * {@code amountDue - amountPaid}, so it can never drift from the abono total
 * the settlement flow keeps.
 */
public class GetAccountStatementUseCaseImpl implements GetAccountStatementUseCase, GetPaymentUseCase {

	private final AnchorQueryPort anchorQueryPort;

	private final PaymentRepository paymentRepository;

	private final PaymentEntryRepository paymentEntryRepository;

	private final PaymentBenefitRepository paymentBenefitRepository;

	public GetAccountStatementUseCaseImpl(AnchorQueryPort anchorQueryPort, PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository, PaymentBenefitRepository paymentBenefitRepository) {
		this.anchorQueryPort = anchorQueryPort;
		this.paymentRepository = paymentRepository;
		this.paymentEntryRepository = paymentEntryRepository;
		this.paymentBenefitRepository = paymentBenefitRepository;
	}

	@Override
	public AccountStatement getAccountStatement(UUID candidateId, UUID studentId) {
		PersonView person = (candidateId != null ? anchorQueryPort.findPersonByCandidateId(candidateId)
				: anchorQueryPort.findPersonByStudentId(studentId))
				.orElseThrow(() -> new PersonNotFoundException(
						"Person not found for anchor: " + (candidateId != null ? candidateId : studentId)));
		List<Payment> payments = candidateId != null ? paymentRepository.findByCandidateId(candidateId)
				: paymentRepository.findByStudentId(studentId);
		List<StatementPayment> rows = payments.stream().map(this::toStatementPayment).toList();
		return new AccountStatement(candidateId, studentId, fullName(person), rows);
	}

	@Override
	public StatementPayment getPayment(UUID id) {
		Payment payment = paymentRepository.findById(id)
				.orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + id));
		return toStatementPayment(payment);
	}

	private StatementPayment toStatementPayment(Payment payment) {
		List<StatementEntry> entries = paymentEntryRepository.findByPaymentIdOrderByPaidAtAsc(payment.getId()).stream()
				.map(GetAccountStatementUseCaseImpl::toStatementEntry)
				.toList();
		BenefitSummary benefit = payment.getBenefitId() == null ? null
				: BenefitSummaries.from(paymentBenefitRepository.findById(payment.getBenefitId()).orElse(null));
		BigDecimal balance = payment.getAmountDue() == null ? null
				: payment.getAmountDue().subtract(payment.getAmountPaid());
		return new StatementPayment(payment.getId(), payment.getConceptCode(), payment.getOriginalAmount(),
				payment.getAmountDue(), payment.getAmountPaid(), balance, payment.getStatus(), payment.getOrigin(),
				payment.getDueAt(), payment.getCreatedAt(), benefit, entries, payment.getReferenceNumber(),
				payment.getProgramId());
	}

	private static StatementEntry toStatementEntry(PaymentEntry entry) {
		return new StatementEntry(entry.getId(), entry.getAmount(), entry.getMethod(), entry.getChannel(),
				entry.getReceiptNumber(), entry.getReference(), entry.isPhysical(), entry.getCollectedByName(),
				entry.getPaidAt());
	}

	private static String fullName(PersonView person) {
		StringBuilder name = new StringBuilder(person.firstName()).append(' ').append(person.lastName1());
		if (person.lastName2() != null && !person.lastName2().isBlank()) {
			name.append(' ').append(person.lastName2());
		}
		return name.toString();
	}
}
