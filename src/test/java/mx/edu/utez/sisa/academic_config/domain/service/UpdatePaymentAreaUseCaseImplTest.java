package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.UpdatePaymentAreaUseCase.UpdatePaymentAreaCommand;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaNameException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentAreaDataException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdatePaymentAreaUseCaseImplTest {

	@Mock
	private PaymentAreaRepository paymentAreaRepository;

	private UpdatePaymentAreaUseCaseImpl useCase;

	private PaymentArea area;
	private UUID areaId;

	@BeforeEach
	void setUp() {
		useCase = new UpdatePaymentAreaUseCaseImpl(paymentAreaRepository);
		area = new PaymentArea("Colegiaturas", "COL", "Descripcion");
		areaId = UUID.randomUUID();
		ReflectionTestUtils.setField(area, "id", areaId);
	}

	@Test
	void updatePaymentArea_successfulUpdateLeavesStatusUnchanged() {
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
		when(paymentAreaRepository.findByName("Inscripcion")).thenReturn(Optional.empty());
		when(paymentAreaRepository.findByCode("INS")).thenReturn(Optional.empty());
		when(paymentAreaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentAreaResult result = useCase
				.updatePaymentArea(new UpdatePaymentAreaCommand(areaId, "Inscripcion", "INS", "Otra"));

		assertThat(result.name()).isEqualTo("Inscripcion");
		assertThat(result.code()).isEqualTo("INS");
		assertThat(result.status()).isEqualTo(PaymentAreaStatus.ACTIVE);
	}

	@Test
	void updatePaymentArea_rejectsUnknownPaymentAreaId() {
		UUID unknownId = UUID.randomUUID();
		when(paymentAreaRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase
				.updatePaymentArea(new UpdatePaymentAreaCommand(unknownId, "Inscripcion", "INS", "Descripcion")))
				.isInstanceOf(PaymentAreaNotFoundException.class);
	}

	@Test
	void updatePaymentArea_rejectsNameUsedByAnotherArea() {
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
		PaymentArea other = new PaymentArea("Inscripcion", "INS", "Descripcion");
		ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
		when(paymentAreaRepository.findByName("Inscripcion")).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> useCase
				.updatePaymentArea(new UpdatePaymentAreaCommand(areaId, "Inscripcion", "COL", "Descripcion")))
				.isInstanceOf(DuplicatePaymentAreaNameException.class);

		verify(paymentAreaRepository, never()).save(any());
	}

	@Test
	void updatePaymentArea_rejectsCodeUsedByAnotherArea() {
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
		when(paymentAreaRepository.findByName("Colegiaturas")).thenReturn(Optional.of(area));
		PaymentArea other = new PaymentArea("Inscripcion", "INS", "Descripcion");
		ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
		when(paymentAreaRepository.findByCode("INS")).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> useCase
				.updatePaymentArea(new UpdatePaymentAreaCommand(areaId, "Colegiaturas", "INS", "Descripcion")))
				.isInstanceOf(DuplicatePaymentAreaCodeException.class);

		verify(paymentAreaRepository, never()).save(any());
	}

	@Test
	void updatePaymentArea_allowsKeepingOwnNameAndCode() {
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
		when(paymentAreaRepository.findByName("Colegiaturas")).thenReturn(Optional.of(area));
		when(paymentAreaRepository.findByCode("COL")).thenReturn(Optional.of(area));
		when(paymentAreaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentAreaResult result = useCase
				.updatePaymentArea(new UpdatePaymentAreaCommand(areaId, "Colegiaturas", "COL", "Descripcion 2"));

		assertThat(result.name()).isEqualTo("Colegiaturas");
		assertThat(result.code()).isEqualTo("COL");
	}

	@Test
	void updatePaymentArea_rejectsBlankName() {
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));

		assertThatThrownBy(() -> useCase
				.updatePaymentArea(new UpdatePaymentAreaCommand(areaId, " ", "COL", "Descripcion")))
				.isInstanceOf(InvalidPaymentAreaDataException.class);

		verify(paymentAreaRepository, never()).save(any());
	}

	@Test
	void updatePaymentArea_rejectsBlankCode() {
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(area));

		assertThatThrownBy(() -> useCase
				.updatePaymentArea(new UpdatePaymentAreaCommand(areaId, "Colegiaturas", "", "Descripcion")))
				.isInstanceOf(InvalidPaymentAreaDataException.class);
	}
}
