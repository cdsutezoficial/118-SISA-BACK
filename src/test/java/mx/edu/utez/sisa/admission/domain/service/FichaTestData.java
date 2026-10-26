package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttempt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Test builders for the ficha as finance holds it (2026-10-23): the charge
 * header ({@link FichaPaymentPort.FichaCharge}) and its checkout attempts.
 */
final class FichaTestData {

	static final String REFERENCE = "REF-ADM-2026-000001";

	private FichaTestData() {
	}

	static FichaPaymentPort.FichaCharge pendingCharge(UUID chargeId) {
		return new FichaPaymentPort.FichaCharge(chargeId, REFERENCE, AdmissionPaymentStatus.PENDING, false,
				Instant.now().plus(10, ChronoUnit.DAYS), Instant.now(), null, null);
	}

	static FichaPaymentPort.FichaCharge pendingCharge(UUID chargeId, Instant dueAt) {
		return new FichaPaymentPort.FichaCharge(chargeId, REFERENCE, AdmissionPaymentStatus.PENDING, false, dueAt,
				Instant.now(), null, null);
	}

	static FichaPaymentPort.FichaCharge paidCharge(UUID chargeId) {
		return new FichaPaymentPort.FichaCharge(chargeId, REFERENCE, AdmissionPaymentStatus.PAID, false,
				Instant.now().plus(10, ChronoUnit.DAYS), Instant.now(), Instant.now(), "REC-20260922-000001");
	}

	static FichaPaymentPort.FichaCharge cancelledCharge(UUID chargeId) {
		return new FichaPaymentPort.FichaCharge(chargeId, REFERENCE, AdmissionPaymentStatus.PENDING, true,
				Instant.now().plus(10, ChronoUnit.DAYS), Instant.now(), null, null);
	}

	/** An open attempt with no price snapshot beyond its amount. */
	static CheckoutAttempt attempt(String orderId, UUID chargeId, UUID candidateId, BigDecimal amount,
			Instant createdAt) {
		return new CheckoutAttempt(orderId, chargeId, candidateId, null, amount, createdAt, null, null, null, null);
	}

	static CheckoutAttempt attempt(String orderId, UUID chargeId, UUID candidateId, BigDecimal amount) {
		return attempt(orderId, chargeId, candidateId, amount, Instant.now());
	}
}
