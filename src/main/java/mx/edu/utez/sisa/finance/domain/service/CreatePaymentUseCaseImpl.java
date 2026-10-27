package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.CreatePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.BenefitSummary;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.QuoteResult;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Creates the adeudo by running the cotizador and freezing its result
 * (Fase 1 §5.3). A FULL benefit yields a born-{@code EXEMPT} payment with
 * {@code amountDue = $0} — the rastro the product decision asks for.
 *
 * <p>{@code onDate} is optional in the command and defaults to
 * {@code LocalDate.now(clock)} (Mexico-zone clock, same one the admission
 * flow uses) so the window/discount tests and the "quote as of a date" case
 * share one path.
 */
public class CreatePaymentUseCaseImpl implements CreatePaymentUseCase {

	private final QuotePaymentUseCase quotePaymentUseCase;

	private final PaymentRepository paymentRepository;

	private final Clock clock;

	public CreatePaymentUseCaseImpl(QuotePaymentUseCase quotePaymentUseCase, PaymentRepository paymentRepository,
			Clock clock) {
		this.quotePaymentUseCase = quotePaymentUseCase;
		this.paymentRepository = paymentRepository;
		this.clock = clock;
	}

	@Override
	@Transactional
	public PaymentResult createPayment(CreatePaymentCommand command) {
		validate(command);
		QuoteResult quote = quotePaymentUseCase.quote(new QuotePaymentUseCase.QuoteCommand(command.candidateId(),
				command.studentId(), command.conceptCode(), command.periodId(), command.generationId(),
				command.programId(), command.level(), command.onDate()));
		UUID benefitId = quote.benefit() == null ? null : quote.benefit().id();
		PaymentStatus status = quote.exempt() ? PaymentStatus.EXEMPT : PaymentStatus.PENDING;

		Payment payment = new Payment(command.candidateId(), command.studentId(), command.conceptCode(),
				command.periodId(), command.generationId(), command.programId(), quote.originalAmount(),
				quote.amountDue(), benefitId, status, command.origin(), command.dueAt(), Instant.now(clock));
		return toResult(paymentRepository.save(payment), quote.benefit());
	}

	static void validate(CreatePaymentCommand command) {
		if (command.origin() == null) {
			throw new InvalidPaymentDataException("origin is required");
		}
		if ((command.candidateId() == null) == (command.studentId() == null)) {
			throw new InvalidPaymentDataException("exactly one of candidateId/studentId is required");
		}
	}

	/**
	 * Shared with {@code RegisterPaymentEntryUseCaseImpl}: both return the same
	 * payment snapshot after a state transition.
	 */
	static PaymentResult toResult(Payment payment, BenefitSummary benefit) {
		return new PaymentResult(payment.getId(), payment.getCandidateId(), payment.getStudentId(),
				payment.getConceptCode(), payment.getOriginalAmount(), payment.getAmountDue(), payment.getAmountPaid(),
				PaymentSnapshotStatus.valueOf(payment.getStatus().name()), payment.getOrigin(), payment.getDueAt(),
				payment.getCreatedAt(), benefit);
	}
}
