package mx.edu.utez.sisa.admission.infrastructure.web.dto;

import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateAccountUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateAccountUseCase.ChargeView;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateAccountUseCase.CreditView;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateAccountUseCase.ProgramChangeView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** {@code GET /candidates/{id}/account}: pagos, saldo a favor y cambios de carrera del candidato. */
public record CandidateAccountResponse(UUID candidateId, BigDecimal availableCredit, List<ChargeView> charges,
		List<CreditView> credits, List<ProgramChangeView> programChanges) {

	public static CandidateAccountResponse from(GetCandidateAccountUseCase.CandidateAccount account) {
		return new CandidateAccountResponse(account.candidateId(), account.availableCredit(), account.charges(),
				account.credits(), account.programChanges());
	}
}
