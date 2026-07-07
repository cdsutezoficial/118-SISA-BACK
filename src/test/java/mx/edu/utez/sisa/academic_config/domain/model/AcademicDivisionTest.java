package mx.edu.utez.sisa.academic_config.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicDivisionTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		AcademicDivision division = newDivision();

		assertThat(division.getStatus()).isEqualTo(DivisionStatus.ACTIVE);
	}

	@Test
	void deactivate_transitionsActiveToInactive() {
		AcademicDivision division = newDivision();

		division.deactivate();

		assertThat(division.getStatus()).isEqualTo(DivisionStatus.INACTIVE);
	}

	@Test
	void deactivate_isIdempotentWhenAlreadyInactive() {
		AcademicDivision division = newDivision();
		division.deactivate();

		division.deactivate();

		assertThat(division.getStatus()).isEqualTo(DivisionStatus.INACTIVE);
	}

	@Test
	void activate_transitionsInactiveToActive() {
		AcademicDivision division = newDivision();
		division.deactivate();

		division.activate();

		assertThat(division.getStatus()).isEqualTo(DivisionStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentWhenAlreadyActive() {
		AcademicDivision division = newDivision();

		division.activate();

		assertThat(division.getStatus()).isEqualTo(DivisionStatus.ACTIVE);
	}

	@Test
	void updateDetails_changesFieldsButNotStatus() {
		AcademicDivision division = newDivision();
		division.deactivate();
		UUID newDirectorId = UUID.randomUUID();

		division.updateDetails("Ingenieria", "ING", "Nueva descripcion", newDirectorId);

		assertThat(division.getName()).isEqualTo("Ingenieria");
		assertThat(division.getCode()).isEqualTo("ING");
		assertThat(division.getDescription()).isEqualTo("Nueva descripcion");
		assertThat(division.getDirectorPersonId()).isEqualTo(newDirectorId);
		assertThat(division.getStatus()).isEqualTo(DivisionStatus.INACTIVE);
	}

	private AcademicDivision newDivision() {
		return new AcademicDivision("Diseno", "DSC", "Division de diseno", null);
	}
}
