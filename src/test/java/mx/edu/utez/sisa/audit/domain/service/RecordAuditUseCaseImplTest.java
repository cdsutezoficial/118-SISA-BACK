package mx.edu.utez.sisa.audit.domain.service;

import mx.edu.utez.sisa.audit.domain.model.AuditEntry;
import mx.edu.utez.sisa.audit.domain.port.in.RecordAuditUseCase.AuditRecord;
import mx.edu.utez.sisa.audit.domain.port.in.RecordAuditUseCase.FieldChange;
import mx.edu.utez.sisa.audit.domain.port.out.AuditActorDirectoryPort;
import mx.edu.utez.sisa.audit.domain.port.out.AuditEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordAuditUseCaseImplTest {

	private static final Instant NOW = Instant.parse("2026-11-05T17:00:00Z");

	private static final UUID ACTOR = UUID.randomUUID();

	private static final UUID ENTITY = UUID.randomUUID();

	@Mock
	private AuditEntryRepository repository;

	@Mock
	private AuditActorDirectoryPort actorDirectory;

	private RecordAuditUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new RecordAuditUseCaseImpl(repository, actorDirectory, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@SuppressWarnings("unchecked")
	void leavesOneEntryPerChangedFieldWithWhoAndWhen() {
		when(actorDirectory.displayName(ACTOR)).thenReturn("Ana López");

		useCase.record(new AuditRecord(ACTOR, "CANDIDATE_FICHA_EDITED", "CANDIDATE", ENTITY,
				List.of(new FieldChange("curp", "A", "B"), new FieldChange("firstName", null, "Juan"))));

		ArgumentCaptor<List<AuditEntry>> saved = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(saved.capture());
		assertThat(saved.getValue())
				.extracting(AuditEntry::getOccurredAt, AuditEntry::getActorId, AuditEntry::getActorName,
						AuditEntry::getAction, AuditEntry::getEntityType, AuditEntry::getEntityId, AuditEntry::getField,
						AuditEntry::getOldValue, AuditEntry::getNewValue)
				.containsExactly(
						tuple(NOW, ACTOR, "Ana López", "CANDIDATE_FICHA_EDITED", "CANDIDATE", ENTITY, "curp", "A", "B"),
						tuple(NOW, ACTOR, "Ana López", "CANDIDATE_FICHA_EDITED", "CANDIDATE", ENTITY, "firstName", null,
								"Juan"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void anEventWithoutFieldsStillLeavesOneEntry() {
		useCase.record(new AuditRecord(null, "LOGIN", "USER", ENTITY, null));

		ArgumentCaptor<List<AuditEntry>> saved = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(saved.capture());
		assertThat(saved.getValue()).singleElement().satisfies(entry -> {
			assertThat(entry.getField()).isNull();
			assertThat(entry.getActorId()).isNull();
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void aValueLongerThanTheColumnIsCutInsteadOfFailingTheChange() {
		String longValue = "x".repeat(RecordAuditUseCaseImpl.MAX_VALUE_LENGTH + 10);

		useCase.record(new AuditRecord(ACTOR, "A", "E", ENTITY, List.of(new FieldChange("f", longValue, "y"))));

		ArgumentCaptor<List<AuditEntry>> saved = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(saved.capture());
		assertThat(saved.getValue().get(0).getOldValue()).hasSize(RecordAuditUseCaseImpl.MAX_VALUE_LENGTH);
	}
}
