package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link OutreachChannelRepository} adapter delegating to
 * {@link OutreachChannelJpaRepository}. Results are sorted by {@code name}
 * ascending then {@code id} ascending. The {@code id} tie-breaker was added
 * before {@code name} had any uniqueness constraint and is now redundant on
 * that count, but it stays: it costs nothing and keeps the sort total even if
 * the constraint is ever dropped.
 */
@Component
public class OutreachChannelRepositoryAdapter implements OutreachChannelRepository {

	private final OutreachChannelJpaRepository jpaRepository;

	public OutreachChannelRepositoryAdapter(OutreachChannelJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public OutreachChannel save(OutreachChannel channel) {
		return jpaRepository.save(channel);
	}

	@Override
	public Optional<OutreachChannel> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<OutreachChannel> findByName(String name) {
		return jpaRepository.findByNameIgnoreCase(name);
	}

	@Override
	public OutreachChannelSearchPage search(OutreachChannelSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
		Page<OutreachChannel> page = jpaRepository.search(criteria.status(), criteria.search(), pageRequest);
		return new OutreachChannelSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
