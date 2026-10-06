package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link OutreachChannel}. There is no
 * {@code findByCode} — this catalog has no code field — but {@code findByName}
 * backs the uniqueness check, exactly as {@code SubjectClassificationRepository}
 * does for its {@code code}.
 */
public interface OutreachChannelRepository {

	OutreachChannel save(OutreachChannel channel);

	/**
	 * Backs {@code GetOutreachChannelUseCase} — same convention as
	 * {@code SubjectClassificationRepository#findById}.
	 */
	Optional<OutreachChannel> findById(UUID id);

	/**
	 * Uniqueness lookup for {@code name}, backing the 409 of
	 * {@code CreateOutreachChannelUseCase} / {@code UpdateOutreachChannelUseCase}.
	 *
	 * <p><b>Deliberately not filtered by {@code status}.</b> The spec requires the
	 * normalized name to be unique "entre registros activos e inactivos": a
	 * deactivated channel still occupies its name, otherwise deactivating
	 * "Facebook" and then creating "facebook" would pass the check and leave two
	 * rows the user cannot tell apart in the reference pickers, which do filter by
	 * status. Same convention as {@code SubjectClassificationRepository}: the
	 * catalog keeps deactivated rows forever, so the constraint cannot be scoped
	 * to the active ones.
	 *
	 * <p>Case-insensitive by contract (see
	 * {@code AcademicDivisionRepository#findByName} for the same shape): the
	 * stored name keeps its casing because it is displayed, so the comparison is
	 * what has to ignore it.
	 *
	 * @param name the already-normalized candidate name
	 */
	Optional<OutreachChannel> findByName(String name);

	/**
	 * Filterable, paginated query backing {@code ListOutreachChannelsUseCase}.
	 */
	OutreachChannelSearchPage search(OutreachChannelSearchCriteria criteria);

	/**
	 * @param status optional — filters to channels with this exact status
	 * @param search optional free-text match against {@code name}
	 * @param page   zero-based page index
	 * @param size   page size
	 */
	record OutreachChannelSearchCriteria(OutreachChannelStatus status, String search, int page, int size) {
	}

	/**
	 * @param content       the {@link OutreachChannel} rows for the requested page
	 * @param totalElements total matching rows across all pages
	 * @param totalPages    total page count for {@code totalElements} at the requested page size
	 */
	record OutreachChannelSearchPage(List<OutreachChannel> content, long totalElements, int totalPages) {
	}
}
