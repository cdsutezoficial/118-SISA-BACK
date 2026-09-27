package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link AdmissionPaymentRepositoryAdapter}.
 * {@code findByCandidateId} backs the payment-confirmation flow (one
 * {@code AdmissionPayment} per {@code Candidate} for the ADMISSION_FICHA
 * concept).
 */
public interface AdmissionPaymentJpaRepository extends JpaRepository<AdmissionPayment, UUID> {

	Optional<AdmissionPayment> findByCandidateId(UUID candidateId);

	/**
	 * Backs {@code AdmissionPaymentRepository#countPaidByAdmissionConfigId} — the
	 * quota rule behind {@code ProgramAdmissionConfig.maxCandidates}.
	 *
	 * <p>The enum is passed in rather than inlined so the {@code PAID} literal
	 * lives in exactly one place, {@link AdmissionPaymentStatus}, and the port's
	 * "paid, not registered" contract cannot drift from the query. Explicit join
	 * (not a JPA association — both sides are bare {@code UUID} columns, the
	 * convention throughout this schema).
	 */
	@Query("""
			SELECT COUNT(pay)
			FROM AdmissionPayment pay
			JOIN Candidate cand ON cand.id = pay.candidateId
			WHERE cand.admissionConfigId = :admissionConfigId
			  AND pay.paymentStatus = :status
			""")
	long countPaidByAdmissionConfigId(@Param("admissionConfigId") UUID admissionConfigId,
			@Param("status") AdmissionPaymentStatus status);
}