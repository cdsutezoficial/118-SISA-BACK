package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Spring Data interface backing {@link OutreachChannelRepositoryAdapter}.
 * Extends {@link JpaRepository} (giving {@code save}/{@code findById} for
 * free, used directly by integration tests to seed rows).
 */
public interface OutreachChannelJpaRepository extends JpaRepository<OutreachChannel, UUID> {

	/**
	 * Backs {@code ListOutreachChannelsUseCase}, mirroring
	 * {@code SubjectClassificationJpaRepository#search}: both filters are
	 * optional via the {@code (:param IS NULL OR ...)} pattern, with an
	 * explicit {@code countQuery} for consistency with the rest of the
	 * codebase. Unlike {@code SubjectClassification}, there is no
	 * {@code code}-equivalent field, so {@code search} only matches
	 * {@code name}.
	 */
	@Query(value = """
			SELECT c FROM OutreachChannel c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""",
			countQuery = """
			SELECT COUNT(c) FROM OutreachChannel c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<OutreachChannel> search(@Param("status") OutreachChannelStatus status, @Param("search") String search,
			Pageable pageable);
}
