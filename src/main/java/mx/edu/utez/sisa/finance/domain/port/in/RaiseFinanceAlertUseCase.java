package mx.edu.utez.sisa.finance.domain.port.in;

import java.math.BigDecimal;
import java.util.UUID;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlertType;

/** Records a payment Finanzas has to review, once per kind and order (E4). */
public interface RaiseFinanceAlertUseCase {

	/** @return {@code true} when a new alert was opened, {@code false} when it already existed */
	boolean raise(RaiseAlertCommand command);

	record RaiseAlertCommand(FinanceAlertType type, UUID candidateId, UUID paymentId, String orderId, String folio,
			BigDecimal amount, String detail) {
	}
}
