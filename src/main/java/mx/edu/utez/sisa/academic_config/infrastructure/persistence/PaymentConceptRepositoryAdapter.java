package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentQuotaLevelException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentConceptRepository} adapter delegating to
 * {@link PaymentConceptJpaRepository}. Results are sorted by {@code name}
 * ascending then {@code id} ascending — {@code name} is NOT unique for this
 * aggregate, and sorting by a second column is what keeps pagination
 * deterministic across pages. {@code code} would also work as the tie-breaker
 * now that it is unique, but {@code id} is used to keep this independent of
 * that field.
 */
@Component
public class PaymentConceptRepositoryAdapter implements PaymentConceptRepository {

	/**
	 * Kept in sync with the {@code @UniqueConstraint} on {@code PaymentConcept}.
	 * Duplicated as a constant rather than read reflectively because the
	 * alternative — matching on the column name — fails: MySQL names the
	 * violation after the index, and the index is named after a column the error
	 * message never mentions.
	 */
	private static final String ACTIVE_LEVEL_CONSTRAINT = "uk_payment_concept_active_level";

	private final PaymentConceptJpaRepository jpaRepository;

	public PaymentConceptRepositoryAdapter(PaymentConceptJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	/**
	 * Flushes explicitly rather than letting the transaction commit do it.
	 *
	 * <p>
	 * That is not a performance choice — it is what makes the race on
	 * {@code uk_payment_concept_active_level} reportable. A deferred flush happens
	 * after the use case has returned, so a constraint violation would reach the
	 * web layer as a raw {@code DataIntegrityViolationException} and be rendered
	 * by the catch-all handler as "revisa el formulario", telling the user to fix
	 * an input that is perfectly valid. Flushing here turns the lost race into
	 * the same {@link DuplicatePaymentQuotaLevelException} the read-then-write
	 * check throws for the non-concurrent case, so both paths answer 409.
	 */
	@Override
	public PaymentConcept save(PaymentConcept concept) {
		try {
			return jpaRepository.saveAndFlush(concept);
		}
		catch (DataIntegrityViolationException ex) {
			if (isActiveLevelCollision(ex)) {
				throw new DuplicatePaymentQuotaLevelException(
						"An active recurring quota already exists for this level: levelNumber=" + concept.getLevelNumber());
			}
			throw ex;
		}
	}

	/**
	 * Recognises only this aggregate's own constraint. Re-throwing everything else
	 * is deliberate: a {@code code} collision or a foreign-key failure has its own
	 * rule and its own message, and swallowing it into "duplicate quota level"
	 * would send the user looking in the wrong place.
	 */
	private static boolean isActiveLevelCollision(DataIntegrityViolationException ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			String message = cause.getMessage();
			if (message != null && message.contains(ACTIVE_LEVEL_CONSTRAINT)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Optional<PaymentConcept> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<PaymentConcept> findByCode(String code, UUID excludingId) {
		if (code == null) {
			return Optional.empty();
		}
		return jpaRepository.findByCodeIgnoreCase(code)
				.filter(concept -> excludingId == null || !excludingId.equals(concept.getId()));
	}

	@Override
	public Optional<PaymentConcept> findActiveByTypeAndLevelNumber(PaymentConceptType type, Integer levelNumber,
			UUID excludingId) {
		if (type == null || levelNumber == null) {
			return Optional.empty();
		}
		return jpaRepository.findActiveByTypeAndLevelNumber(type, levelNumber, PaymentConceptStatus.ACTIVE,
				excludingId);
	}

	@Override
	public PaymentConceptSearchPage search(PaymentConceptSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
		Page<PaymentConcept> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new PaymentConceptSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
