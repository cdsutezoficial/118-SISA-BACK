package mx.edu.utez.sisa.audit.infrastructure.config;

import mx.edu.utez.sisa.audit.domain.port.in.QueryAuditLogUseCase;
import mx.edu.utez.sisa.audit.domain.port.in.RecordAuditUseCase;
import mx.edu.utez.sisa.audit.domain.port.out.AuditActorDirectoryPort;
import mx.edu.utez.sisa.audit.domain.port.out.AuditEntryRepository;
import mx.edu.utez.sisa.audit.domain.service.QueryAuditLogUseCaseImpl;
import mx.edu.utez.sisa.audit.domain.service.RecordAuditUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Nombre explícito por la misma razón que los demás {@code UseCaseConfig}: evitar el choque de nombres. */
@Configuration("auditUseCaseConfig")
public class UseCaseConfig {

	@Bean
	public RecordAuditUseCase recordAuditUseCase(AuditEntryRepository repository,
			AuditActorDirectoryPort actorDirectory, Clock clock) {
		return new RecordAuditUseCaseImpl(repository, actorDirectory, clock);
	}

	@Bean
	public QueryAuditLogUseCase queryAuditLogUseCase(AuditEntryRepository repository) {
		return new QueryAuditLogUseCaseImpl(repository);
	}
}
