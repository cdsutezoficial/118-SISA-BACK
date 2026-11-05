package mx.edu.utez.sisa.admission.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FinanceAlertPort;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertType;
import mx.edu.utez.sisa.finance.domain.port.in.RaiseFinanceAlertUseCase;

/**
 * {@link FinanceAlertPort} over finance's alert use case. Adds the ficha's folio so
 * the alert reads without a join, and swallows failures: the alert runs in its own
 * transaction and is never worth failing a payment over.
 */
@Component
public class FinanceAlertAdapter implements FinanceAlertPort {

	private static final Logger log = LoggerFactory.getLogger(FinanceAlertAdapter.class);

	private final RaiseFinanceAlertUseCase raiseFinanceAlertUseCase;

	private final CandidateRepository candidateRepository;

	public FinanceAlertAdapter(RaiseFinanceAlertUseCase raiseFinanceAlertUseCase,
			CandidateRepository candidateRepository) {
		this.raiseFinanceAlertUseCase = raiseFinanceAlertUseCase;
		this.candidateRepository = candidateRepository;
	}

	@Override
	public void raise(Kind kind, UUID candidateId, UUID paymentId, String orderId, BigDecimal amount,
			String detail) {
		try {
			String folio = candidateId == null ? null
					: candidateRepository.findById(candidateId).map(Candidate::getFolio).orElse(null);
			raiseFinanceAlertUseCase.raise(new RaiseFinanceAlertUseCase.RaiseAlertCommand(
					FinanceAlertType.valueOf(kind.name()), candidateId, paymentId, orderId, folio, amount, detail));
		} catch (RuntimeException ex) {
			log.error("No se pudo registrar la alerta de Finanzas {} de la orden {}: {}", kind, orderId,
					ex.getMessage(), ex);
		}
	}
}
