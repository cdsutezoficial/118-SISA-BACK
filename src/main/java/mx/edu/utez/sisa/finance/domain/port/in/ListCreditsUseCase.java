package mx.edu.utez.sisa.finance.domain.port.in;

import mx.edu.utez.sisa.finance.domain.model.CreditStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Saldos a favor de un candidato: los disponibles y los que ya se aplicaron,
 * con el cargo que los originó. Un saldo aplicado en parte conserva lo que le
 * queda como {@code amount}.
 */
public interface ListCreditsUseCase {

	List<CreditView> listByCandidate(UUID candidateId);

	record CreditView(UUID id, BigDecimal amount, CreditStatus status, UUID originPaymentId,
			UUID appliedToPaymentId, Instant createdAt) {
	}
}
