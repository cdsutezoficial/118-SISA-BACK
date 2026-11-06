package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Solo para ponerle referencia a los saldos a favor de la persona que salieron de
 * otra ficha suya (F9): esos cargos no están en el estado de cuenta del candidato.
 */
public interface PaymentReferenceLookupJpaRepository extends JpaRepository<Payment, UUID> {
}
