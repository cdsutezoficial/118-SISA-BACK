package mx.edu.utez.sisa.academic_config.domain.model;

import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicProgramTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		AcademicProgram program = newProgram();

		assertThat(program.getStatus()).isEqualTo(ProgramStatus.ACTIVE);
	}

	@Test
	void constructor_acceptsNullContinuityProgramId() {
		AcademicProgram program = newProgram();

		assertThat(program.getContinuityProgramId()).isNull();
	}

	@Test
	void deactivate_transitionsActiveToInactive() {
		AcademicProgram program = newProgram();

		program.deactivate();

		assertThat(program.getStatus()).isEqualTo(ProgramStatus.INACTIVE);
	}

	@Test
	void deactivate_isIdempotentWhenAlreadyInactive() {
		AcademicProgram program = newProgram();
		program.deactivate();

		program.deactivate();

		assertThat(program.getStatus()).isEqualTo(ProgramStatus.INACTIVE);
	}

	@Test
	void activate_transitionsInactiveToActive() {
		AcademicProgram program = newProgram();
		program.deactivate();

		program.activate();

		assertThat(program.getStatus()).isEqualTo(ProgramStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentWhenAlreadyActive() {
		AcademicProgram program = newProgram();

		program.activate();

		assertThat(program.getStatus()).isEqualTo(ProgramStatus.ACTIVE);
	}

	@Test
	void updateDetails_changesFieldsButNotStatus() {
		AcademicProgram program = newProgram();
		program.deactivate();
		UUID divisionId = UUID.randomUUID();
		UUID continuityProgramId = UUID.randomUUID();

		program.updateDetails(divisionId, "Ingenieria en Software", "Ingenieria en Software", "ISC-02",
				AcademicLevel.INGENIERIA, ProgramModality.MIXTA, continuityProgramId, "Nueva descripcion");

		assertThat(program.getDivisionId()).isEqualTo(divisionId);
		assertThat(program.getName()).isEqualTo("Ingenieria en Software");
		assertThat(program.getOfferName()).isEqualTo("Ingenieria en Software");
		assertThat(program.getCode()).isEqualTo("ISC-02");
		assertThat(program.getLevel()).isEqualTo(AcademicLevel.INGENIERIA);
		assertThat(program.getModality()).isEqualTo(ProgramModality.MIXTA);
		assertThat(program.getContinuityProgramId()).isEqualTo(continuityProgramId);
		assertThat(program.getDescription()).isEqualTo("Nueva descripcion");
		assertThat(program.getStatus()).isEqualTo(ProgramStatus.INACTIVE);
	}

	private AcademicProgram newProgram() {
		return new AcademicProgram(UUID.randomUUID(), "Ingenieria en Software", "Ingenieria en Software", "ISC-01",
				AcademicLevel.INGENIERIA, ProgramModality.PRESENCIAL, null, "desc");
	}
}
