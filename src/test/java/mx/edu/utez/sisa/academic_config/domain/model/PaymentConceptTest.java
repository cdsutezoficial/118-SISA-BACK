package mx.edu.utez.sisa.academic_config.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

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
		assertThat(concept.getDescription()).isEqualTo("Descripcion");
		assertThat(concept.getPolicies()).isEqualTo("Politicas");
		assertThat(concept.getType()).isEqualTo(PaymentConceptType.ENROLLMENT);
		assertThat(concept.isTuition()).isTrue();
		assertThat(concept.isStandalone()).isFalse();
		assertThat(concept.getMaxPerStudent()).isEqualTo(1);
		assertThat(concept.getMaxPerPeriod()).isEqualTo(2);
		assertThat(concept.isRequiresValidation()).isTrue();
		assertThat(concept.getAvailableFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(concept.getAvailableUntil()).isEqualTo(LocalDate.of(2026, 12, 31));
	}

	@Test
	void constructor_allowsNullOptionalFields() {
		PaymentConcept concept = new PaymentConcept("Extraordinario", null, null, PaymentConceptType.EXTRAORDINARY,
				false, true, null, null, false, null, null);

		assertThat(concept.getDescription()).isNull();
		assertThat(concept.getPolicies()).isNull();
		assertThat(concept.getMaxPerStudent()).isNull();
		assertThat(concept.getMaxPerPeriod()).isNull();
		assertThat(concept.getAvailableFrom()).isNull();
		assertThat(concept.getAvailableUntil()).isNull();
	}

	@Test
	void updateDetails_leavesStatusUnchanged() {
		PaymentConcept concept = newConcept();

		concept.updateDetails("Reinscripcion", "Otra descripcion", "Otras politicas", PaymentConceptType.REINSCRIPTION,
				false, true, 3, 4, false, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));

		assertThat(concept.getName()).isEqualTo("Reinscripcion");
		assertThat(concept.getType()).isEqualTo(PaymentConceptType.REINSCRIPTION);
		assertThat(concept.isTuition()).isFalse();
		assertThat(concept.isStandalone()).isTrue();
		assertThat(concept.getMaxPerStudent()).isEqualTo(3);
		assertThat(concept.getMaxPerPeriod()).isEqualTo(4);
		assertThat(concept.isRequiresValidation()).isFalse();
		assertThat(concept.getAvailableFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(concept.getAvailableUntil()).isEqualTo(LocalDate.of(2027, 6, 30));
		assertThat(concept.getStatus()).isEqualTo(PaymentConceptStatus.ACTIVE);
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
		return new PaymentConcept("Inscripcion", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT, true,
				false, 1, 2, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
	}
}
