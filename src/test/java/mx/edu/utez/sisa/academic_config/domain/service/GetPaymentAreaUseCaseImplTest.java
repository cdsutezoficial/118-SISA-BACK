package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentAreaNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPaymentAreaUseCaseImplTest {

	@Mock
	private PaymentAreaRepository paymentAreaRepository;

	private GetPaymentAreaUseCaseImpl useCase;

	private PaymentArea area;
	private UUID areaId;

	@BeforeEach
	void setUp() {
		useCase = new GetPaymentAreaUseCaseImpl(paymentAreaRepository);
		area = new PaymentArea("Colegiaturas", "COL", "Descripcion");
		areaId = UUID.randomUUID();
		ReflectionTestUtils.setField(area, "id", areaId);
	}

	@Test
	void getById_returnsTheAreaWhenItExists() {
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));

		PaymentAreaResult result = useCase.getById(areaId);

		assertThat(result.id()).isEqualTo(areaId);
		assertThat(result.name()).isEqualTo("Colegiaturas");
		assertThat(result.code()).isEqualTo("COL");
	}

	@Test
	void getById_rejectsUnknownPaymentAreaId() {
		UUID unknownId = UUID.randomUUID();
		when(paymentAreaRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(PaymentAreaNotFoundException.class);
	}
}
