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
 * {@code GenerationRepository}, there is no uniqueness-lookup method — the
 * plan's resolved design carries no uniqueness rule for {@code Group.code}.
 */
public interface GroupRepository {

	Group save(Group group);

	Optional<Group> findById(UUID id);

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
