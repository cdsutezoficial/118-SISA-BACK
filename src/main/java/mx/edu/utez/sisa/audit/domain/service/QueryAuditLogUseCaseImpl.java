package mx.edu.utez.sisa.audit.domain.service;

import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase;
import mx.edu.utez.sisa.audit.domain.port.out.AuditEntryRepository;
import org.springframework.transaction.annotation.Transactional;

public class QueryAuditLogUseCaseImpl implements QueryAuditLogUseCase {

	static final int MAX_PAGE_SIZE = 200;

	private final AuditEntryRepository repository;

	public QueryAuditLogUseCaseImpl(AuditEntryRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public AuditPage search(AuditFilter filter, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		return repository.search(filter == null ? new AuditFilter(null, null, null, null, null, null) : filter,
				safePage, safeSize);
	}
}
