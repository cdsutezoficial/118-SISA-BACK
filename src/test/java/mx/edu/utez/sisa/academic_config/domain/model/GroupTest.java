package mx.edu.utez.sisa.academic_config.domain.model;

import mx.edu.utez.sisa.shared.model.Shift;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroupTest {

	@Test
	void constructor_defaultsToOpenStatus() {
		Group group = newGroup("3A");

		assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
	}

	@Test
	void constructor_setsAllFields() {
		UUID generationId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID planLevelId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();

		Group group = new Group(generationId, periodId, planLevelId, programId, "3A", 35, Shift.MORNING);

		assertThat(group.getGenerationId()).isEqualTo(generationId);
		assertThat(group.getPeriodId()).isEqualTo(periodId);
		assertThat(group.getPlanLevelId()).isEqualTo(planLevelId);
		assertThat(group.getProgramId()).isEqualTo(programId);
		assertThat(group.getCode()).isEqualTo("3A");
		assertThat(group.getMaxCapacity()).isEqualTo(35);
		assertThat(group.getShift()).isEqualTo(Shift.MORNING);
	}

	// --- updateDetails ---

	@Test
	void updateDetails_updatesAllFields() {
		Group group = newGroup("3A");
		UUID newGenerationId = UUID.randomUUID();
		UUID newPeriodId = UUID.randomUUID();
		UUID newPlanLevelId = UUID.randomUUID();
		UUID newProgramId = UUID.randomUUID();

		group.updateDetails(newGenerationId, newPeriodId, newPlanLevelId, newProgramId, "3B", 40, Shift.AFTERNOON);

		assertThat(group.getGenerationId()).isEqualTo(newGenerationId);
		assertThat(group.getPeriodId()).isEqualTo(newPeriodId);
		assertThat(group.getPlanLevelId()).isEqualTo(newPlanLevelId);
		assertThat(group.getProgramId()).isEqualTo(newProgramId);
		assertThat(group.getCode()).isEqualTo("3B");
		assertThat(group.getMaxCapacity()).isEqualTo(40);
		assertThat(group.getShift()).isEqualTo(Shift.AFTERNOON);
	}

	@Test
	void updateDetails_leavesStatusUnchanged() {
		Group group = newGroup("3A");
		group.close();

		group.updateDetails(group.getGenerationId(), group.getPeriodId(), group.getPlanLevelId(), group.getProgramId(),
				"3A", 35, Shift.MIXED);

		assertThat(group.getStatus()).isEqualTo(GroupStatus.CLOSED);
	}

	// --- status toggle (simple 2-state, both directions, no sequence) ---

	@Test
	void open_setsStatusToOpen() {
		Group group = newGroup("3A");
		group.close();

		group.open();

		assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
	}

	@Test
	void open_isIdempotentOnAlreadyOpenGroup() {
		Group group = newGroup("3A");

		group.open();

		assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
	}

	@Test
	void close_setsStatusToClosed() {
		Group group = newGroup("3A");

		group.close();

		assertThat(group.getStatus()).isEqualTo(GroupStatus.CLOSED);
	}

	@Test
	void close_isIdempotentOnAlreadyClosedGroup() {
		Group group = newGroup("3A");
		group.close();

		group.close();

		assertThat(group.getStatus()).isEqualTo(GroupStatus.CLOSED);
	}

	@Test
	void closeThenOpen_bothDirectionsAllowedUnlikeAcademicPeriodsSequentialMachine() {
		// Deliberately NOT a sequential state machine like AcademicPeriod's 4
		// states — a Group can bounce OPEN <-> CLOSED freely.
		Group group = newGroup("3A");

		group.close();
		group.open();
		group.close();

		assertThat(group.getStatus()).isEqualTo(GroupStatus.CLOSED);
	}

	private static Group newGroup(String code) {
		return new Group(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), code, 35,
				Shift.MORNING);
	}
}
