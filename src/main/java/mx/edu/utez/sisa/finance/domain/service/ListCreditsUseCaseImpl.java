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
				.map(credit -> new CreditView(credit.getId(), credit.getAmount(), credit.getStatus(),
						credit.getOriginPaymentId(), credit.getAppliedToPaymentId(), credit.getCreatedAt()))
				.toList();
	}
}
