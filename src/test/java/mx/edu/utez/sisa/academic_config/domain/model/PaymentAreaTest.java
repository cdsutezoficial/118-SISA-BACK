package mx.edu.utez.sisa.academic_config.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAreaTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		PaymentArea area = newArea();

		assertThat(area.getStatus()).isEqualTo(PaymentAreaStatus.ACTIVE);
	}

	@Test
	void constructor_setsAllFields() {
		PaymentArea area = newArea();

		assertThat(area.getName()).isEqualTo("Colegiaturas");
		assertThat(area.getCode()).isEqualTo("COL");
		assertThat(area.getDescription()).isEqualTo("Descripcion");
	}

	@Test
	void constructor_allowsNullDescription() {
		PaymentArea area = new PaymentArea("Colegiaturas", "COL", null);

		assertThat(area.getDescription()).isNull();
	}

	@Test
	void updateDetails_leavesStatusUnchanged() {
		PaymentArea area = newArea();

		area.updateDetails("Inscripcion", "INS", "Otra descripcion");

		assertThat(area.getName()).isEqualTo("Inscripcion");
		assertThat(area.getCode()).isEqualTo("INS");
		assertThat(area.getDescription()).isEqualTo("Otra descripcion");
		assertThat(area.getStatus()).isEqualTo(PaymentAreaStatus.ACTIVE);
	}

	@Test
	void deactivate_transitionsAnActiveAreaToInactive() {
		PaymentArea area = newArea();

		area.deactivate();

		assertThat(area.getStatus()).isEqualTo(PaymentAreaStatus.INACTIVE);
	}

	@Test
	void deactivate_isIdempotentOnAnAlreadyInactiveArea() {
		PaymentArea area = newArea();
		area.deactivate();

		area.deactivate();

		assertThat(area.getStatus()).isEqualTo(PaymentAreaStatus.INACTIVE);
	}

	@Test
	void activate_transitionsAnInactiveAreaToActive() {
		PaymentArea area = newArea();
		area.deactivate();

		area.activate();

		assertThat(area.getStatus()).isEqualTo(PaymentAreaStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentOnAnAlreadyActiveArea() {
		PaymentArea area = newArea();

		area.activate();

		assertThat(area.getStatus()).isEqualTo(PaymentAreaStatus.ACTIVE);
	}

	private static PaymentArea newArea() {
		return new PaymentArea("Colegiaturas", "COL", "Descripcion");
	}
}
