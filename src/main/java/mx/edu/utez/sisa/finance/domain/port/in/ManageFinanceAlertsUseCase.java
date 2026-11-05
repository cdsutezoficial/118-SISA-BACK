package mx.edu.utez.sisa.finance.domain.port.in;

import java.util.List;
import java.util.UUID;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertStatus;

/** The Finanzas review screen: list the alerts and mark them as reviewed. */
public interface ManageFinanceAlertsUseCase {

	/** Newest first; every alert when {@code status} is {@code null}. */
	List<FinanceAlert> list(FinanceAlertStatus status);

	FinanceAlert resolve(UUID alertId, UUID userId, String note);
}
