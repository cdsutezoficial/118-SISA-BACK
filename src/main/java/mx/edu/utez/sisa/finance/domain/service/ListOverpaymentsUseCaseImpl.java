package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.port.in.ListOverpaymentsUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentCreditRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.domain.port.out.PersonQueryPort.PersonView;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListOverpaymentsUseCaseImpl implements ListOverpaymentsUseCase {

	private final PaymentCreditRepository paymentCreditRepository;

	private final PaymentRepository paymentRepository;

	private final AnchorQueryPort anchorQueryPort;

	public ListOverpaymentsUseCaseImpl(PaymentCreditRepository paymentCreditRepository,
			PaymentRepository paymentRepository, AnchorQueryPort anchorQueryPort) {
		this.paymentCreditRepository = paymentCreditRepository;
		this.paymentRepository = paymentRepository;
		this.anchorQueryPort = anchorQueryPort;
	}

	@Override
	@Transactional(readOnly = true)
	public List<OverpaymentView> list() {
		Map<UUID, Optional<PersonView>> people = new HashMap<>();
		Map<UUID, String> references = new HashMap<>();
		return paymentCreditRepository.findOverpayments().stream().map(credit -> {
			Optional<PersonView> person = credit.getCandidateId() == null ? Optional.empty()
					: people.computeIfAbsent(credit.getCandidateId(), anchorQueryPort::findPersonByCandidateId);
			return new OverpaymentView(credit.getId(), credit.getCandidateId(),
					person.map(ListOverpaymentsUseCaseImpl::fullName).orElse(null),
					person.map(PersonView::curp).orElse(null), credit.getAmount(), credit.getStatus(),
					reference(credit.getOriginPaymentId(), references),
					reference(credit.getAppliedToPaymentId(), references), credit.getExternalOrderId(),
					credit.getCreatedAt());
		}).toList();
	}

	private String reference(UUID paymentId, Map<UUID, String> references) {
		if (paymentId == null) {
			return null;
		}
		return references.computeIfAbsent(paymentId,
				id -> paymentRepository.findById(id).map(Payment::getReferenceNumber).orElse(null));
	}

	private static String fullName(PersonView person) {
		return Stream.of(person.firstName(), person.lastName1(), person.lastName2()).filter(Objects::nonNull)
				.map(String::trim).filter(part -> !part.isEmpty()).collect(Collectors.joining(" "));
	}

}
