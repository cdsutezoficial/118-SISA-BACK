package mx.edu.utez.sisa.audit.domain.service;

import mx.edu.utez.sisa.audit.domain.model.AuditEntry;
import mx.edu.utez.sisa.audit.domain.port.in.RecordAuditUseCase;
import mx.edu.utez.sisa.audit.domain.port.out.AuditActorDirectoryPort;
import mx.edu.utez.sisa.audit.domain.port.out.AuditEntryRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class RecordAuditUseCaseImpl implements RecordAuditUseCase {

	/** Igual que la columna: un valor más largo se corta en vez de tumbar el cambio auditado. */
	static final int MAX_VALUE_LENGTH = 2000;

	private final AuditEntryRepository repository;

	private final AuditActorDirectoryPort actorDirectory;

	private final Clock clock;

	public RecordAuditUseCaseImpl(AuditEntryRepository repository, AuditActorDirectoryPort actorDirectory,
			Clock clock) {
		this.repository = repository;
		this.actorDirectory = actorDirectory;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void record(AuditRecord record) {
		Instant now = Instant.now(clock);
		String actorName = actorDirectory.displayName(record.actorId());
		List<AuditEntry> entries = record.changes().isEmpty()
				? List.of(entry(record, now, actorName, null, null, null))
				: record.changes().stream()
						.map(change -> entry(record, now, actorName, change.field(), change.before(), change.after()))
						.toList();
		repository.saveAll(entries);
	}

	private static AuditEntry entry(AuditRecord record, Instant now, String actorName, String field, String before,
			String after) {
		return new AuditEntry(now, record.actorId(), actorName, record.action(), record.entityType(),
				record.entityId(), field, truncate(before), truncate(after));
	}

	private static String truncate(String value) {
		return value == null || value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH);
	}
}
