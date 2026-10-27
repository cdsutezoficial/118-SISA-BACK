package mx.edu.utez.sisa.finance.domain.port.in;

import java.util.UUID;

/**
 * Cancels an open charge that nobody paid — e.g. a ficha whose payment window
 * lapsed — so it stops counting as an active debt. A charge with abonos, or
 * already closed, is left untouched (idempotent; returns {@code false}).
 */
public interface CancelDebtUseCase {

	boolean cancel(UUID paymentId);
}
