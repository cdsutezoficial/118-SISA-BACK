package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.CreatePaymentConceptCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentConceptDataException;
import mx.edu.utez.sisa.academic_config.shared.exception.PaymentConceptReferenceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePaymentConceptUseCaseImplTest {

	@Mock
	private PaymentConceptRepository paymentConceptRepository;

	@Mock
	private PaymentAreaRepository paymentAreaRepository;

	@Mock
	private AcademicProgramRepository academicProgramRepository;

	private CreatePaymentConceptUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CreatePaymentConceptUseCaseImpl(paymentConceptRepository, paymentAreaRepository,
				academicProgramRepository);
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

	@Test
	void createPaymentConcept_persistsAndReturnsExtensionFields() {
		UUID areaId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();
		UUID linkedConceptId = UUID.randomUUID();
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(mock(PaymentArea.class)));
		when(academicProgramRepository.findById(programId)).thenReturn(Optional.of(mock(AcademicProgram.class)));
		when(paymentConceptRepository.findById(linkedConceptId)).thenReturn(Optional.of(mock(PaymentConcept.class)));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase.createPaymentConcept(new CreatePaymentConceptCommand("Inscripcion", null,
				null, PaymentConceptType.ENROLLMENT, false, false, null, null, false, null, null, areaId,
				new BigDecimal("1234.50"), true, new BigDecimal("2000.00"), true, true, 12, List.of(linkedConceptId),
				List.of(programId)));

		assertThat(result.areaId()).isEqualTo(areaId);
		assertThat(result.cost()).isEqualByComparingTo("1234.50");
		assertThat(result.isExternal()).isTrue();
		assertThat(result.costExternal()).isEqualByComparingTo("2000.00");
		assertThat(result.isAccumulable()).isTrue();
		assertThat(result.isMulticoncept()).isTrue();
		assertThat(result.quotaLimit()).isEqualTo(12);
		assertThat(result.linkedConceptIds()).containsExactly(linkedConceptId);
		assertThat(result.programIds()).containsExactly(programId);
	}

	@Test
	void createPaymentConcept_rejectsNegativeCost() {
		CreatePaymentConceptCommand command = extendedCommand(null, new BigDecimal("-0.01"), false, null, null,
				List.of(), List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void createPaymentConcept_rejectsExternalWithoutCostExternal() {
		CreatePaymentConceptCommand command = extendedCommand(null, new BigDecimal("100.00"), true, null, null,
				List.of(), List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void createPaymentConcept_rejectsNegativeCostExternal() {
		CreatePaymentConceptCommand command = extendedCommand(null, new BigDecimal("100.00"), true,
				new BigDecimal("-1.00"), null, List.of(), List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void createPaymentConcept_rejectsNonPositiveQuotaLimit() {
		CreatePaymentConceptCommand command = extendedCommand(null, new BigDecimal("100.00"), false, null, 0,
				List.of(), List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void createPaymentConcept_rejectsUnknownAreaReference() {
		UUID areaId = UUID.randomUUID();
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.empty());
		CreatePaymentConceptCommand command = extendedCommand(areaId, null, false, null, null, List.of(), List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(PaymentConceptReferenceNotFoundException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void createPaymentConcept_rejectsUnknownProgramReference() {
		UUID programId = UUID.randomUUID();
		when(academicProgramRepository.findById(programId)).thenReturn(Optional.empty());
		CreatePaymentConceptCommand command = extendedCommand(null, null, false, null, null, List.of(),
				List.of(programId));

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(PaymentConceptReferenceNotFoundException.class);
	}

	@Test
	void createPaymentConcept_rejectsUnknownLinkedConceptReference() {
		UUID linkedConceptId = UUID.randomUUID();
		when(paymentConceptRepository.findById(linkedConceptId)).thenReturn(Optional.empty());
		CreatePaymentConceptCommand command = extendedCommand(null, null, false, null, null,
				List.of(linkedConceptId), List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(PaymentConceptReferenceNotFoundException.class);
	}

	@Test
	void createPaymentConcept_rejectsDuplicateProgramIds() {
		UUID programId = UUID.randomUUID();
		CreatePaymentConceptCommand command = extendedCommand(null, null, false, null, null, List.of(),
				List.of(programId, programId));

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	private static CreatePaymentConceptCommand extendedCommand(UUID areaId, BigDecimal cost, boolean isExternal,
			BigDecimal costExternal, Integer quotaLimit, List<UUID> linkedConceptIds, List<UUID> programIds) {
		return new CreatePaymentConceptCommand("Inscripcion", "Descripcion", "Politicas",
				PaymentConceptType.ENROLLMENT, true, false, 1, 2, true, null, null, areaId, cost, isExternal,
				costExternal, false, false, quotaLimit, linkedConceptIds, programIds);
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
