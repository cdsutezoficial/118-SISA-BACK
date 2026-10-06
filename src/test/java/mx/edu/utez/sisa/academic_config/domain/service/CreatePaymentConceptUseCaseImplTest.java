package mx.edu.utez.sisa.academic_config.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptStatus;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.CreatePaymentConceptCommand;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentConceptResult;
import mx.edu.utez.sisa.academic_config.domain.port.in.CreatePaymentConceptUseCase.PaymentRateDraft;
import mx.edu.utez.sisa.academic_config.domain.port.in.ReconcilePaymentRatesUseCase;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentAreaRepository;
import mx.edu.utez.sisa.academic_config.domain.port.out.PaymentConceptRepository;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentConceptCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicatePaymentQuotaLevelException;
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
	private ReconcilePaymentRatesUseCase reconcilePaymentRatesUseCase;

	private CreatePaymentConceptUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CreatePaymentConceptUseCaseImpl(paymentConceptRepository, paymentAreaRepository,
				reconcilePaymentRatesUseCase);
	}

	@Test
	void createPaymentConcept_successfulCreation() {
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase.createPaymentConcept(validCommand("Inscripcion"));

		assertThat(result.status()).isEqualTo(PaymentConceptStatus.ACTIVE);
		assertThat(result.name()).isEqualTo("Inscripcion");
		assertThat(result.code()).isEqualTo("INS-1");
		assertThat(result.type()).isEqualTo(PaymentConceptType.ENROLLMENT);
	}

	/**
	 * The whole point of adding {@code code}: the name stays free-text and
	 * duplicable, and anything that has to reference a concept from outside the
	 * catalog — a scholarship's scope, a support-rule config — uses the code
	 * instead. Two concepts may share a name precisely because they will not
	 * share a code.
	 */
	@Test
	void createPaymentConcept_allowsDuplicateName() {
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult first = useCase.createPaymentConcept(validCommand("Inscripcion"));
		PaymentConceptResult second = useCase.createPaymentConcept(validCommand("Inscripcion"));

		assertThat(first.name()).isEqualTo("Inscripcion");
		assertThat(second.name()).isEqualTo("Inscripcion");
	}

	@Test
	void createPaymentConcept_rejectsDuplicateCode() {
		when(paymentConceptRepository.findByCode("INS-1", null)).thenReturn(Optional.of(mock(PaymentConcept.class)));

		assertThatThrownBy(() -> useCase.createPaymentConcept(validCommand("Inscripcion")))
				.isInstanceOf(DuplicatePaymentConceptCodeException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	/**
	 * Case-insensitive because a code is typed by hand into other systems, and a
	 * uniqueness rule that "INS-1" and "ins-1" both satisfy would let two configs
	 * point at what the operator believes is one concept.
	 */
	@Test
	void createPaymentConcept_rejectsDuplicateCodeIgnoringCase() {
		// The case-insensitivity lives in the adapter (findByCodeIgnoreCase), so
		// what this layer sees is the caller's own casing, trimmed. The stub is
		// written against "ins-1" because that is the argument the use case
		// forwards; asserting on "INS-1" would stub a call that never happens.
		when(paymentConceptRepository.findByCode("ins-1", null)).thenReturn(Optional.of(mock(PaymentConcept.class)));

		assertThatThrownBy(() -> useCase.createPaymentConcept(quotaCommand("Cuota", "ins-1")))
				.isInstanceOf(DuplicatePaymentConceptCodeException.class);
	}

	@Test
	void createPaymentConcept_rejectsBlankCode() {
		assertThatThrownBy(() -> useCase.createPaymentConcept(quotaCommand("Cuota", "   ")))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	/**
	 * One active recurring quota per level. A second one would leave the pricing
	 * lookup with two defensible prices for the same student, which is worse than
	 * refusing to create it.
	 */
	@Test
	void createPaymentConcept_rejectsASecondActiveQuotaForTheSameLevel() {
		when(paymentConceptRepository.findByCode("CUA-1", null)).thenReturn(Optional.empty());
		when(paymentConceptRepository.findActiveByTypeAndLevelNumber(PaymentConceptType.PERIODIC_QUOTA, 1, null))
				.thenReturn(Optional.of(mock(PaymentConcept.class)));

		assertThatThrownBy(() -> useCase.createPaymentConcept(quotaCommand("Cuota", "CUA-1")))
				.isInstanceOf(DuplicatePaymentQuotaLevelException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void createPaymentConcept_allowsAQuotaForADifferentLevel() {
		// The level check has to be scoped to the level, not to the type: a second
		// quota for level 2 is not a duplicate of the one for level 1.
		when(paymentConceptRepository.findByCode("CUA-2", null)).thenReturn(Optional.empty());
		when(paymentConceptRepository.findActiveByTypeAndLevelNumber(PaymentConceptType.PERIODIC_QUOTA, 2, null))
				.thenReturn(Optional.empty());
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase.createPaymentConcept(quotaCommand("Cuota segundo", "CUA-2", 2));

		assertThat(result.levelNumber()).isEqualTo(2);
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
		UUID linkedConceptId = UUID.randomUUID();
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.of(mock(PaymentArea.class)));
		when(paymentConceptRepository.findById(linkedConceptId)).thenReturn(Optional.of(mock(PaymentConcept.class)));
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		PaymentConceptResult result = useCase.createPaymentConcept(new CreatePaymentConceptCommand("Inscripcion",
				"INS-EXT", null, null, PaymentConceptType.ENROLLMENT, null, false, null, null, false, null, null,
				areaId, new BigDecimal("1234.50"), true, new BigDecimal("2000.00"), true, true, 12,
				List.of(linkedConceptId), List.of()));

		assertThat(result.areaId()).isEqualTo(areaId);
		assertThat(result.cost()).isEqualByComparingTo("1234.50");
		assertThat(result.isExternal()).isTrue();
		assertThat(result.costExternal()).isEqualByComparingTo("2000.00");
		assertThat(result.isAccumulable()).isTrue();
		assertThat(result.isMulticoncept()).isTrue();
		assertThat(result.quotaLimit()).isEqualTo(12);
		assertThat(result.linkedConceptIds()).containsExactly(linkedConceptId);
	}

	/**
	 * Rates are reconciled through the dedicated use case rather than written
	 * inline, so the create path and the rate endpoint cannot drift into two
	 * different rules about what a complete set is.
	 */
	@Test
	void createPaymentConcept_reconcilesTheRatesItWasGiven() {
		when(paymentConceptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		List<PaymentRateDraft> rates = List.of();

		useCase.createPaymentConcept(validCommand("Inscripcion", rates));

		verify(reconcilePaymentRatesUseCase).reconcileRates(any());
	}

	@Test
	void createPaymentConcept_rejectsNegativeCost() {
		CreatePaymentConceptCommand command = extendedCommand(null, new BigDecimal("-0.01"), false, null, null,
				List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void createPaymentConcept_rejectsExternalWithoutCostExternal() {
		CreatePaymentConceptCommand command = extendedCommand(null, new BigDecimal("100.00"), true, null, null,
				List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void createPaymentConcept_rejectsNegativeCostExternal() {
		CreatePaymentConceptCommand command = extendedCommand(null, new BigDecimal("100.00"), true,
				new BigDecimal("-1.00"), null, List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void createPaymentConcept_rejectsNonPositiveQuotaLimit() {
		CreatePaymentConceptCommand command = extendedCommand(null, new BigDecimal("100.00"), false, null, 0,
				List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(InvalidPaymentConceptDataException.class);
	}

	@Test
	void createPaymentConcept_rejectsUnknownAreaReference() {
		UUID areaId = UUID.randomUUID();
		when(paymentAreaRepository.findById(areaId)).thenReturn(Optional.empty());
		CreatePaymentConceptCommand command = extendedCommand(areaId, null, false, null, null, List.of());

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(PaymentConceptReferenceNotFoundException.class);

		verify(paymentConceptRepository, never()).save(any());
	}

	@Test
	void createPaymentConcept_rejectsUnknownLinkedConceptReference() {
		UUID linkedConceptId = UUID.randomUUID();
		when(paymentConceptRepository.findById(linkedConceptId)).thenReturn(Optional.empty());
		CreatePaymentConceptCommand command = extendedCommand(null, null, false, null, null,
				List.of(linkedConceptId));

		assertThatThrownBy(() -> useCase.createPaymentConcept(command))
				.isInstanceOf(PaymentConceptReferenceNotFoundException.class);
	}

	private static CreatePaymentConceptCommand extendedCommand(UUID areaId, BigDecimal cost, boolean isExternal,
			BigDecimal costExternal, Integer quotaLimit, List<UUID> linkedConceptIds) {
		return new CreatePaymentConceptCommand("Inscripcion", "INS-1", "Descripcion", "Politicas",
				PaymentConceptType.ENROLLMENT, null, false, 1, 2, true, null, null, areaId, cost, isExternal,
				costExternal, false, false, quotaLimit, linkedConceptIds, List.of());
	}

	private static CreatePaymentConceptCommand validCommand(String name) {
		return validCommand(name, List.of());
	}

	private static CreatePaymentConceptCommand validCommand(String name,
			List<PaymentRateDraft> rates) {
		return new CreatePaymentConceptCommand(name, "INS-1", "Descripcion", "Politicas",
				PaymentConceptType.ENROLLMENT, null, false, 1, 2, true, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 12, 31), null, null, false, null, false, false, null, List.of(), rates);
	}

	private static CreatePaymentConceptCommand quotaCommand(String name, String code) {
		return quotaCommand(name, code, 1);
	}

	private static CreatePaymentConceptCommand quotaCommand(String name, String code, Integer levelNumber) {
		return new CreatePaymentConceptCommand(name, code, null, null, PaymentConceptType.PERIODIC_QUOTA, levelNumber,
				true, null, null, false, null, null, null, null, false, null, false, false, null, List.of(), List.of());
	}

	private static CreatePaymentConceptCommand commandWith(Integer maxPerStudent, Integer maxPerPeriod,
			LocalDate availableFrom, LocalDate availableUntil) {
		return new CreatePaymentConceptCommand("Inscripcion", "INS-1", "Descripcion", "Politicas",
				PaymentConceptType.ENROLLMENT, null, false, maxPerStudent, maxPerPeriod, true, availableFrom,
				availableUntil, null, null, false, null, false, false, null, List.of(), List.of());
	}
}