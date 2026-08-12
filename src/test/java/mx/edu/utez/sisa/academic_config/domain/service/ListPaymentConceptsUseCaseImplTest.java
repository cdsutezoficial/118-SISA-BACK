package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase.ListPaymentConceptsQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase.ListPaymentConceptsResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentConceptsUseCase.PaymentConceptSummary;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository.PaymentConceptSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository.PaymentConceptSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPaymentConceptsUseCaseImplTest {

	@Mock
	private PaymentConceptRepository paymentConceptRepository;

	private ListPaymentConceptsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListPaymentConceptsUseCaseImpl(paymentConceptRepository);
	}

	@Test
	void listPaymentConcepts_defaultPaginationUsesDefaultSize() {
		List<PaymentConcept> content = List.of(newConcept("Inscripcion"), newConcept("Reinscripcion"));
		when(paymentConceptRepository.search(any())).thenReturn(new PaymentConceptSearchPage(content, 22, 2));

		ListPaymentConceptsResult result = useCase
				.listPaymentConcepts(new ListPaymentConceptsQuery(null, null, 0, 0));

		ArgumentCaptor<PaymentConceptSearchCriteria> captor = ArgumentCaptor.forClass(PaymentConceptSearchCriteria.class);
		verify(paymentConceptRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListPaymentConceptsQuery.DEFAULT_PAGE_SIZE);
		assertThat(captor.getValue().page()).isZero();
		assertThat(result.totalElements()).isEqualTo(22);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void listPaymentConcepts_mapsEntitiesToSummaries() {
		List<PaymentConcept> content = List.of(newConcept("Inscripcion"), newConcept("Reinscripcion"));
		when(paymentConceptRepository.search(any())).thenReturn(new PaymentConceptSearchPage(content, 2, 1));

		ListPaymentConceptsResult result = useCase
				.listPaymentConcepts(new ListPaymentConceptsQuery(null, null, 0, 20));

		assertThat(result.items()).hasSize(2).extracting(PaymentConceptSummary::name)
				.containsExactlyInAnyOrder("Inscripcion", "Reinscripcion");
	}

	@Test
	void listPaymentConcepts_oversizedPageIsCappedAtMax() {
		when(paymentConceptRepository.search(any())).thenReturn(new PaymentConceptSearchPage(List.of(), 0, 0));

		useCase.listPaymentConcepts(new ListPaymentConceptsQuery(null, null, 0, 500));

		ArgumentCaptor<PaymentConceptSearchCriteria> captor = ArgumentCaptor.forClass(PaymentConceptSearchCriteria.class);
		verify(paymentConceptRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListPaymentConceptsQuery.MAX_PAGE_SIZE);
	}

	@Test
	void listPaymentConcepts_negativePageIsNormalizedToZero() {
		when(paymentConceptRepository.search(any())).thenReturn(new PaymentConceptSearchPage(List.of(), 0, 0));

		useCase.listPaymentConcepts(new ListPaymentConceptsQuery(null, null, -5, 20));

		ArgumentCaptor<PaymentConceptSearchCriteria> captor = ArgumentCaptor.forClass(PaymentConceptSearchCriteria.class);
		verify(paymentConceptRepository).search(captor.capture());
		assertThat(captor.getValue().page()).isZero();
	}

	private static PaymentConcept newConcept(String name) {
		return new PaymentConcept(name, "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true, false, 1, 2,
				true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}
}
