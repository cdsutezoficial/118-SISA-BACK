package mx.edu.utez.sisa.academic_config.domain.port.out;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link Group} — same shape as
 * {@code GenerationRepository}: {@link #save}, {@link #findById}, and a
 * filterable paginated {@link #search(GroupSearchCriteria)}. Unlike
 * {@code GenerationRepository}, plus the two lookups the Fase 8 uniqueness rule
 * needs: {@link #findByGenerationIdAndCode} for the duplicate check and
 * {@link #findCodesByGenerationId} for the bulk-creation letter allocator.
 */
public interface GroupRepository {

	Group save(Group group);

	Optional<Group> findById(UUID id);

	/**
	 * The {@code (generationId, code)} uniqueness probe. {@code programId} is
	 * absent from the key on purpose — it is functionally dependent on
	 * {@code generationId} (see {@code DuplicateGroupCodeException}).
	 */
	Optional<Group> findByGenerationIdAndCode(UUID generationId, String code);

	/**
	 * Every {@code code} already in use within one generation, for the bulk
	 * creation letter allocator. Codes only: the allocator reads one column and
	 * does not need the rows.
	 */
	List<String> findCodesByGenerationId(UUID generationId);

	/**
	 * Filterable, paginated query backing {@code ListGroupsUseCase}.
	 */
	GroupSearchPage search(GroupSearchCriteria criteria);

	/**
	 * @param status       optional — filters to groups with this exact status
	 * @param search       optional free-text match against {@code code}
	 * @param programId    optional — filters to groups belonging to this program
	 * @param generationId optional — filters to groups belonging to this generation
	 * @param page         zero-based page index
	 * @param size         page size
	 */
	record GroupSearchCriteria(GroupStatus status, String search, UUID programId, UUID generationId, int page,
			int size) {
	}

	/**
	 * @param content       the {@link Group} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record GroupSearchPage(List<Group> content, long totalElements, int totalPages) {
	}
}
