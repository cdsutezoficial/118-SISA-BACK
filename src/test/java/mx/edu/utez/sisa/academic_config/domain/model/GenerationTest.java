package mx.edu.utez.sisa.academic_config.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		Generation generation = newGeneration(2026, 7);

		assertThat(generation.getStatus()).isEqualTo(GenerationStatus.ACTIVE);
	}

	@Test
	void constructor_computesCodeFromStartPeriodYearAndNumber() {
		Generation generation = newGeneration(2026, 7);

		assertThat(generation.getCode()).isEqualTo("2026-7");
	}

	@Test
	void constructor_setsAllFields() {
		UUID planId = UUID.randomUUID();
		UUID startPeriodId = UUID.randomUUID();
		UUID programId = UUID.randomUUID();

		Generation generation = new Generation(planId, startPeriodId, programId, 3, 2026);

		assertThat(generation.getPlanId()).isEqualTo(planId);
		assertThat(generation.getStartPeriodId()).isEqualTo(startPeriodId);
		assertThat(generation.getProgramId()).isEqualTo(programId);
		assertThat(generation.getNumber()).isEqualTo(3);
	}

	// --- updateDetails ---

	@Test
	void updateDetails_recomputesCodeWhenStartPeriodYearOrNumberChange() {
		Generation generation = newGeneration(2026, 7);

		generation.updateDetails(generation.getPlanId(), UUID.randomUUID(), generation.getProgramId(), 8, 2027);

		assertThat(generation.getCode()).isEqualTo("2027-8");
		assertThat(generation.getNumber()).isEqualTo(8);
	}

	@Test
	void updateDetails_leavesStatusUnchanged() {
		Generation generation = newGeneration(2026, 7);
		generation.finish();

		generation.updateDetails(generation.getPlanId(), generation.getStartPeriodId(), generation.getProgramId(), 7,
				2026);

		assertThat(generation.getStatus()).isEqualTo(GenerationStatus.FINISHED);
	}

	// --- status toggle (simple 2-state, both directions, no sequence) ---

	@Test
	void activate_setsStatusToActive() {
		Generation generation = newGeneration(2026, 7);
		generation.finish();

		generation.activate();

		assertThat(generation.getStatus()).isEqualTo(GenerationStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentOnAlreadyActiveGeneration() {
		Generation generation = newGeneration(2026, 7);

		generation.activate();

		assertThat(generation.getStatus()).isEqualTo(GenerationStatus.ACTIVE);
	}

	@Test
	void finish_setsStatusToFinished() {
		Generation generation = newGeneration(2026, 7);

		generation.finish();

		assertThat(generation.getStatus()).isEqualTo(GenerationStatus.FINISHED);
	}

	@Test
	void finish_isIdempotentOnAlreadyFinishedGeneration() {
		Generation generation = newGeneration(2026, 7);
		generation.finish();

		generation.finish();

		assertThat(generation.getStatus()).isEqualTo(GenerationStatus.FINISHED);
	}

	@Test
	void finishThenActivate_bothDirectionsAllowedUnlikeAcademicPeriodsSequentialMachine() {
		// Deliberately NOT a sequential state machine like AcademicPeriod's 4
		// states — a Generation can bounce ACTIVE <-> FINISHED freely.
		Generation generation = newGeneration(2026, 7);

		generation.finish();
		generation.activate();
		generation.finish();

		assertThat(generation.getStatus()).isEqualTo(GenerationStatus.FINISHED);
	}

	private static Generation newGeneration(int startPeriodYear, int number) {
		return new Generation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), number, startPeriodYear);
	}
}
