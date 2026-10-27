package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.in.RaiseDebtUseCase.RaiseDebtCommand;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort.ConceptView;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentRepository;
import mx.edu.utez.sisa.finance.shared.exception.ConceptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaiseDebtUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T18:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private AnchorQueryPort anchorQueryPort;

	@Mock
	private ConceptQueryPort conceptQueryPort;

	private RaiseDebtUseCaseImpl useCase;

	private UUID candidateId;
	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new RaiseDebtUseCaseImpl(paymentRepository, anchorQueryPort, conceptQueryPort, CLOCK);
		candidateId = UUID.randomUUID();
		programId = UUID.randomUUID();
		when(anchorQueryPort.candidateExists(candidateId)).thenReturn(true);
	}

	private RaiseDebtCommand command(String code) {
		return new RaiseDebtCommand(candidateId, null, code, programId, null, null, PaymentOrigin.ADMISSION,
				null, null);
	}

	@Test
	void raisesAHeaderWithTheConceptsRealCodeAndNoAmounts() {
		when(conceptQueryPort.findByCode("ficha-ing")).thenReturn(Optional.of(
				new ConceptView(UUID.randomUUID(), "Ficha", "FICHA-ING", PaymentConceptType.ADMISSION)));
		when(paymentRepository.findByCandidateIdAndConceptCode(candidateId, "FICHA-ING")).thenReturn(List.of());
		when(paymentRepository.save(any())).thenAnswer(invocation -> {
			Payment saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
			return saved;
		});

		UUID id = useCase.raise(command("ficha-ing"));

		ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
		verify(paymentRepository).save(saved.capture());
		assertThat(id).isEqualTo(saved.getValue().getId());
		assertThat(saved.getValue().getConceptCode()).isEqualTo("FICHA-ING");
		assertThat(saved.getValue().getOriginalAmount()).isNull();
		assertThat(saved.getValue().getAmountDue()).isNull();
		assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
		assertThat(saved.getValue().getProgramId()).isEqualTo(programId);
	}

	/** The ficha's reference lives on the charge since there is no admission table any more. */
	@Test
	void theReferenceTheFlowGivesIsStoredOnTheCharge() {
		when(conceptQueryPort.findByCode("FICHA-ING")).thenReturn(Optional.of(
				new ConceptView(UUID.randomUUID(), "Ficha", "FICHA-ING", PaymentConceptType.ADMISSION)));
		when(paymentRepository.findByCandidateIdAndConceptCode(candidateId, "FICHA-ING")).thenReturn(List.of());
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.raise(new RaiseDebtCommand(candidateId, null, "FICHA-ING", programId, null, null,
				PaymentOrigin.ADMISSION, null, "REF-ADM-2026-000007"));

		ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
		verify(paymentRepository).save(saved.capture());
		assertThat(saved.getValue().getReferenceNumber()).isEqualTo("REF-ADM-2026-000007");
	}

	@Test
	void anOpenChargeForTheSameConceptIsReturnedInsteadOfASecondOne() {
		when(conceptQueryPort.findByCode("FICHA-ING")).thenReturn(Optional.of(
				new ConceptView(UUID.randomUUID(), "Ficha", "FICHA-ING", PaymentConceptType.ADMISSION)));
		Payment open = new Payment(candidateId, null, "FICHA-ING", null, null, programId, null, null, null,
				PaymentStatus.PENDING, PaymentOrigin.ADMISSION, null, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(open, "id", UUID.randomUUID());
		when(paymentRepository.findByCandidateIdAndConceptCode(candidateId, "FICHA-ING")).thenReturn(List.of(open));

		assertThat(useCase.raise(command("FICHA-ING"))).isEqualTo(open.getId());
		verify(paymentRepository, never()).save(any());
	}

	@Test
	void anUnknownConceptIsRefused() {
		when(conceptQueryPort.findByCode("ADMISSION_FICHA")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.raise(command("ADMISSION_FICHA")))
				.isInstanceOf(ConceptNotFoundException.class);
	}
}
