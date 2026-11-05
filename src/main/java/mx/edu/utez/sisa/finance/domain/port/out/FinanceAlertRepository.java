package mx.edu.utez.sisa.finance.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertStatus;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertType;

public interface FinanceAlertRepository {

	FinanceAlert save(FinanceAlert alert);

	Optional<FinanceAlert> findById(UUID id);

	Optional<FinanceAlert> findByTypeAndOrderId(FinanceAlertType type, String orderId);

	/** Newest first; every alert when {@code status} is {@code null}. */
	List<FinanceAlert> findByStatus(FinanceAlertStatus status);
}
