package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;

import java.time.LocalDate;
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

	/**
	 * How many of an admission config's quota slots are taken, counted the way the
	 * quota is actually enforced: a paid ficha, or a pending one that has claimed
	 * a slot at checkout while its payment window is still open.
	 *
	 * <p>Replaces {@link #countPaidByAdmissionConfigId} as <b>the</b> quota rule.
	 * Counting only paid fichas was the original bug: the check ran at
	 * registration, the slot was consumed at payment, and those are days apart — so
	 * any number of people could register on the same reading of the counter and
	 * all of them could go on to pay.
	 *
	 * <p>Keyed by config, matching {@link #countPaidByAdmissionConfigId} and the
	 * picker's own subquery. It used to be keyed by program, on the reasoning that
	 * the expiry rule is found per program — true for the <em>price</em>, which the
	 * query still reads per program, but false for the <em>quota</em>: the same
	 * program sold in two periods has two quotas, and summing them let a full old
	 * cycle block the new one.
	 *
	 * @param admissionConfigId the config whose occupied slots are counted
	 * @param onDate            today, in the admission zone, used for window expiry
	 */
	long countOccupiedByConfigId(UUID admissionConfigId, LocalDate onDate);

	/**
	 * {@link #countOccupiedByConfigId} minus the requesting candidate's own
	 * ficha.
	 *
	 * <p>This is the one the checkout claim must use. A candidate retrying a
	 * checkout already holds a claim, and their own claim is inside the plain
	 * count — so on a career that sold its last slot, {@code occupied >= max}
	 * would refuse them a retry for a slot that is theirs. Excluding themselves
	 * makes the comparison mean "is there room for one more ficha besides the one
	 * I already hold".
	 *
	 * @param admissionConfigId the config whose occupied slots are counted
	 * @param candidateId       the candidate whose own ficha is left out
	 * @param onDate            today, in the admission zone, used for window expiry
	 */
	long countOccupiedByConfigIdExcludingCandidate(UUID admissionConfigId, UUID candidateId, LocalDate onDate);
}
