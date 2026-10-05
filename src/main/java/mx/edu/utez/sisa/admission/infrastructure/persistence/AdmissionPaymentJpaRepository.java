package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link AdmissionPaymentRepositoryAdapter}.
 * {@code findByCandidateId} backs the payment-confirmation flow (one
 * {@code AdmissionPayment} per {@code Candidate} for the ADMISSION_FICHA
 * concept).
 *
 * <p>Occupancy queries ({@code countOccupiedByConfigId} and its
 * self-excluding variant) come from {@link AdmissionPaymentOccupancyQueries},
 * which this repository extends: the dropdown and the checkout claim have to
 * agree on what "full" means, so the JPQL is defined once.
 */
public interface AdmissionPaymentJpaRepository
		extends JpaRepository<AdmissionPayment, UUID>, AdmissionPaymentOccupancyQueries {

	Optional<AdmissionPayment> findByCandidateId(UUID candidateId);

	Optional<AdmissionPayment> findByCandidateIdAndConcept(UUID candidateId, AdmissionPaymentConcept concept);

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
