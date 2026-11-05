package mx.edu.utez.sisa.finance.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertStatus;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertType;
import mx.edu.utez.sisa.finance.domain.port.in.RaiseFinanceAlertUseCase.RaiseAlertCommand;
import mx.edu.utez.sisa.finance.domain.port.out.FinanceAlertNotifier;
import mx.edu.utez.sisa.finance.domain.port.out.FinanceAlertRepository;
import mx.edu.utez.sisa.finance.shared.exception.FinanceAlertNotFoundException;

/** E4: one alert per kind and order, mailed once, resolved once. */
@ExtendWith(MockitoExtension.class)
class FinanceAlertServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-05T06:05:00Z");

	@Mock
	private FinanceAlertRepository repository;

	@Mock
	private FinanceAlertNotifier notifier;

	private FinanceAlertService service;

	@BeforeEach
	void setUp() {
		service = new FinanceAlertService(repository, notifier, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static RaiseAlertCommand command() {
		return new RaiseAlertCommand(FinanceAlertType.AUTHORIZED_NOT_CAPTURED, UUID.randomUUID(), UUID.randomUUID(),
				"TESTUTEZ-ADM-2026-000001", "ADM-2026-000001", new BigDecimal("1750.00"), "detalle");
	}

	@Test
	void aNewConditionOpensAnAlertAndMailsFinanzas() {
		when(repository.findByTypeAndOrderId(any(), any())).thenReturn(Optional.empty());
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(service.raise(command())).isTrue();

		verify(notifier).alertRaised(any());
	}

	/** The reconciliation finds the same authorization every night: no second alert nor mail. */
	@Test
	void theSameConditionAgainOnlyMovesLastSeen() {
		FinanceAlert existing = new FinanceAlert(FinanceAlertType.AUTHORIZED_NOT_CAPTURED, null, null,
				"TESTUTEZ-ADM-2026-000001", null, null, null, NOW.minusSeconds(86400));
		when(repository.findByTypeAndOrderId(any(), any())).thenReturn(Optional.of(existing));

		assertThat(service.raise(command())).isFalse();

		assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
		verify(notifier, never()).alertRaised(any());
	}

	@Test
	void aResolvedAlertIsNotReopened() {
		FinanceAlert existing = new FinanceAlert(FinanceAlertType.AUTHORIZED_NOT_CAPTURED, null, null,
				"TESTUTEZ-ADM-2026-000001", null, null, null, NOW.minusSeconds(86400));
		existing.resolve(UUID.randomUUID(), "liberado por el banco", NOW.minusSeconds(3600));
		when(repository.findByTypeAndOrderId(any(), any())).thenReturn(Optional.of(existing));

		service.raise(command());

		assertThat(existing.getStatus()).isEqualTo(FinanceAlertStatus.RESOLVED);
		verify(notifier, never()).alertRaised(any());
	}

	@Test
	void resolvingRecordsWhoWhenAndTheNote() {
		FinanceAlert alert = new FinanceAlert(FinanceAlertType.OVERPAYMENT, null, null, "O-1", null, null, null,
				NOW.minusSeconds(60));
		UUID id = UUID.randomUUID();
		UUID user = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(alert));
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		FinanceAlert resolved = service.resolve(id, user, "  devuelto a la persona  ");

		assertThat(resolved.getStatus()).isEqualTo(FinanceAlertStatus.RESOLVED);
		assertThat(resolved.getResolvedBy()).isEqualTo(user);
		assertThat(resolved.getResolvedAt()).isEqualTo(NOW);
		assertThat(resolved.getResolutionNote()).isEqualTo("devuelto a la persona");
	}

	@Test
	void resolvingAnUnknownAlertIsNotFound() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.resolve(id, UUID.randomUUID(), null))
				.isInstanceOf(FinanceAlertNotFoundException.class);
	}
}
