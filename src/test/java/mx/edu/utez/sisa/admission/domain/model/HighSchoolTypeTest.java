package mx.edu.utez.sisa.admission.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HighSchoolTypeTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		HighSchoolType type = newType();

		assertThat(type.getStatus()).isEqualTo(HighSchoolTypeStatus.ACTIVE);
	}

	@Test
	void constructor_setsName() {
		HighSchoolType type = newType();

		assertThat(type.getName()).isEqualTo("Conalep");
	}

	@Test
	void updateDetails_changesName() {
		HighSchoolType type = newType();

		type.updateDetails("Cobaem");

		assertThat(type.getName()).isEqualTo("Cobaem");
	}

	@Test
	void deactivate_transitionsAnActiveTypeToInactive() {
		HighSchoolType type = newType();

		type.deactivate();

		assertThat(type.getStatus()).isEqualTo(HighSchoolTypeStatus.INACTIVE);
	}

	@Test
	void deactivate_isIdempotentOnAnAlreadyInactiveType() {
		HighSchoolType type = newType();
		type.deactivate();

		type.deactivate();

		assertThat(type.getStatus()).isEqualTo(HighSchoolTypeStatus.INACTIVE);
	}

	@Test
	void activate_transitionsAnInactiveTypeToActive() {
		HighSchoolType type = newType();
		type.deactivate();

		type.activate();

		assertThat(type.getStatus()).isEqualTo(HighSchoolTypeStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentOnAnAlreadyActiveType() {
		HighSchoolType type = newType();

		type.activate();

		assertThat(type.getStatus()).isEqualTo(HighSchoolTypeStatus.ACTIVE);
	}

	private HighSchoolType newType() {
		return new HighSchoolType("Conalep");
	}
}
