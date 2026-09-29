package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.CreatePaymentAreaCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentAreaUseCase.PaymentAreaResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentAreaNameException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentAreaDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePaymentAreaUseCaseImplTest {

	@Mock
	private PaymentAreaRepository paymentAreaRepository;

	private CreatePaymentAreaUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CreatePaymentAreaUseCaseImpl(paymentAreaRepository);
	}

	@Test
	void createPaymentArea_successfulCreation() {
		when(paymentAreaRepository.findByName("Colegiaturas")).thenReturn(Optional.empty());
		when(paymentAreaRepository.findByCode("COL")).thenReturn(Optional.empty());
		when(paymentAreaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentAreaResult result = useCase.createPaymentArea(validCommand("Colegiaturas", "COL"));

		assertThat(result.status()).isEqualTo(PaymentAreaStatus.ACTIVE);
		assertThat(result.name()).isEqualTo("Colegiaturas");
		assertThat(result.code()).isEqualTo("COL");
	}

	@Test
	void createPaymentArea_rejectsDuplicateName() {
		when(paymentAreaRepository.findByName("Colegiaturas"))
				.thenReturn(Optional.of(newArea("Colegiaturas", "COL")));

		assertThatThrownBy(() -> useCase.createPaymentArea(validCommand("Colegiaturas", "OTRO")))
				.isInstanceOf(DuplicatePaymentAreaNameException.class);

		verify(paymentAreaRepository, never()).save(any());
	}

	@Test
	void createPaymentArea_rejectsDuplicateCode() {
		when(paymentAreaRepository.findByName("Inscripcion")).thenReturn(Optional.empty());
		when(paymentAreaRepository.findByCode("COL")).thenReturn(Optional.of(newArea("Colegiaturas", "COL")));

		assertThatThrownBy(() -> useCase.createPaymentArea(validCommand("Inscripcion", "COL")))
				.isInstanceOf(DuplicatePaymentAreaCodeException.class);

		verify(paymentAreaRepository, never()).save(any());
	}

	@Test
	void createPaymentArea_rejectsBlankName() {
		assertThatThrownBy(() -> useCase.createPaymentArea(validCommand("  ", "COL")))
				.isInstanceOf(InvalidPaymentAreaDataException.class);

		verify(paymentAreaRepository, never()).save(any());
	}

	@Test
	void createPaymentArea_rejectsNullName() {
		assertThatThrownBy(() -> useCase.createPaymentArea(validCommand(null, "COL")))
				.isInstanceOf(InvalidPaymentAreaDataException.class);
	}

	@Test
	void createPaymentArea_rejectsBlankCode() {
		assertThatThrownBy(() -> useCase.createPaymentArea(validCommand("Colegiaturas", " ")))
				.isInstanceOf(InvalidPaymentAreaDataException.class);
	}

	@Test
	void createPaymentArea_rejectsNullCode() {
		assertThatThrownBy(() -> useCase.createPaymentArea(validCommand("Colegiaturas", null)))
				.isInstanceOf(InvalidPaymentAreaDataException.class);
	}

	private static CreatePaymentAreaCommand validCommand(String name, String code) {
		return new CreatePaymentAreaCommand(name, code, "Descripcion");
	}

	private static PaymentArea newArea(String name, String code) {
		return new PaymentArea(name, code, "Descripcion");
	}
}
