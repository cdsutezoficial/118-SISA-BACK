package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyPaidException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmAdmissionPaymentUseCaseImplTest {

	private static final BigDecimal AMOUNT = new BigDecimal("500.00");

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private AdmissionPaymentRepository paymentRepository;

	private ConfirmAdmissionPaymentUseCaseImpl useCase;

	private UUID candidateId;
	private Candidate candidate;
	private AdmissionPayment payment;

	@BeforeEach
	void setUp() {
		useCase = new ConfirmAdmissionPaymentUseCaseImpl(candidateRepository, paymentRepository);
		candidateId = UUID.randomUUID();
		candidate = new Candidate(UUID.randomUUID(), UUID.randomUUID(), "ADM-2026-000001", true, true, null);
		ReflectionTestUtils.setField(candidate, "id", candidateId);
		payment = new AdmissionPayment(candidateId, AdmissionPaymentConcept.ADMISSION_FICHA, AMOUNT,
				"REF-20260922-000001", LocalDate.now().plusDays(10));
	}

	@Test
	void confirm_marksPaymentAndCandidatePaidWithReceipt() {
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(paymentRepository.findByCandidateId(candidateId)).thenReturn(Optional.of(payment));
		when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(candidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var result = useCase.confirm(candidateId);

		assertThat(result.candidateStatus()).isEqualTo(CandidateStatus.PAID);
		assertThat(result.receiptNumber()).isNotNull().startsWith("REC-");
		assertThat(result.amount()).isEqualByComparingTo(AMOUNT);
		assertThat(payment.getPaymentStatus()).isEqualTo(AdmissionPaymentStatus.PAID);
		assertThat(payment.getPaidAt()).isNotNull();

		ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
		verify(candidateRepository).save(candidateCaptor.capture());
		assertThat(candidateCaptor.getValue().getStatus()).isEqualTo(CandidateStatus.PAID);
		assertThat(candidateCaptor.getValue().getPaidAt()).isNotNull();
	}

	@Test
	void confirm_unknownCandidateThrowsNotFound() {
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.confirm(candidateId)).isInstanceOf(CandidateNotFoundException.class);
	}

	@Test
	void confirm_alreadyPaidThrowsConflict() {
		payment.markPaid("REC-20260922-000001");
		candidate.markPaid();
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
		when(paymentRepository.findByCandidateId(candidateId)).thenReturn(Optional.of(payment));

		assertThatThrownBy(() -> useCase.confirm(candidateId)).isInstanceOf(CandidateAlreadyPaidException.class);
	}
}