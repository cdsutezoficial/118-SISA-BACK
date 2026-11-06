package mx.edu.utez.sisa.finance.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertStatus;
import mx.edu.utez.sisa.finance.domain.port.in.ManageFinanceAlertsUseCase;
import mx.edu.utez.sisa.finance.domain.port.in.RaiseFinanceAlertUseCase;
import mx.edu.utez.sisa.finance.domain.port.out.FinanceAlertNotifier;
import mx.edu.utez.sisa.finance.domain.port.out.FinanceAlertRepository;
import mx.edu.utez.sisa.finance.shared.exception.FinanceAlertNotFoundException;

/**
 * Raising and reviewing Finanzas alerts (E4).
 *
 * <p>Raising is idempotent by kind and order: the reconciliation finds the same old
 * authorization every night, and only the first time opens an alert and mails
 * Finanzas. A resolved alert stays resolved.
 */
public class FinanceAlertService implements RaiseFinanceAlertUseCase, ManageFinanceAlertsUseCase {

	private final FinanceAlertRepository repository;

	private final FinanceAlertNotifier notifier;

	private final Clock clock;

	public FinanceAlertService(FinanceAlertRepository repository, FinanceAlertNotifier notifier, Clock clock) {
		this.repository = repository;
		this.notifier = notifier;
		this.clock = clock;
	}

	/**
	 * In its own transaction: it is called from inside payment flows, and neither should
	 * be able to undo the other. A failed alert must not roll back the saldo a favor it
	 * reports, nor the other way round.
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean raise(RaiseAlertCommand command) {
		Instant now = Instant.now(clock);
		Optional<FinanceAlert> existing = repository.findByTypeAndOrderId(command.type(), command.orderId());
		if (existing.isPresent()) {
			existing.get().seenAgain(now);
			repository.save(existing.get());
			return false;
		}
		FinanceAlert alert = repository.save(new FinanceAlert(command.type(), command.candidateId(),
				command.paymentId(), command.orderId(), command.folio(), command.amount(), command.detail(), now));
		notifier.alertRaised(alert);
		return true;
	}

	@Override
	@Transactional(readOnly = true)
	public List<FinanceAlert> list(FinanceAlertStatus status) {
		return repository.findByStatus(status);
	}

	@Override
	@Transactional
	public FinanceAlert resolve(UUID alertId, UUID userId, String note) {
		FinanceAlert alert = repository.findById(alertId)
				.orElseThrow(() -> new FinanceAlertNotFoundException("No existe la alerta: " + alertId));
		alert.resolve(userId, note, Instant.now(clock));
		return repository.save(alert);
	}
}
