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

	/**
	 * How many fichas of the given admission config have actually been
	 * <em>paid</em> — the left side of the quota rule the Configuración de
	 * Admisión screen edits as {@code maxCandidates}.
	 *
	 * <p>Paid, not registered: a program may take a hundred registrations and
	 * still be under quota while fewer than {@code maxCandidates} have paid.
	 * Counting {@code Candidate} rows instead would let the quota be consumed by
	 * people who never complete a payment, which is the opposite of what the
	 * field promises.
	 *
	 * <p>Joins {@code candidate.admission_config_id} because the quota is per
	 * admission process (program + period), not per program: the same program can
	 * be sold twice, in two periods, with two separate quotas.
	 *
	 * @param admissionConfigId the config whose paid fichas are counted
	 */
	long countPaidByAdmissionConfigId(UUID admissionConfigId);
}