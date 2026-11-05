package mx.edu.utez.sisa.audit.domain.port.out;

import mx.edu.utez.sisa.audit.domain.model.AuditEntry;
import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase.AuditFilter;
import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase.AuditPage;

import java.util.List;

/** Solo agrega y consulta: el log no se edita ni se borra (RN-AUD-001). */
public interface AuditEntryRepository {

	void saveAll(List<AuditEntry> entries);

	AuditPage search(AuditFilter filter, int page, int size);
}
