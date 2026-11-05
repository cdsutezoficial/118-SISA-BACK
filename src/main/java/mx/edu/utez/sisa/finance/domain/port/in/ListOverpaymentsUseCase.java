package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.CreditStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lista de excedentes para Finanzas (E1): dinero que el banco capturó sin
 * liquidar nada, ya convertido en saldo a favor de su dueño. Trae lo necesario
 * para revisarlo sin leer logs: de quién es, de qué cargo salió y si ya se usó.
 */
public interface ListOverpaymentsUseCase {

	List<OverpaymentView> list();

	record OverpaymentView(UUID creditId, UUID candidateId, String personName, String curp, BigDecimal amount,
			CreditStatus status, String originReference, String appliedToReference, String externalOrderId,
			Instant createdAt) {
	}
}
