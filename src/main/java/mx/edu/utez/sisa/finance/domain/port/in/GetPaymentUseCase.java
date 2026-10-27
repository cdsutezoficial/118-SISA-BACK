package mx.edu.utez.sisa.finance.domain.port.in;

import java.util.UUID;

/**
 * Reads a single adeudo with its abonos, backing
 * {@code GET /finance/payments/{id}} ("Detalle con abonos" — §5.4). Returns
 * the same projection as the account statement so the two never diverge.
 */
public interface GetPaymentUseCase {

	GetAccountStatementUseCase.StatementPayment getPayment(UUID id);
}
