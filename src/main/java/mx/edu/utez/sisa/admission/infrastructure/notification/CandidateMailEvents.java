package mx.edu.utez.sisa.admission.infrastructure.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult;

/** The application events behind {@link SpringCandidateNotifier}; one per mail. */
final class CandidateMailEvents {

	private CandidateMailEvents() {
	}

	record PaymentConfirmed(ConfirmPaymentResult result) {
	}

	record DifferenceLeft(UUID candidateId, BigDecimal newAmount, BigDecimal alreadyPaid, BigDecimal toPay,
			Instant dueAt) {
	}

	record CreditLeft(UUID candidateId, BigDecimal credit) {
	}
}
