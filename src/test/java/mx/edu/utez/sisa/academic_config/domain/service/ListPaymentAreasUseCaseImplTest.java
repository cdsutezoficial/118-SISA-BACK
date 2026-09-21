package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase.ListPaymentAreasQuery;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase.ListPaymentAreasResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.ListPaymentAreasUseCase.PaymentAreaSummary;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository.PaymentAreaSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository.PaymentAreaSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPaymentAreasUseCaseImplTest {

	@Mock
	private PaymentAreaRepository paymentAreaRepository;

	private ListPaymentAreasUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ListPaymentAreasUseCaseImpl(paymentAreaRepository);
	}

	@Test
	void listPaymentAreas_defaultPaginationUsesDefaultSize() {
		List<PaymentArea> content = List.of(newArea("Colegiaturas", "COL"), newArea("Inscripcion", "INS"));
		when(paymentAreaRepository.search(any())).thenReturn(new PaymentAreaSearchPage(content, 22, 2));

		ListPaymentAreasResult result = useCase.listPaymentAreas(new ListPaymentAreasQuery(null, null, 0, 0));

		ArgumentCaptor<PaymentAreaSearchCriteria> captor = ArgumentCaptor.forClass(PaymentAreaSearchCriteria.class);
		verify(paymentAreaRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListPaymentAreasQuery.DEFAULT_PAGE_SIZE);
		assertThat(captor.getValue().page()).isZero();
		assertThat(result.totalElements()).isEqualTo(22);
		assertThat(result.totalPages()).isEqualTo(2);
	}

	@Test
	void listPaymentAreas_mapsEntitiesToSummaries() {
		List<PaymentArea> content = List.of(newArea("Colegiaturas", "COL"), newArea("Inscripcion", "INS"));
		when(paymentAreaRepository.search(any())).thenReturn(new PaymentAreaSearchPage(content, 2, 1));

		ListPaymentAreasResult result = useCase.listPaymentAreas(new ListPaymentAreasQuery(null, null, 0, 20));

		assertThat(result.items()).hasSize(2).extracting(PaymentAreaSummary::name)
				.containsExactlyInAnyOrder("Colegiaturas", "Inscripcion");
		assertThat(result.items()).extracting(PaymentAreaSummary::description).containsOnly("Descripcion");
	}

	@Test
	void listPaymentAreas_oversizedPageIsCappedAtMax() {
		when(paymentAreaRepository.search(any())).thenReturn(new PaymentAreaSearchPage(List.of(), 0, 0));

		useCase.listPaymentAreas(new ListPaymentAreasQuery(null, null, 0, 500));

		ArgumentCaptor<PaymentAreaSearchCriteria> captor = ArgumentCaptor.forClass(PaymentAreaSearchCriteria.class);
		verify(paymentAreaRepository).search(captor.capture());
		assertThat(captor.getValue().size()).isEqualTo(ListPaymentAreasQuery.MAX_PAGE_SIZE);
	}

	@Test
	void listPaymentAreas_negativePageIsNormalizedToZero() {
		when(paymentAreaRepository.search(any())).thenReturn(new PaymentAreaSearchPage(List.of(), 0, 0));

		useCase.listPaymentAreas(new ListPaymentAreasQuery(null, null, -5, 20));

		ArgumentCaptor<PaymentAreaSearchCriteria> captor = ArgumentCaptor.forClass(PaymentAreaSearchCriteria.class);
		verify(paymentAreaRepository).search(captor.capture());
		assertThat(captor.getValue().page()).isZero();
	}

	private static PaymentArea newArea(String name, String code) {
		return new PaymentArea(name, code, "Descripcion");
	}
}
