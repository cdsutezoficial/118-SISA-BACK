package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.finance.domain.model.CheckoutAttemptCloseReason;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * The quota count of one admission config, over finance (2026-10-23).
 *
 * <p>A place is occupied by a candidate of the config whose ficha charge
 * ({@code Candidate.fichaPaymentId}) is either
 * <ul>
 * <li><b>settled</b> — {@code PAID} or {@code EXEMPT}: a place sold for good; or</li>
 * <li><b>being paid right now</b> — it has a {@code STARTED} checkout attempt
 * opened at or after {@code attemptsOpenedSince} (now − the 15-minute hold).
 * An older open attempt counts for nothing: the hosted session it belonged
 * to is over, and no job has to release it.</li>
 * </ul>
 * Counted per candidate, so several attempts of one ficha are one place.
 *
 * <p>The same rule is written in
 * {@code ProgramAdmissionConfigJpaRepository#findOpenOfferedOptions}, which
 * decides what the career picker offers; both must change together.
 */
public interface FichaOccupancyJpaRepository extends Repository<Candidate, UUID> {

	@Query("""
			SELECT COUNT(cand)
			FROM Candidate cand
			WHERE cand.admissionConfigId = :admissionConfigId
			  AND cand.id <> :excludedCandidateId
			  AND cand.fichaPaymentId IS NOT NULL
			  AND (EXISTS (SELECT pay.id FROM Payment pay
			               WHERE pay.id = cand.fichaPaymentId AND pay.status IN :settled)
			       OR EXISTS (SELECT att.id FROM CheckoutAttempt att
			                  WHERE att.paymentId = cand.fichaPaymentId
			                    AND att.closeReason = :started
			                    AND att.createdAt >= :attemptsOpenedSince))
			""")
	long countOccupied(@Param("admissionConfigId") UUID admissionConfigId,
			@Param("excludedCandidateId") UUID excludedCandidateId,
			@Param("settled") Collection<PaymentStatus> settled,
			@Param("started") CheckoutAttemptCloseReason started,
			@Param("attemptsOpenedSince") Instant attemptsOpenedSince);
}
