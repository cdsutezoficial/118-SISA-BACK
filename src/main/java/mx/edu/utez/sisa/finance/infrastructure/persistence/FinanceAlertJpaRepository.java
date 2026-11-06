package mx.edu.utez.sisa.finance.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.utez.sisa.finance.domain.model.FinanceAlert;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertStatus;
import mx.edu.utez.sisa.finance.domain.model.FinanceAlertType;

public interface FinanceAlertJpaRepository extends JpaRepository<FinanceAlert, UUID> {

	Optional<FinanceAlert> findByTypeAndOrderId(FinanceAlertType type, String orderId);

	List<FinanceAlert> findByStatusOrderByCreatedAtDesc(FinanceAlertStatus status);

	List<FinanceAlert> findAllByOrderByCreatedAtDesc();
}
