package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.out.ChargeReplacementQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.BenefitNotEditableException;

import java.util.Optional;
import java.util.UUID;

/**
 * Only the charge being collected takes a discount: the difference left by a
 * career change if there is one, otherwise the ficha. A paid charge that a
 * career change replaced keeps its money as history; that money already moved
 * to the new charge as saldo a favor, so a discount on the old one would hand
 * it back a second time.
 */
public class ReplacedChargeGuard {

	static final String APPLY_TO_DIFFERENCE =
			"Este cargo se reemplazó por el cambio de carrera; aplica el descuento a la diferencia.";

	static final String APPLY_TO_NEW_CHARGE =
			"Este cargo se reemplazó por el cambio de carrera; aplica el descuento al cargo de la carrera nueva.";

	/** A guard against a broken history (a charge pointing back to itself). */
	private static final int MAX_CHAIN = 50;

	private final ChargeReplacementQueryPort chargeReplacementQueryPort;

	private final PaymentRepository paymentRepository;

	public ReplacedChargeGuard(ChargeReplacementQueryPort chargeReplacementQueryPort,
			PaymentRepository paymentRepository) {
		this.chargeReplacementQueryPort = chargeReplacementQueryPort;
		this.paymentRepository = paymentRepository;
	}

	public boolean isReplaced(UUID paymentId) {
		return paymentId != null && chargeReplacementQueryPort.findReplacingChargeId(paymentId).isPresent();
	}

	/**
	 * @throws BenefitNotEditableException when a career change replaced the
	 *         charge; the message points to the charge to use instead
	 */
	public void requireCurrent(UUID paymentId) {
		if (paymentId == null) {
			return;
		}
		Optional<UUID> next = chargeReplacementQueryPort.findReplacingChargeId(paymentId);
		if (next.isEmpty()) {
			return;
		}
		// A → B → C: the one that counts is the last one.
		UUID current = next.get();
		for (int i = 0; i < MAX_CHAIN; i++) {
			Optional<UUID> after = chargeReplacementQueryPort.findReplacingChargeId(current);
			if (after.isEmpty() || after.get().equals(current)) {
				break;
			}
			current = after.get();
		}
		boolean differenceOpen = paymentRepository.findById(current).map(ReplacedChargeGuard::isOpen).orElse(false);
		throw new BenefitNotEditableException(differenceOpen ? APPLY_TO_DIFFERENCE : APPLY_TO_NEW_CHARGE);
	}

	private static boolean isOpen(Payment charge) {
		return charge.getStatus() == PaymentStatus.PENDING || charge.getStatus() == PaymentStatus.PARTIAL;
	}
}
