package mx.edu.utez.sisa.audit.infrastructure.persistence;

import mx.edu.utez.sisa.audit.domain.model.AuditEntry;
import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase.AuditFilter;
import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase.AuditPage;
import mx.edu.utez.sisa.audit.domain.port.out.AuditEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuditEntryRepositoryAdapter implements AuditEntryRepository {

	private final AuditEntryJpaRepository jpaRepository;

	public AuditEntryRepositoryAdapter(AuditEntryJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public void saveAll(List<AuditEntry> entries) {
		jpaRepository.saveAll(entries);
	}

	@Override
	public AuditPage search(AuditFilter filter, int page, int size) {
		Page<AuditEntry> result = jpaRepository.search(filter.from(), filter.to(), filter.actorId(),
				blankToNull(filter.action()), blankToNull(filter.entityType()), filter.entityId(),
				PageRequest.of(page, size));
		return new AuditPage(result.getContent(), result.getTotalElements(), page, size);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
