package mx.edu.utez.sisa.academic_config.domain.model;

import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPaymentConceptDataException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentConceptTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		PaymentConcept concept = newConcept();

		assertThat(concept.getStatus()).isEqualTo(PaymentConceptStatus.ACTIVE);
	}

	@Test
	void constructor_setsAllFields() {
		PaymentConcept concept = newConcept();

		assertThat(concept.getName()).isEqualTo("Inscripcion");
		assertThat(concept.getCode()).isEqualTo("INS-2026");
		assertThat(concept.getDescription()).isEqualTo("Descripcion");
		assertThat(concept.getPolicies()).isEqualTo("Politicas");
		assertThat(concept.getType()).isEqualTo(PaymentConceptType.ENROLLMENT);
		assertThat(concept.isStandalone()).isFalse();
		assertThat(concept.getMaxPerStudent()).isEqualTo(1);
		assertThat(concept.getMaxPerPeriod()).isEqualTo(2);
		assertThat(concept.isRequiresValidation()).isTrue();
		assertThat(concept.getAvailableFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(concept.getAvailableUntil()).isEqualTo(LocalDate.of(2026, 12, 31));
	}

	@Test
	void constructor_allowsNullOptionalFields() {
		PaymentConcept concept = new PaymentConcept("Extraordinario", "EXT-2026", null, null,
				PaymentConceptType.EXTRAORDINARY, null, true, null, null, false, null, null);

		assertThat(concept.getDescription()).isNull();
		assertThat(concept.getPolicies()).isNull();
		assertThat(concept.getMaxPerStudent()).isNull();
		assertThat(concept.getMaxPerPeriod()).isNull();
		assertThat(concept.getAvailableFrom()).isNull();
		assertThat(concept.getAvailableUntil()).isNull();
	}

	/**
	 * A recurring quota is identified by its level, so the level is part of what
	 * the constructor stores rather than something a caller has to remember to
	 * re-read from the request.
	 */
	@Test
	void constructor_storesLevelNumberForAPeriodicQuota() {
		PaymentConcept concept = new PaymentConcept("Cuota segundo", "CUA-2", null, null,
				PaymentConceptType.PERIODIC_QUOTA, 2, true, null, null, false, null, null);

		assertThat(concept.getType()).isEqualTo(PaymentConceptType.PERIODIC_QUOTA);
		assertThat(concept.getLevelNumber()).isEqualTo(2);
	}

	@Test
	void constructor_rejectsAPeriodicQuotaWithoutALevel() {
		assertThatThrownBy(() -> new PaymentConcept("Cuota", "CUA-1", null, null, PaymentConceptType.PERIODIC_QUOTA, null,
				true, null, null, false, null, null))
				.isInstanceOf(InvalidPaymentConceptDataException.class)
				.hasMessageContaining("levelNumber is required");
	}

	@Test
	void constructor_rejectsALevelBelowOne() {
		assertThatThrownBy(() -> new PaymentConcept("Cuota", "CUA-0", null, null, PaymentConceptType.PERIODIC_QUOTA, 0,
				true, null, null, false, null, null))
				.isInstanceOf(InvalidPaymentConceptDataException.class)
				.hasMessageContaining("greater than zero");
	}

	/**
	 * The other direction, and the reason the check is a pairing rather than a
	 * one-way requirement: an ADMISSION concept carrying a level would read as
	 * "the admission ticket for level 2", which is a statement nobody makes.
	 */
	@Test
	void constructor_rejectsALevelOnANonRecurringQuota() {
		assertThatThrownBy(() -> new PaymentConcept("Admision", "ADM-1", null, null, PaymentConceptType.ADMISSION, 1,
				true, null, null, false, null, null))
				.isInstanceOf(InvalidPaymentConceptDataException.class)
				.hasMessageContaining("only applies to a PERIODIC_QUOTA");
	}

	@Test
	void updateDetails_leavesStatusUnchanged() {
		PaymentConcept concept = newConcept();

		concept.updateDetails("Reinscripcion", "REI-2027", "Otra descripcion", "Otras politicas",
				PaymentConceptType.REINSCRIPTION, null, true, 3, 4, false, LocalDate.of(2027, 1, 1),
				LocalDate.of(2027, 6, 30));

		assertThat(concept.getName()).isEqualTo("Reinscripcion");
		assertThat(concept.getCode()).isEqualTo("REI-2027");
		assertThat(concept.getType()).isEqualTo(PaymentConceptType.REINSCRIPTION);
		assertThat(concept.isStandalone()).isTrue();
		assertThat(concept.getMaxPerStudent()).isEqualTo(3);
		assertThat(concept.getMaxPerPeriod()).isEqualTo(4);
		assertThat(concept.isRequiresValidation()).isFalse();
		assertThat(concept.getAvailableFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(concept.getAvailableUntil()).isEqualTo(LocalDate.of(2027, 6, 30));
		assertThat(concept.getStatus()).isEqualTo(PaymentConceptStatus.ACTIVE);
	}

	/**
	 * Editing the type or the level is also a way to smuggle an invalid pairing
	 * in, so {@code updateDetails} runs the same check the constructor does
	 * rather than trusting that the row was valid once.
	 */
	@Test
	void updateDetails_rejectsSwappingAnAdmissionConceptIntoARecurringQuotaWithoutALevel() {
		PaymentConcept concept = newConcept();

		assertThatThrownBy(() -> concept.updateDetails("Cuota", "CUA-1", null, null, PaymentConceptType.PERIODIC_QUOTA,
				null, true, null, null, false, null, null))
				.isInstanceOf(InvalidPaymentConceptDataException.class)
				.hasMessageContaining("levelNumber is required");
	}

	@Test
	void deactivate_transitionsAnActiveConceptToInactive() {
		PaymentConcept concept = newConcept();

		concept.deactivate();

		assertThat(concept.getStatus()).isEqualTo(PaymentConceptStatus.INACTIVE);
	}

	@Test
	void deactivate_isIdempotentOnAnAlreadyInactiveConcept() {
		PaymentConcept concept = newConcept();
		concept.deactivate();

		concept.deactivate();

		assertThat(concept.getStatus()).isEqualTo(PaymentConceptStatus.INACTIVE);
	}

	@Test
	void activate_transitionsAnInactiveConceptToActive() {
		PaymentConcept concept = newConcept();
		concept.deactivate();

		concept.activate();

		assertThat(concept.getStatus()).isEqualTo(PaymentConceptStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentOnAnAlreadyActiveConcept() {
		PaymentConcept concept = newConcept();

		concept.activate();

		assertThat(concept.getStatus()).isEqualTo(PaymentConceptStatus.ACTIVE);
	}

	private PaymentConcept newConcept() {
		return new PaymentConcept("Inscripcion", "INS-2026", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT,
				null, false, 1, 2, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}
}