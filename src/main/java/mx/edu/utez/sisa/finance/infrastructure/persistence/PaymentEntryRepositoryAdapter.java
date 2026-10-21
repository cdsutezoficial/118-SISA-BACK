package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.model.PaymentEntry;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentEntryRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * JPA-backed {@link PaymentEntryRepository} adapter. {@code sumAmountByPaymentId}
 * maps the empty-sum {@code null} to a scaled zero so callers never receive
 * {@code null} for "no abonos yet".
 */
@Component
public class PaymentEntryRepositoryAdapter implements PaymentEntryRepository {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private final PaymentEntryJpaRepository jpaRepository;

	public PaymentEntryRepositoryAdapter(PaymentEntryJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public PaymentEntry save(PaymentEntry entry) {
		return jpaRepository.save(entry);
	}

	@Override
	public List<PaymentEntry> findByPaymentIdOrderByPaidAtAsc(UUID paymentId) {
		return jpaRepository.findByPaymentIdOrderByPaidAtAsc(paymentId);
	}

	@Override
	public BigDecimal sumAmountByPaymentId(UUID paymentId) {
		BigDecimal sum = jpaRepository.sumAmountByPaymentId(paymentId);
		return sum == null ? ZERO : sum;
	}
}
