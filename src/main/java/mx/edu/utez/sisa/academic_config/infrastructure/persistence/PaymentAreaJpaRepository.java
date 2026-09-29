package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data interface backing {@link PaymentAreaRepositoryAdapter}. Extends
 * {@link JpaRepository} (giving {@code save}/{@code findById} for free, used
 * directly by integration tests to seed rows).
 */
public interface PaymentAreaJpaRepository extends JpaRepository<PaymentArea, UUID> {

	/**
	 * Case-insensitive lookup — backs {@code PaymentAreaRepository#findByCode}'s
	 * documented case-insensitive contract.
	 */
	Optional<PaymentArea> findByCodeIgnoreCase(String code);

	/**
	 * Case-insensitive lookup — see {@link #findByCodeIgnoreCase(String)}.
	 */
	Optional<PaymentArea> findByNameIgnoreCase(String name);

	/**
	 * Backs {@code ListPaymentAreasUseCase}, mirroring
	 * {@code AcademicDivisionJpaRepository#search}: both filters are optional
	 * via the {@code (:param IS NULL OR ...)} pattern, with an explicit
	 * {@code countQuery}.
	 */
	@Query(value = """
			SELECT a FROM PaymentArea a
			WHERE (:status IS NULL OR a.status = :status)
			  AND (:search IS NULL
			       OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(a.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(a) FROM PaymentArea a
			WHERE (:status IS NULL OR a.status = :status)
			  AND (:search IS NULL
			       OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(a.code) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<PaymentArea> search(@Param("status") PaymentAreaStatus status, @Param("search") String search,
			Pageable pageable);

	/**
	 * Reference-catalog read backing {@code GET /payment-areas/options}: returns
	 * only {@link PaymentAreaStatus#ACTIVE} areas as a minimal
	 * {@link PaymentAreaOptionProjection}, ordered by name.
	 */
	List<PaymentAreaOptionProjection> findByStatusOrderByNameAsc(PaymentAreaStatus status);

	/**
	 * Minimal projection for reference pickers — maps to {@code OptionResponse}.
	 */
	interface PaymentAreaOptionProjection {
		UUID getId();

		String getName();

		String getCode();
	}
}
