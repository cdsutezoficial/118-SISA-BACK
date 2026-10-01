package mx.edu.utez.sisa.admission.infrastructure.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import mx.edu.utez.sisa.admission.domain.port.in.ReconcileFichaPaymentsUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.ReconcileFichaPaymentsUseCase.ReconciliationResult;

/**
 * Daily job (RECONCILIAR_PAGOS) that asks the bank about every payment attempt this
 * system left open, per §3.7.
 *
 * <p>Runs at 00:05, five minutes <b>before</b> {@code VENCEN_FICHAS} at 00:10, and the
 * order is not a formatting detail. The expiry sweep decides which fichas missed their
 * deadline and marks them {@code PAYMENT_EXPIRED}; if reconciliation ran after it, a
 * payment captured at 23:59 the night before would be found after the ficha had already
 * been expired, and the system would be choosing to believe the deadline over the money
 * actually taken. Reconciling first means every capture that happened is recorded before
 * the deadline check ever looks at the ficha.
 *
 * <p>Like {@code ExpireStaleFichaPaymentsJob}, this class only schedules and only logs;
 * the decisions belong to the use case.
 */
@Component
public class ReconcileFichaPaymentsJob {

	private static final Logger log = LoggerFactory.getLogger(ReconcileFichaPaymentsJob.class);

	private final ReconcileFichaPaymentsUseCase reconcileFichaPaymentsUseCase;

	public ReconcileFichaPaymentsJob(ReconcileFichaPaymentsUseCase reconcileFichaPaymentsUseCase) {
		this.reconcileFichaPaymentsUseCase = reconcileFichaPaymentsUseCase;
	}

	@Scheduled(cron = "0 5 0 * * *")
	public void reconcilePayments() {
		ReconciliationResult result = reconcileFichaPaymentsUseCase.reconcile();

		// Logged unconditionally when something failed, even if nothing was settled: a
		// night where the bank was unreachable must not look identical to a night where
		// there was nothing to do.
		if (result.changedAnything() || result.failedAttempts() > 0) {
			log.info("ReconcileFichaPaymentsJob: {} pago(s) recuperados, {} lugar(es) liberado(s), "
					+ "{} intento(s) cerrado(s), {} en espera, {} con fallo", result.settledCaptures(),
					result.releasedSlots(), result.closedRows(), result.heldAttempts(), result.failedAttempts());
		}
	}
}