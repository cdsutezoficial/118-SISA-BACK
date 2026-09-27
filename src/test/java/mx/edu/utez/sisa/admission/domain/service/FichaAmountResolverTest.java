package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.port.out.PaymentConceptQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.AmbiguousFichaPaymentConceptException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentConceptExpiredException;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The ficha price rule (Fase 11): exactly one ACTIVE {@code ENROLLMENT} concept
 * of the program, priced by {@code costExternal} when the concept is external
 * and {@code cost} otherwise. Zero and many are both hard failures — silently
 * picking a price would charge applicants an arbitrary amount.
 *
 * <p>The second half of this class is about telling the two empty results apart.
 * A concept outside its availability window and a concept that does not exist
 * both make the date-filtered query return nothing, but they are different
 * problems with different audiences, and collapsing them sends every applicant
 * of a closed period to a message about a broken catalog.
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

	private static PaymentConceptQueryPort.FichaConcept concept(LocalDate from, LocalDate until) {
		return new PaymentConceptQueryPort.FichaConcept("Inscripción", new BigDecimal("1578.00"), null, false, from,
				until);
	}

	@Test
	void resolvesTheInternalConceptCost() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(concept(null, null)));

		FichaAmountResolver.FichaAmount amount = resolver.resolve(PROGRAM_ID, ON_DATE);

		assertThat(amount.amount()).isEqualByComparingTo("1578.00");
		assertThat(amount.conceptName()).isEqualTo("Inscripción");
	}

	@Test
	void externalConceptIsPricedWithItsExternalCost() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(new PaymentConceptQueryPort.FichaConcept("Inscripción",
						new BigDecimal("1578.00"), new BigDecimal("1450.00"), true, null, null)));

		FichaAmountResolver.FichaAmount amount = resolver.resolve(PROGRAM_ID, ON_DATE);

		assertThat(amount.amount()).isEqualByComparingTo("1450.00");
	}

	@Test
	void noActiveConceptIs404() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE)).thenReturn(List.of());
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID)).thenReturn(List.of());

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.isInstanceOf(FichaPaymentConceptNotFoundException.class)
				.hasMessageContaining("no tiene un concepto de inscripción activo");
	}

	@Test
	void moreThanOneActiveConceptIs409() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE)).thenReturn(List.of(
				new PaymentConceptQueryPort.FichaConcept("Inscripción", new BigDecimal("1578.00"), null, false, null,
						null),
				new PaymentConceptQueryPort.FichaConcept("Reinscripción", new BigDecimal("1200.00"), null, false,
						null, null)));

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.isInstanceOf(AmbiguousFichaPaymentConceptException.class)
				.hasMessageContaining("más de un concepto de inscripción activo");
	}

	// ── the window: closed, open, and absent ────────────────────────────────────

	@Test
	void aClosedWindowIsReportedAsExpiredWithItsClosingDate() {
		LocalDate until = LocalDate.of(2026, 9, 20);
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE)).thenReturn(List.of());
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID)).thenReturn(List.of(concept(null,
				until)));

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.isInstanceOf(PaymentConceptExpiredException.class)
				.hasMessageContaining("cerró el 20/09/2026");
	}

	@Test
	void aWindowThatHasNotOpenedYetIsReportedAsExpiredWithItsOpeningDate() {
		LocalDate from = LocalDate.of(2026, 10, 1);
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE)).thenReturn(List.of());
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID))
				.thenReturn(List.of(concept(from, null)));

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.isInstanceOf(PaymentConceptExpiredException.class)
				.hasMessageContaining("abre el 01/10/2026");
	}

	@Test
	void bothWindowEdgesAreInclusive() {
		// available_until >= onDate and available_from <= onDate, matching the
		// BETWEEN the SQL does. "Cierra el 20/09" has to still be payable on the
		// 20th, or a period silently loses its last day.
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(concept(ON_DATE, ON_DATE)));

		assertThatCode(() -> resolver.requirePayableOn(PROGRAM_ID, ON_DATE)).doesNotThrowAnyException();
	}

	@Test
	void aConceptWithoutDatesIsAlwaysPayable() {
		// null on both sides is the catalog's way of saying "no period", and it
		// must not be read as "closed on both ends".
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(concept(null, null)));

		assertThatCode(() -> resolver.requirePayableOn(PROGRAM_ID, ON_DATE)).doesNotThrowAnyException();
	}

	@Test
	void anOpenEndedWindowOnlyClosesOnItsClosingDate() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(concept(LocalDate.of(2020, 1, 1), null)));

		assertThatCode(() -> resolver.requirePayableOn(PROGRAM_ID, ON_DATE)).doesNotThrowAnyException();
	}

	@Test
	void theFailureMessagesNeverLeakTheProgramId() {
		// These strings are rendered to applicants. A program UUID tells a reader
		// nothing they can act on and confirms which internal ids exist.
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE)).thenReturn(List.of());
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID))
				.thenReturn(List.of(concept(null, LocalDate.of(2026, 9, 20))));

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.isInstanceOf(PaymentConceptExpiredException.class)
				.hasMessageNotContaining(PROGRAM_ID.toString());
	}

	@Test
	void theNotFoundMessageNeverLeaksTheProgramIdEither() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE)).thenReturn(List.of());
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID)).thenReturn(List.of());

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.isInstanceOf(FichaPaymentConceptNotFoundException.class)
				.hasMessageNotContaining(PROGRAM_ID.toString());
	}

	@Test
	void requirePayableOnAppliesTheSameStrictRuleAsResolve() {
		// Two entry points, one rule: if the payment-side check were laxer than the
		// pricing one, a ficha could be issued against a catalog state that the
		// payment then refuses — or worse, accepts.
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE)).thenReturn(List.of());
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID))
				.thenReturn(List.of(concept(null, null), concept(null, null)));

		assertThatThrownBy(() -> resolver.requirePayableOn(PROGRAM_ID, ON_DATE))
				.isInstanceOf(AmbiguousFichaPaymentConceptException.class);
	}
}
