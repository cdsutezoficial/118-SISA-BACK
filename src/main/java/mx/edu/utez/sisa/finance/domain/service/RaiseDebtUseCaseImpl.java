package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.RaiseDebtUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.ConceptNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import mx.edu.utez.sisa.finance.shared.exception.PersonNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raises a header-only charge (see {@link RaiseDebtUseCase}).
 */
public class RaiseDebtUseCaseImpl implements RaiseDebtUseCase {

	private final PaymentRepository paymentRepository;

	private final AnchorQueryPort anchorQueryPort;

	private final ConceptQueryPort conceptQueryPort;

	private final Clock clock;

	public RaiseDebtUseCaseImpl(PaymentRepository paymentRepository, AnchorQueryPort anchorQueryPort,
			ConceptQueryPort conceptQueryPort, Clock clock) {
		this.paymentRepository = paymentRepository;
		this.anchorQueryPort = anchorQueryPort;
		this.conceptQueryPort = conceptQueryPort;
		this.clock = clock;
	}

	@Override
	@Transactional
	public UUID raise(RaiseDebtCommand command) {
		validate(command);
		boolean candidate = command.candidateId() != null;
		if (candidate ? !anchorQueryPort.candidateExists(command.candidateId())
				: !anchorQueryPort.studentExists(command.studentId())) {
			throw new PersonNotFoundException("Anchor not found: "
					+ (candidate ? command.candidateId() : command.studentId()));
		}
		ConceptQueryPort.ConceptView concept = conceptQueryPort.findByCode(command.conceptCode())
				.orElseThrow(() -> new ConceptNotFoundException("Concept not found: " + command.conceptCode()));

		Optional<Payment> open = openCharge(command, concept.code());
		if (open.isPresent()) {
			if (command.referenceNumber() != null && open.get().getReferenceNumber() == null) {
				open.get().assignReference(command.referenceNumber());
				paymentRepository.save(open.get());
			}
			return open.get().getId();
		}

		Payment payment = new Payment(command.candidateId(), command.studentId(), concept.code(), command.periodId(),
				command.generationId(), command.programId(), null, null, null, PaymentStatus.PENDING,
				command.origin(), command.dueAt(), Instant.now(clock));
		payment.assignReference(command.referenceNumber());
		return paymentRepository.save(payment).getId();
	}

	private Optional<Payment> openCharge(RaiseDebtCommand command, String conceptCode) {
		List<Payment> existing = command.candidateId() != null
				? paymentRepository.findByCandidateIdAndConceptCode(command.candidateId(), conceptCode)
				: paymentRepository.findByStudentId(command.studentId()).stream()
						.filter(payment -> payment.getConceptCode().equalsIgnoreCase(conceptCode)).toList();
		return existing.stream()
				.filter(payment -> payment.getStatus() != PaymentStatus.CANCELLED
						&& payment.getStatus() != PaymentStatus.PAID)
				.findFirst();
	}

	static void validate(RaiseDebtCommand command) {
		if ((command.candidateId() == null) == (command.studentId() == null)) {
			throw new InvalidPaymentDataException("exactly one of candidateId/studentId is required");
		}
		if (command.conceptCode() == null || command.conceptCode().isBlank()) {
			throw new InvalidPaymentDataException("conceptCode is required");
		}
		if (command.programId() == null) {
			throw new InvalidPaymentDataException("programId is required to price the charge");
		}
		if (command.origin() == null) {
			throw new InvalidPaymentDataException("origin is required");
		}
	}
}
