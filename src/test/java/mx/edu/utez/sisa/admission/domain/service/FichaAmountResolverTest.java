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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The ficha price rule (Fase 11): exactly one ACTIVE {@code ADMISSION} concept
 * of the program, and then the rate of THAT concept that prices this program on
 * this date. The amount is never a field of the concept — the catalog's answer
 * lives in the rates, so a concept with no matching rate is a configuration
 * gap, not a price of zero.
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

	private static final UUID CONCEPT_ID = UUID.randomUUID();

	private static final LocalDate ON_DATE = LocalDate.of(2026, 9, 25);

	@Mock
	private PaymentConceptQueryPort paymentConceptQueryPort;

	private FichaAmountResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new FichaAmountResolver(paymentConceptQueryPort);
	}

	private static PaymentConceptQueryPort.FichaConcept concept(LocalDate from, LocalDate until) {
		return new PaymentConceptQueryPort.FichaConcept(CONCEPT_ID, "Inscripción", from, until);
	}

	/** A concept that exists, is sellable, and has a rate that prices it. */
	private void givenPricedConcept() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(concept(null, null)));
		when(paymentConceptQueryPort.findActiveRateAmountFor(CONCEPT_ID, PROGRAM_ID, ON_DATE))
				.thenReturn(Optional.of(new BigDecimal("1578.00")));
	}

	@Test
	void pricesTheFichaFromTheConceptsRate() {
		givenPricedConcept();

		FichaAmountResolver.FichaAmount amount = resolver.resolve(PROGRAM_ID, ON_DATE);

		assertThat(amount.amount()).isEqualByComparingTo("1578.00");
		assertThat(amount.conceptName()).isEqualTo("Inscripción");
	}

	@Test
	void theRateIsLookedUpForTheResolvedConceptAndThisProgramAndDate() {
		// The three arguments are what make the amount mean anything. Asking for
		// the concept only would be a catalog-wide minimum, not a price; asking
		// for the program only would ignore which concept is being charged.
		givenPricedConcept();

		resolver.resolve(PROGRAM_ID, ON_DATE);

		verify(paymentConceptQueryPort).findActiveRateAmountFor(CONCEPT_ID, PROGRAM_ID, ON_DATE);
	}

	@Test
	void aConceptWithNoRateForThisProgramIs409AndNeverFallsBack() {
		// The whole point of the change: the concept is there and sellable, and it
		// still cannot be priced. Returning the concept's own cost here would be
		// exactly the silent fallback that was removed.
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(concept(null, null)));
		when(paymentConceptQueryPort.findActiveRateAmountFor(CONCEPT_ID, PROGRAM_ID, ON_DATE))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.isInstanceOf(FichaPaymentConceptNotFoundException.class)
				.hasMessageContaining("no tiene una tarifa configurada para esta carrera");
	}

	@Test
	void theMissingRateMessageNamesTheConceptSoStaffCanFindIt() {
		// Two failures, same code, different audience: the applicant can only act
		// on "contacta a la universidad", but whoever fixes the catalog needs to
		// know WHICH concept lost its price.
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(concept(null, null)));
		when(paymentConceptQueryPort.findActiveRateAmountFor(CONCEPT_ID, PROGRAM_ID, ON_DATE))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.hasMessageContaining("Inscripción");
	}

	@Test
	void theMissingRateMessageNeverLeaksInternalIds() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(concept(null, null)));
		when(paymentConceptQueryPort.findActiveRateAmountFor(CONCEPT_ID, PROGRAM_ID, ON_DATE))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> resolver.resolve(PROGRAM_ID, ON_DATE))
				.hasMessageNotContaining(PROGRAM_ID.toString())
				.hasMessageNotContaining(CONCEPT_ID.toString());
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
				new PaymentConceptQueryPort.FichaConcept(CONCEPT_ID, "Inscripción", null, null),
				new PaymentConceptQueryPort.FichaConcept(UUID.randomUUID(), "Reinscripción", null, null)));

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
	void requirePayableOnDoesNotNeedARate() {
		// The amount is frozen on the ticket at registration. A rate deleted or
		// edited between registration and payment must not be able to decide
		// whether an already-issued ticket can be paid. Nothing is stubbed for the
		// rate lookup: if the method called it, Mockito would hand back null and
		// the verify() below would fail on its own.
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID, ON_DATE))
				.thenReturn(List.of(concept(null, null)));

		assertThatCode(() -> resolver.requirePayableOn(PROGRAM_ID, ON_DATE)).doesNotThrowAnyException();

		verify(paymentConceptQueryPort, never()).findActiveRateAmountFor(any(), any(), any());
	}

	@Test
	void paymentClosesOnReportsTheCatalogsOwnClosingDate() {
		LocalDate until = LocalDate.of(2026, 9, 30);
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID))
				.thenReturn(List.of(concept(null, until)));

		assertThat(resolver.paymentClosesOn(PROGRAM_ID)).isEqualTo(until);
	}

	@Test
	void paymentClosesOnIsNullWhenNoEndDateIsConfigured() {
		when(paymentConceptQueryPort.findActiveEnrollmentForProgram(PROGRAM_ID))
				.thenReturn(List.of(concept(null, null)));

		assertThat(resolver.paymentClosesOn(PROGRAM_ID)).isNull();
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
