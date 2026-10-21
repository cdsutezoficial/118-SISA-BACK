package mx.edu.utez.sisa.finance.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.finance.domain.model.BenefitMode;
import mx.edu.utez.sisa.finance.domain.model.PaymentBenefit;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.QuoteCommand;
import mx.edu.utez.sisa.finance.domain.port.in.QuotePaymentUseCase.QuoteResult;
import mx.edu.utez.sisa.finance.domain.port.out.AnchorQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptQueryPort.ConceptView;
import mx.edu.utez.sisa.finance.domain.port.out.ConceptRateQueryPort;
import mx.edu.utez.sisa.finance.domain.port.out.PaymentBenefitRepository;
import mx.edu.utez.sisa.finance.shared.exception.ConceptNotFoundException;
import mx.edu.utez.sisa.finance.shared.exception.ConceptUnavailableException;
import mx.edu.utez.sisa.finance.shared.exception.InvalidPaymentDataException;
import mx.edu.utez.sisa.finance.shared.exception.NoRateAvailableException;
import mx.edu.utez.sisa.finance.shared.exception.PersonNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotePaymentUseCaseImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"),
			ZoneId.of("America/Mexico_City"));

	@Mock
	private AnchorQueryPort anchorQueryPort;

	@Mock
	private ConceptQueryPort conceptQueryPort;

	@Mock
	private ConceptRateQueryPort conceptRateQueryPort;

	@Mock
	private PaymentBenefitRepository paymentBenefitRepository;

	private QuotePaymentUseCaseImpl useCase;

	private UUID candidateId;
	private UUID programId;
	private ConceptView concept;

	@BeforeEach
	void setUp() {
		useCase = new QuotePaymentUseCaseImpl(anchorQueryPort, conceptQueryPort, conceptRateQueryPort,
				paymentBenefitRepository, new PaymentQuoter(), new BenefitResolver(), CLOCK);
		candidateId = UUID.randomUUID();
		programId = UUID.randomUUID();
		concept = new ConceptView(UUID.randomUUID(), "Colegiatura", "TUITION", PaymentConceptType.PERIODIC_QUOTA);
	}

	@Test
	void unknownAnchor_fails() {
		when(anchorQueryPort.candidateExists(candidateId)).thenReturn(false);

		assertThatThrownBy(() -> useCase.quote(command()))
				.isInstanceOf(PersonNotFoundException.class);

		verify(conceptQueryPort, never()).findSellableByCode(anyString(), any());
	}

	@Test
	void unknownConcept_fails() {
		when(anchorQueryPort.candidateExists(candidateId)).thenReturn(true);
		when(conceptQueryPort.findSellableByCode("TUITION", LocalDate.of(2026, 10, 19))).thenReturn(Optional.empty());
		when(conceptQueryPort.findByCode("TUITION")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.quote(command()))
				.isInstanceOf(ConceptNotFoundException.class);
	}

	@Test
	void conceptExistsButNotOnSale_failsAsUnavailable() {
		when(anchorQueryPort.candidateExists(candidateId)).thenReturn(true);
		when(conceptQueryPort.findSellableByCode("TUITION", LocalDate.of(2026, 10, 19))).thenReturn(Optional.empty());
		when(conceptQueryPort.findByCode("TUITION")).thenReturn(Optional.of(concept));

		assertThatThrownBy(() -> useCase.quote(command()))
				.isInstanceOf(ConceptUnavailableException.class);
	}

	@Test
	void noRateForProgram_fails() {
		when(anchorQueryPort.candidateExists(candidateId)).thenReturn(true);
		when(conceptQueryPort.findSellableByCode("TUITION", LocalDate.of(2026, 10, 19))).thenReturn(Optional.of(concept));
		when(conceptRateQueryPort.findActiveAmount(concept.id(), programId, null)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.quote(command()))
				.isInstanceOf(NoRateAvailableException.class);
	}

	@Test
	void validQuote_appliesActiveBenefit() {
		PaymentBenefit benefit = new PaymentBenefit(candidateId, null, "TUITION", null, null, BenefitMode.PERCENT,
				new BigDecimal("25.00"), null, LocalDate.of(2026, 1, 1), null, "beca", UUID.randomUUID(), "Finanzas",
				Instant.parse("2026-01-01T00:00:00Z"));
		when(anchorQueryPort.candidateExists(candidateId)).thenReturn(true);
		when(conceptQueryPort.findSellableByCode("TUITION", LocalDate.of(2026, 10, 19))).thenReturn(Optional.of(concept));
		when(conceptRateQueryPort.findActiveAmount(concept.id(), programId, null))
				.thenReturn(Optional.of(new BigDecimal("1000.00")));
		when(paymentBenefitRepository.findActiveByCandidateAndConcept(candidateId, "TUITION"))
				.thenReturn(List.of(benefit));

		QuoteResult result = useCase.quote(command());

		assertThat(result.originalAmount()).isEqualByComparingTo("1000.00");
		assertThat(result.discountAmount()).isEqualByComparingTo("250.00");
		assertThat(result.amountDue()).isEqualByComparingTo("750.00");
		assertThat(result.exempt()).isFalse();
		assertThat(result.conceptType()).isEqualTo(PaymentConceptType.PERIODIC_QUOTA);
		assertThat(result.benefit().mode()).isEqualTo(BenefitMode.PERCENT);
	}

	@Test
	void fullBenefit_quotesExempt() {
		PaymentBenefit benefit = new PaymentBenefit(candidateId, null, "TUITION", null, null, BenefitMode.FULL, null,
				null, LocalDate.of(2026, 1, 1), null, "exención", UUID.randomUUID(), "Finanzas",
				Instant.parse("2026-01-01T00:00:00Z"));
		when(anchorQueryPort.candidateExists(candidateId)).thenReturn(true);
		when(conceptQueryPort.findSellableByCode("TUITION", LocalDate.of(2026, 10, 19))).thenReturn(Optional.of(concept));
		when(conceptRateQueryPort.findActiveAmount(concept.id(), programId, null))
				.thenReturn(Optional.of(new BigDecimal("1000.00")));
		when(paymentBenefitRepository.findActiveByCandidateAndConcept(candidateId, "TUITION"))
				.thenReturn(List.of(benefit));

		QuoteResult result = useCase.quote(command());

		assertThat(result.amountDue()).isEqualByComparingTo("0.00");
		assertThat(result.exempt()).isTrue();
	}

	@Test
	void missingProgramId_failsValidation() {
		QuoteCommand invalid = new QuoteCommand(candidateId, null, "TUITION", null, null, null, null, null);

		assertThatThrownBy(() -> useCase.quote(invalid)).isInstanceOf(InvalidPaymentDataException.class);
	}

	private QuoteCommand command() {
		return new QuoteCommand(candidateId, null, "TUITION", null, null, programId, null, null);
	}
}
