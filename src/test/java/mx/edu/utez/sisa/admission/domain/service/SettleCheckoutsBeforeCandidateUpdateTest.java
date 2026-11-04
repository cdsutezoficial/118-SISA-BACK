package mx.edu.utez.sisa.admission.domain.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.SeleccionCarrera;
import mx.edu.utez.sisa.admission.domain.port.in.UpdateCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.shared.exception.PaymentInProgressException;

/** E5: the bank is asked before a career change, and only before one. */
@ExtendWith(MockitoExtension.class)
class SettleCheckoutsBeforeCandidateUpdateTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final UUID CURRENT_CONFIG = UUID.randomUUID();

	private static final UUID ACTING_USER = UUID.randomUUID();

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private PendingCheckoutGuard pendingCheckoutGuard;

	@Mock
	private UpdateCandidateUseCase transactionalUpdate;

	private SettleCheckoutsBeforeCandidateUpdate useCase;

	private Candidate candidate;

	@BeforeEach
	void setUp() {
		useCase = new SettleCheckoutsBeforeCandidateUpdate(candidateRepository, pendingCheckoutGuard,
				transactionalUpdate);
		candidate = new Candidate(UUID.randomUUID(), CURRENT_CONFIG, "ADM-2026-000001", true, true, null);
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
	}

	private static RegisterCandidateCommand choosing(UUID configId) {
		return new RegisterCandidateCommand(null, null, null, null, null, null,
				new SeleccionCarrera(configId, null, true), false);
	}

	@Test
	void aCareerChangeSettlesOpenCheckoutsBeforeSaving() {
		RegisterCandidateCommand command = choosing(UUID.randomUUID());

		useCase.update(CANDIDATE_ID, command, ACTING_USER);

		InOrder order = inOrder(pendingCheckoutGuard, transactionalUpdate);
		order.verify(pendingCheckoutGuard).settleBeforeProgramChange(candidate);
		order.verify(transactionalUpdate).update(CANDIDATE_ID, command, ACTING_USER);
	}

	@Test
	void anEditThatKeepsTheCareerDoesNotAskTheBank() {
		RegisterCandidateCommand command = choosing(CURRENT_CONFIG);

		useCase.update(CANDIDATE_ID, command, ACTING_USER);

		verify(pendingCheckoutGuard, never()).settleBeforeProgramChange(any());
		verify(transactionalUpdate).update(CANDIDATE_ID, command, ACTING_USER);
	}

	@Test
	void aPaymentInFlightStopsTheSave() {
		doThrow(new PaymentInProgressException("en curso")).when(pendingCheckoutGuard)
				.settleBeforeProgramChange(candidate);

		assertThatThrownBy(() -> useCase.update(CANDIDATE_ID, choosing(UUID.randomUUID()), ACTING_USER))
				.isInstanceOf(PaymentInProgressException.class);
		verify(transactionalUpdate, never()).update(any(), any(), any());
	}
}
