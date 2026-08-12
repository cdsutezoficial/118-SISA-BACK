package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.CreatePaymentConceptCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentConceptDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePaymentConceptUseCaseImplTest {

	@Mock
	private PaymentConceptRepository paymentConceptRepository;

	private CreatePaymentConceptUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CreatePaymentConceptUseCaseImpl(paymentConceptRepository);
	}

	@Test
	void createPaymentConcept_successfulCreation() {
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase.createPaymentConcept(validCommand("Inscripcion"));

		assertThat(result.status()).isEqualTo(PaymentConceptStatus.ACTIVE);
		assertThat(result.name()).isEqualTo("Inscripcion");
		assertThat(result.type()).isEqualTo(PaymentConceptType.ENROLLMENT);
	}

	@Test
	void createPaymentConcept_allowsDuplicateName() {
		// This aggregate has no code field and no documented uniqueness
		// constraint on name at all (plan section 4) — unlike
		// SubjectClassification, there isn't even a secondary unique key, so two
		// concepts with the same name must both succeed with no lookup at all.
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult first = useCase.createPaymentConcept(validCommand("Inscripcion"));
		PaymentConceptResult second = useCase.createPaymentConcept(validCommand("Inscripcion"));

		assertThat(first.name()).isEqualTo("Inscripcion");
		assertThat(second.name()).isEqualTo("Inscripcion");
	}

	@Test
	void createPaymentConcept_rejectsZeroMaxPerStudent() {
		assertThatThrownBy(() -> useCase.createPaymentConcept(commandWith(0, 2, null, null)))
				.isInstanceOf(InvalidPaymentConceptDataException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void createPaymentConcept_rejectsNegativeMaxPerStudent() {
		assertThatThrownBy(() -> useCase.createPaymentConcept(commandWith(-1, 2, null, null)))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void createPaymentConcept_rejectsZeroMaxPerPeriod() {
		assertThatThrownBy(() -> useCase.createPaymentConcept(commandWith(1, 0, null, null)))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void createPaymentConcept_rejectsNegativeMaxPerPeriod() {
		assertThatThrownBy(() -> useCase.createPaymentConcept(commandWith(1, -5, null, null)))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void createPaymentConcept_allowsNullMaxPerStudentAndMaxPerPeriod() {
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase.createPaymentConcept(commandWith(null, null, null, null));

		assertThat(result.maxPerStudent()).isNull();
		assertThat(result.maxPerPeriod()).isNull();
	}

	@Test
	void createPaymentConcept_rejectsAvailableFromAfterAvailableUntil() {
		LocalDate from = LocalDate.of(2026, 6, 1);
		LocalDate until = LocalDate.of(2026, 1, 1);

		assertThatThrownBy(() -> useCase.createPaymentConcept(commandWith(1, 1, from, until)))
				.isInstanceOf(InvalidPaymentConceptDataException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void createPaymentConcept_allowsAvailableFromEqualToAvailableUntil() {
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		LocalDate sameDay = LocalDate.of(2026, 6, 1);

		PaymentConceptResult result = useCase.createPaymentConcept(commandWith(1, 1, sameDay, sameDay));

		assertThat(result.availableFrom()).isEqualTo(sameDay);
		assertThat(result.availableUntil()).isEqualTo(sameDay);
	}

	@Test
	void createPaymentConcept_allowsOnlyOneOfAvailableFromOrAvailableUntil() {
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase
				.createPaymentConcept(commandWith(1, 1, LocalDate.of(2026, 1, 1), null));

		assertThat(result.availableFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(result.availableUntil()).isNull();
	}

	private static CreatePaymentConceptCommand validCommand(String name) {
		return new CreatePaymentConceptCommand(name, "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true,
				false, 1, 2, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}

	private static CreatePaymentConceptCommand commandWith(Integer maxPerStudent, Integer maxPerPeriod,
			LocalDate availableFrom, LocalDate availableUntil) {
		return new CreatePaymentConceptCommand("Inscripcion", "Descripcion", "Politicas",
				PaymentConceptType.ENROLLMENT, true, false, maxPerStudent, maxPerPeriod, true, availableFrom,
				availableUntil);
	}
}
