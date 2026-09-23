package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link AdmissionPayment}. The ficha payment is
 * created together with its {@code Candidate} (same transaction,
 * {@code RegisterCandidateUseCase}) and later confirmed ({@code markPaid}) by
 * the payment-confirmation flow.
 */
public interface AdmissionPaymentRepository {

	AdmissionPayment save(AdmissionPayment payment);

	Optional<AdmissionPayment> findByCandidateId(UUID candidateId);
}