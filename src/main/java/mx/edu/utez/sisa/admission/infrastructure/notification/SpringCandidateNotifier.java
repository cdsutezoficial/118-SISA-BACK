package mx.edu.utez.sisa.admission.infrastructure.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateNotificationPort;

/**
 * {@link CandidateNotificationPort} as Spring events. Publishing is all it does: the
 * mail is sent by {@link CandidateMailListener} once the transaction that published
 * the event has committed, so the domain never waits on SMTP and a rollback sends
 * nothing.
 */
@Component
public class SpringCandidateNotifier implements CandidateNotificationPort {

	private final ApplicationEventPublisher publisher;

	public SpringCandidateNotifier(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public void paymentConfirmed(ConfirmPaymentResult result) {
		publisher.publishEvent(new CandidateMailEvents.PaymentConfirmed(result));
	}

	@Override
	public void programChangeLeftDifference(UUID candidateId, BigDecimal newAmount, BigDecimal alreadyPaid,
			BigDecimal toPay, Instant dueAt) {
		publisher.publishEvent(new CandidateMailEvents.DifferenceLeft(candidateId, newAmount, alreadyPaid, toPay,
				dueAt));
	}

	@Override
	public void programChangeLeftCredit(UUID candidateId, BigDecimal credit) {
		publisher.publishEvent(new CandidateMailEvents.CreditLeft(candidateId, credit));
	}
}
