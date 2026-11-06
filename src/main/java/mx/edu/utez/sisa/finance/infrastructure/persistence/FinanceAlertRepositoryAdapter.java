package mx.edu.utez.sisa.finance.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertStatus;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertType;
import mx.edu.utez.sisa.finance.domain.port.out.FinanceAlertRepository;

@Component
public class FinanceAlertRepositoryAdapter implements FinanceAlertRepository {

	private final FinanceAlertJpaRepository jpaRepository;

	public FinanceAlertRepositoryAdapter(FinanceAlertJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public FinanceAlert save(FinanceAlert alert) {
		return jpaRepository.save(alert);
	}

	@Override
	public Optional<FinanceAlert> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<FinanceAlert> findByTypeAndOrderId(FinanceAlertType type, String orderId) {
		return jpaRepository.findByTypeAndOrderId(type, orderId);
	}

	@Override
	public List<FinanceAlert> findByStatus(FinanceAlertStatus status) {
		return status == null ? jpaRepository.findAllByOrderByCreatedAtDesc()
				: jpaRepository.findByStatusOrderByCreatedAtDesc(status);
	}
}
