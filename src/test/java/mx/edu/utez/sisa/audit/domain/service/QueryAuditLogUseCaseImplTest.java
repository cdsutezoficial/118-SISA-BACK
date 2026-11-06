package mx.edu.utez.sisa.audit.domain.service;

import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase.AuditFilter;
import mx.edu.utez.sisa.audit.domain.port.out.AuditEntryRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QueryAuditLogUseCaseImplTest {

	private final AuditEntryRepository repository = mock(AuditEntryRepository.class);

	private final QueryAuditLogUseCaseImpl useCase = new QueryAuditLogUseCaseImpl(repository);

	@Test
	void clampsThePageAndItsSize() {
		useCase.search(null, -3, 10_000);

		verify(repository).search(any(AuditFilter.class), eq(0), eq(QueryAuditLogUseCaseImpl.MAX_PAGE_SIZE));
	}

	@Test
	void passesTheFilterThrough() {
		AuditFilter filter = new AuditFilter(null, null, null, "CANDIDATE_FICHA_EDITED", "CANDIDATE", null);

		useCase.search(filter, 2, 0);

		verify(repository).search(filter, 2, 1);
	}
}
