package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.PaymentCredit;
import mx.edu.utez.sisa.finance.domain.port.in.ListCreditsUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class ListCreditsUseCaseImpl implements ListCreditsUseCase {

	private final PaymentCreditRepository paymentCreditRepository;

	public ListCreditsUseCaseImpl(PaymentCreditRepository paymentCreditRepository) {
		this.paymentCreditRepository = paymentCreditRepository;
	}

	@Override
	public List<CreditView> listByCandidate(UUID candidateId) {
		return paymentCreditRepository.findByCandidateId(candidateId).stream()
				.sorted(Comparator.comparing(PaymentCredit::getCreatedAt))
				.map(ListCreditsUseCaseImpl::toView)
				.toList();
	}

	@Override
	public List<CreditView> listAvailableOwnByCandidate(UUID candidateId) {
		return paymentCreditRepository.findAvailableOwnByCandidateId(candidateId).stream()
				.map(ListCreditsUseCaseImpl::toView)
				.toList();
	}

	private static CreditView toView(PaymentCredit credit) {
		return new CreditView(credit.getId(), credit.getAmount(), credit.getStatus(), credit.getOriginPaymentId(),
				credit.getAppliedToPaymentId(), credit.getCreatedAt());
	}
}
