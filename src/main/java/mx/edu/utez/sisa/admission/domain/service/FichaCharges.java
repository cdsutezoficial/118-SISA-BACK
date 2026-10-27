package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;

import java.util.Optional;

/**
 * Reads a candidate's ficha — which, since 2026-10-23, is their finance
 * charge linked by {@link Candidate#getFichaPaymentId()}.
 */
public final class FichaCharges {

	private FichaCharges() {
	}

	/** Empty when the candidate has no charge linked (or it no longer exists). */
	public static Optional<FichaPaymentPort.FichaCharge> of(Candidate candidate, FichaPaymentPort fichaPaymentPort) {
		if (candidate == null || candidate.getFichaPaymentId() == null) {
			return Optional.empty();
		}
		return fichaPaymentPort.findCharge(candidate.getFichaPaymentId());
	}
}
