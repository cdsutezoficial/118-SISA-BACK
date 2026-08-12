package mx.edu.utez.sisa.admission.domain.port.out;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence out-port for {@link OutreachChannel}. No {@code findByCode} /
 * {@code findByName} lookup is declared — unlike {@code SubjectClassification},
 * this aggregate has no uniqueness constraint to enforce, so Create/Update
 * never need to look up an existing row by a candidate value.
 */
public interface OutreachChannelRepository {

	OutreachChannel save(OutreachChannel channel);

	/**
	 * Backs {@code GetOutreachChannelUseCase} — same convention as
	 * {@code SubjectClassificationRepository#findById}.
	 */
	Optional<OutreachChannel> findById(UUID id);

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
