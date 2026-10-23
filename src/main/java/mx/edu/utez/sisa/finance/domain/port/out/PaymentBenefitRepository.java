package mx.edu.utez.sisa.finance.domain.port.out;

import mx.edu.utez.sisa.finance.domain.model.BenefitStatus;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link PaymentBenefit}. Fase 1 needed the reads the
 * quoter and the statement make plus {@code save}; Fase 2 adds the CRUD the
 * manager screen drives ({@link #search}) on top of the same storage.
 */
public interface PaymentBenefitRepository {

	PaymentBenefit save(PaymentBenefit benefit);

	Optional<PaymentBenefit> findById(UUID id);

	/**
	 * Candidate discounts for an aspirante on a concept code. Only
	 * {@code ACTIVE} rows are returned; window and anchor matching happen in
	 * the domain ({@link PaymentBenefit#appliesTo}) so the rule lives in one
	 * place.
	 */
	List<PaymentBenefit> findActiveByCandidateAndConcept(UUID candidateId, String conceptCode);

	/**
	 * Student discounts for a student on a concept code (same contract as
	 * {@link #findActiveByCandidateAndConcept}).
	 */
	List<PaymentBenefit> findActiveByStudentAndConcept(UUID studentId, String conceptCode);

	/**
	 * Filterable query backing {@code GET /finance/benefits} and the discount
	 * report. Every criterion is optional ({@code null} = "any"); results sort
	 * by {@code createdAt} descending. {@code createdFrom}/{@code createdTo}
	 * bound the grant date for the report.
	 */
	/** ACTIVE discounts granted on this specific charge. */
	List<PaymentBenefit> findActiveByPaymentId(UUID paymentId);

	/** Every discount granted on this charge, newest first, any status. */
	List<PaymentBenefit> findByPaymentId(UUID paymentId);

	List<PaymentBenefit> search(BenefitSearchCriteria criteria);

	record BenefitSearchCriteria(UUID candidateId, UUID studentId, String conceptCode, BenefitStatus status,
			Instant createdFrom, Instant createdTo) {
	}
}
