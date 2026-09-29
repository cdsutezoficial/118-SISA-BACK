package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.ChangePaymentAreaStatusUseCase.ChangeStatusCommand;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePaymentAreaStatusUseCaseImplTest {

	@Mock
	private PaymentAreaRepository paymentAreaRepository;

	private ChangePaymentAreaStatusUseCaseImpl useCase;

	private PaymentArea area;
	private UUID areaId;

	@BeforeEach
	void setUp() {
		useCase = new ChangePaymentAreaStatusUseCaseImpl(paymentAreaRepository);
		area = new PaymentArea("Colegiaturas", "COL", "Descripcion");
		areaId = UUID.randomUUID();
		ReflectionTestUtils.setField(area, "id", areaId);
	}

	@Test
	void changeStatus_deactivatesAnActiveArea() {
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
		when(paymentAreaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentAreaResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), areaId, PaymentAreaStatus.INACTIVE));

		assertThat(result.status()).isEqualTo(PaymentAreaStatus.INACTIVE);
	}

	@Test
	void changeStatus_reactivatesAnInactiveArea() {
		area.deactivate();
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
		when(paymentAreaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentAreaResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), areaId, PaymentAreaStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(PaymentAreaStatus.ACTIVE);
	}

	@Test
	void changeStatus_isIdempotentWhenTargetMatchesCurrentStatus() {
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
		when(paymentAreaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentAreaResult result = useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), areaId, PaymentAreaStatus.ACTIVE));

		assertThat(result.status()).isEqualTo(PaymentAreaStatus.ACTIVE);
	}

	@Test
	void changeStatus_rejectsUnknownPaymentAreaId() {
		UUID unknownId = UUID.randomUUID();
		when(paymentAreaRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.changeStatus(new ChangeStatusCommand(UUID.randomUUID(), unknownId, PaymentAreaStatus.ACTIVE)))
				.isInstanceOf(PaymentAreaNotFoundException.class);
	}
}
