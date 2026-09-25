package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.port.out.PaymentConceptQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.AmbiguousFichaPaymentConceptException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The ficha price rule (Fase 11): exactly one ACTIVE {@code ENROLLMENT} concept
 * of the program, priced by {@code costExternal} when the concept is external
 * and {@code cost} otherwise. Zero and many are both hard failures — silently
 * picking a price would charge applicants an arbitrary amount.
 */
@ExtendWith(MockitoExtension.class)
class FichaAmountResolverTest {

	private static final UUID PROGRAM_ID = UUID.randomUUID();

	private static final LocalDate ON_DATE = LocalDate.of(2026, 9, 25);

	@Mock
	private PaymentConceptQueryPort paymentConceptQueryPort;

	private FichaAmountResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new FichaAmountResolver(paymentConceptQueryPort);
	}

	@Test
	void resolvesTheInternalConceptCost() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(new PaymentConceptQueryPort.FichaConcept("Inscripción",
						new BigDecimal("1578.00"), null, false)));

		FichaAmountResolver.FichaAmount amount = resolver.resolve(PROGRAM_ID, ON_DATE);

		assertThat(amount.amount()).isEqualByComparingTo("1578.00");
		assertThat(amount.conceptName()).isEqualTo("Inscripción");
	}

	@Test
	void externalConceptIsPricedWithItsExternalCost() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(new PaymentConceptQueryPort.FichaConcept("Inscripción",
						new BigDecimal("1578.00"), new BigDecimal("1450.00"), true)));

		FichaAmountResolver.FichaAmount amount = resolver.resolve(PROGRAM_ID, ON_DATE);

		assertThat(amount.amount()).isEqualByComparingTo("1450.00");
	}

	@Test
	void noActiveConceptIs404() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE)).thenReturn(List.of());

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.isInstanceOf(FichaPaymentConceptNotFoundException.class)
				.hasMessageContaining("No existe un concepto de ENROLLMENT activo");
	}

	@Test
	void moreThanOneActiveConceptIs409() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE)).thenReturn(List.of(
				new PaymentConceptQueryPort.FichaConcept("Inscripción", new BigDecimal("1578.00"), null, false),
				new PaymentConceptQueryPort.FichaConcept("Reinscripción", new BigDecimal("1200.00"), null, false)));

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.isInstanceOf(AmbiguousFichaPaymentConceptException.class)
				.hasMessageContaining("varios conceptos de ENROLLMENT activos");
	}
}
