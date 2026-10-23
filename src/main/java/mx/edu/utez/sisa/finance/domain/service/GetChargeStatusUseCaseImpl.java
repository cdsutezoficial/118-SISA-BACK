package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.port.in.GetChargeStatusUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GetChargeStatusUseCaseImpl implements GetChargeStatusUseCase {

	private final PaymentRepository paymentRepository;
	private final PaymentEntryRepository paymentEntryRepository;

	public GetChargeStatusUseCaseImpl(PaymentRepository paymentRepository,
			PaymentEntryRepository paymentEntryRepository) {
		this.paymentRepository = paymentRepository;
		this.paymentEntryRepository = paymentEntryRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ChargeStatus> find(UUID paymentId) {
		if (paymentId == null) {
			return Optional.empty();
		}
		return paymentRepository.findById(paymentId).map(this::toStatus);
	}

	private ChargeStatus toStatus(Payment payment) {
		List<PaymentEntry> entries = paymentEntryRepository.findByPaymentIdOrderByPaidAtAsc(payment.getId());
		PaymentEntry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
		return new ChargeStatus(payment.getId(), payment.getCandidateId(), payment.getStudentId(),
				payment.getConceptCode(), payment.getReferenceNumber(), payment.getStatus(), payment.getDueAt(),
				payment.getCreatedAt(), payment.getAmountPaid(), last == null ? null : last.getPaidAt(),
				last == null ? null : last.getReceiptNumber());
	}
}
