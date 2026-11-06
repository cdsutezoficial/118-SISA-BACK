package mx.edu.utez.sisa.admission.domain.service;

import java.util.UUID;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;

/**
 * E5: before an edited ficha changes career, the bank is asked about every checkout
 * the candidate still has open, and only then is the ficha saved.
 *
 * <p>A separate step because the save is one transaction and the bank is a third
 * party: calling it with the transaction open would hold row locks for as long as the
 * gateway takes. The settling writes go through their own short transactions, so by
 * the time the save starts, a capture is already applied and a refused or abandoned
 * session is already closed.
 *
 * <p>Only a real career change asks. An edit that keeps the career never touches the
 * charge, so an open session cannot hurt it.
 */
public class SettleCheckoutsBeforeCandidateUpdate implements UpdateCandidateUseCase {

	private final CandidateRepository candidateRepository;

	private final PendingCheckoutGuard pendingCheckoutGuard;

	private final UpdateCandidateUseCase transactionalUpdate;

	public SettleCheckoutsBeforeCandidateUpdate(CandidateRepository candidateRepository,
			PendingCheckoutGuard pendingCheckoutGuard, UpdateCandidateUseCase transactionalUpdate) {
		this.candidateRepository = candidateRepository;
		this.pendingCheckoutGuard = pendingCheckoutGuard;
		this.transactionalUpdate = transactionalUpdate;
	}

	@Override
	public void update(UUID candidateId, RegisterCandidateCommand command, UUID actingUserId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));
		UUID target = command.seleccionCarrera() == null ? null : command.seleccionCarrera().admissionConfigId();
		if (target != null && !target.equals(candidate.getAdmissionConfigId())) {
			pendingCheckoutGuard.settleBeforeProgramChange(candidate);
		}
		transactionalUpdate.update(candidateId, command, actingUserId);
	}
}
