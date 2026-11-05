package mx.edu.utez.sisa.finance.domain.port.out;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;

/**
 * Tells Finanzas about a new alert. Called inside the transaction that raised it and
 * delivered after it commits, so a rolled-back alert is never mailed.
 */
public interface FinanceAlertNotifier {

	void alertRaised(FinanceAlert alert);
}
