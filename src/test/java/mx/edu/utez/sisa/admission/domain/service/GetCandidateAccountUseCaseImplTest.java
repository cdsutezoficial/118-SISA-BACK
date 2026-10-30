package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateProgramChange;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateAccountUseCase.CandidateAccount;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateAccountUseCase.ChargeRole;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateAccountPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateProgramChangeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCandidateAccountUseCaseImplTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();

	private static final UUID OLD_CONFIG = UUID.randomUUID();

	private static final UUID NEW_CONFIG = UUID.randomUUID();

	private static final UUID FIRST = UUID.randomUUID();

	private static final UUID SECOND = UUID.randomUUID();

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CandidateAccountPort candidateAccountPort;

	@Mock
	private CandidateProgramChangeRepository programChangeRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private GetCandidateAccountUseCaseImpl useCase;

	private Candidate candidate;

	@BeforeEach
	void setUp() {
		useCase = new GetCandidateAccountUseCaseImpl(candidateRepository, candidateAccountPort,
				programChangeRepository, programAdmissionConfigQueryPort);
		candidate = new Candidate(UUID.randomUUID(), NEW_CONFIG, "ADM-2026-000002", true, true, null);
		ReflectionTestUtils.setField(candidate, "id", CANDIDATE_ID);
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
		lenient().when(programAdmissionConfigQueryPort.findById(any())).thenAnswer(inv -> Optional.of(
				new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(inv.getArgument(0), null, null,
						OLD_CONFIG.equals(inv.getArgument(0)) ? "Software" : "Diseño", null, null, null, null, null,
						40)));
	}

	@Test
	void aCheaperCareerLeavesTheOldFichaAsHistoryAndTheRestAsCredit() {
		candidate.adoptFichaPayment(SECOND);
		when(candidateAccountPort.findCharges(CANDIDATE_ID)).thenReturn(List.of(
				charge(FIRST, "REF-ADM-2026-000002", "1750.00", "2026-10-29T21:01:00Z"),
				charge(SECOND, "REF-ADM-2026-000002-C3", "1500.00", "2026-10-29T21:17:00Z")));
		when(candidateAccountPort.findCredits(CANDIDATE_ID)).thenReturn(List.of(
				new CandidateAccountPort.Credit(new BigDecimal("250.00"), "AVAILABLE", FIRST, null,
						Instant.parse("2026-10-29T21:17:00Z"))));
		when(programChangeRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(List.of(
				change(FIRST, SECOND, "1750.00", "1500.00", "0", "250.00")));

		CandidateAccount account = useCase.get(CANDIDATE_ID);

		assertThat(account.availableCredit()).isEqualByComparingTo("250.00");
		assertThat(account.charges()).extracting(c -> c.role())
				.containsExactly(ChargeRole.CURRENT_FICHA, ChargeRole.HISTORY);
		assertThat(account.charges().get(0).referenceNumber()).isEqualTo("REF-ADM-2026-000002-C3");
		assertThat(account.credits().get(0).originReference()).isEqualTo("REF-ADM-2026-000002");
		assertThat(account.programChanges().get(0).fromProgramName()).isEqualTo("Software");
		assertThat(account.programChanges().get(0).toProgramName()).isEqualTo("Diseño");
	}

	@Test
	void aPendingDifferenceComesFirst() {
		candidate.adoptFichaPayment(FIRST);
		candidate.awaitProgramChangePayment(SECOND);
		when(candidateAccountPort.findCharges(CANDIDATE_ID)).thenReturn(List.of(
				charge(FIRST, "REF-ADM-2026-000001", "1750.00", "2026-10-28T16:57:00Z"),
				charge(SECOND, "REF-ADM-2026-000001-C2", "1850.00", "2026-10-29T20:47:00Z")));
		when(candidateAccountPort.findCredits(CANDIDATE_ID)).thenReturn(List.of());
		when(programChangeRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(List.of());

		CandidateAccount account = useCase.get(CANDIDATE_ID);

		assertThat(account.charges()).extracting(c -> c.role())
				.containsExactly(ChargeRole.CURRENT_FICHA, ChargeRole.PENDING_DIFFERENCE);
		assertThat(account.availableCredit()).isEqualByComparingTo("0");
	}

	private static CandidateAccountPort.Charge charge(UUID id, String reference, String amount, String createdAt) {
		BigDecimal value = new BigDecimal(amount);
		return new CandidateAccountPort.Charge(id, "ADM-FICHA", reference, "Software", "PAID", value, null, null,
				value, value, BigDecimal.ZERO, null, Instant.parse(createdAt), List.of());
	}

	private static CandidateProgramChange change(UUID from, UUID to, String paid, String newAmount, String toPay,
			String creditLeft) {
		return new CandidateProgramChange(CANDIDATE_ID, OLD_CONFIG, NEW_CONFIG, true, from, to, new BigDecimal(paid),
				new BigDecimal(paid), new BigDecimal(newAmount), new BigDecimal(toPay), new BigDecimal(creditLeft),
				UUID.randomUUID(), Instant.parse("2026-10-29T21:17:00Z"));
	}
}
