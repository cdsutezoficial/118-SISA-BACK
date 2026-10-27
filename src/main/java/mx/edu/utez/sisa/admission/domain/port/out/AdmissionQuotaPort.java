package mx.edu.utez.sisa.admission.domain.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * The serialised read that makes the quota enforceable, kept separate from
 * {@link ProgramAdmissionConfigQueryPort} on purpose.
 *
 * <p>That port is documented read-only and is used for advisory lookups. A quota
 * check is the opposite of advisory: it has to be correct under concurrency, and
 * it is only correct if the cap and the count are read under the same lock in the
 * same transaction. Exposing a plain "read the cap" method next to a plain
 * "count the fichas" method invites exactly the two-call race this exists to
 * prevent, so the lock and the cap travel together here and the count is taken
 * by {@link #countOccupiedExcluding} while the lock is still held.
 */
public interface AdmissionQuotaPort {

	/**
	 * Reads a config's quota while holding an exclusive lock on its row.
	 *
	 * <p>Implementations MUST take a pessimistic write lock on
	 * {@code program_admission_config} and hold it until the calling transaction
	 * commits or rolls back. Callers therefore have to be inside a transaction
	 * that is short: taking this lock and then calling the payment gateway would
	 * hold the admission process hostage to a third party's latency.
	 *
	 * @param admissionConfigId the config whose quota is being claimed
	 * @throws mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException
	 *                           if no such config exists
	 */
	QuotaState lockQuota(UUID admissionConfigId);

	/**
	 * Places of the config taken by <em>other</em> candidates: fichas whose
	 * charge is {@code PAID}/{@code EXEMPT}, plus fichas with a checkout attempt
	 * still open that was started at or after {@code attemptsOpenedSince}
	 * (now − 15 min). Call it while holding {@link #lockQuota}.
	 *
	 * @param candidateId the candidate asking, left out of the count so a retry
	 *                    of their own checkout never competes with itself;
	 *                    {@code null} counts everybody
	 */
	long countOccupiedExcluding(UUID admissionConfigId, UUID candidateId, Instant attemptsOpenedSince);

	/**
	 * The cap. The count that is compared against it is taken by
	 * {@link #countOccupiedExcluding}, keyed by the
	 * same {@code admissionConfigId} the lock was taken on — the caller already
	 * holds it and does not need it returned here.
	 *
	 * @param maxCandidates how many fichas may end up paid for the program
	 */
	record QuotaState(int maxCandidates) {
	}
}
