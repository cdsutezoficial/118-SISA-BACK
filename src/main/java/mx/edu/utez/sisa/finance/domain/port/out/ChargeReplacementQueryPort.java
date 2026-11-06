package mx.edu.utez.sisa.finance.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only out-port: which charge took the place of a paid one when the
 * candidate changed career. A career change on a paid ficha never rewrites the
 * paid charge; it raises a new one and keeps the old one as history. That old
 * charge is no longer the one being collected, so a discount must not land on
 * it (it would mint saldo a favor on money that already moved).
 */
@FunctionalInterface
public interface ChargeReplacementQueryPort {

	/**
	 * The charge raised by the career change that replaced {@code paymentId}, or
	 * empty when no career change replaced it.
	 */
	Optional<UUID> findReplacingChargeId(UUID paymentId);
}
