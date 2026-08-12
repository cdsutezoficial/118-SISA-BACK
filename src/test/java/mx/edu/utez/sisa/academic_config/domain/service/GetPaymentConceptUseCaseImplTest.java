package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPaymentConceptUseCaseImplTest {

	@Mock
	private PaymentConceptRepository paymentConceptRepository;

	private GetPaymentConceptUseCaseImpl useCase;

	private PaymentConcept concept;
	private UUID conceptId;

	@BeforeEach
	void setUp() {
		useCase = new GetPaymentConceptUseCaseImpl(paymentConceptRepository);
		concept = new PaymentConcept("Inscripcion", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true,
				false, 1, 2, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
		conceptId = UUID.randomUUID();
		ReflectionTestUtils.setField(concept, "id", conceptId);
	}

	@Test
	void getById_returnsTheConceptWhenItExists() {
		when(paymentConceptRepository.findById(conceptId)).thenReturn(Optional.of(concept));

		PaymentConceptResult result = useCase.getById(conceptId);

		assertThat(result.id()).isEqualTo(conceptId);
		assertThat(result.name()).isEqualTo("Inscripcion");
		assertThat(result.type()).isEqualTo(PaymentConceptType.ENROLLMENT);
	}

	@Test
	void getById_rejectsUnknownPaymentConceptId() {
		UUID unknownId = UUID.randomUUID();
		when(paymentConceptRepository.findById(unknownId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getById(unknownId)).isInstanceOf(PaymentConceptNotFoundException.class);
	}
}
