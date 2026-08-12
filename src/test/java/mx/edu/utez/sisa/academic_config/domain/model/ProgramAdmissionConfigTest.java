package mx.edu.utez.sisa.academic_config.domain.model;

import mx.edu.utez.sisa.academic_config.shared.exception.InvalidProgramAdmissionConfigDataException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgramAdmissionConfigTest {

	private static final Instant OPENS_AT = Instant.parse("2026-01-01T00:00:00Z");

	private static final Instant CLOSES_AT = Instant.parse("2026-03-01T00:00:00Z");

	@Test
	void constructor_defaultsToOpenStatus() {
		ProgramAdmissionConfig config = newConfig();

		assertThat(config.getStatus()).isEqualTo(ProgramAdmissionConfigStatus.OPEN);
	}

	@Test
	void constructor_alwaysDefaultsSelectionStatusToInReview() {
		// selectionStatus is NEVER a constructor parameter from the client —
		// per plan scoping, no use case in this phase changes it either.
		ProgramAdmissionConfig config = newConfig();

		assertThat(config.getSelectionStatus()).isEqualTo(SelectionStatus.IN_REVIEW);
	}

	@Test
	void constructor_setsAllFields() {
		UUID programId = UUID.randomUUID();
		UUID periodId = UUID.randomUUID();
		UUID targetGenerationId = UUID.randomUUID();

		ProgramAdmissionConfig config = new ProgramAdmissionConfig(programId, periodId, targetGenerationId, true, 50,
				OPENS_AT, CLOSES_AT);

		assertThat(config.getProgramId()).isEqualTo(programId);
		assertThat(config.getPeriodId()).isEqualTo(periodId);
		assertThat(config.getTargetGenerationId()).isEqualTo(targetGenerationId);
		assertThat(config.isOffered()).isTrue();
		assertThat(config.getMaxCandidates()).isEqualTo(50);
		assertThat(config.getOpensAt()).isEqualTo(OPENS_AT);
		assertThat(config.getClosesAt()).isEqualTo(CLOSES_AT);
	}

	@Test
	void constructor_rejectsMaxCandidatesNotGreaterThanZero() {
		assertThatThrownBy(() -> new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				true, 0, OPENS_AT, CLOSES_AT)).isInstanceOf(InvalidProgramAdmissionConfigDataException.class);
	}

	@Test
	void constructor_rejectsNegativeMaxCandidates() {
		assertThatThrownBy(() -> new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				true, -5, OPENS_AT, CLOSES_AT)).isInstanceOf(InvalidProgramAdmissionConfigDataException.class);
	}

	@Test
	void constructor_rejectsClosesAtEqualToOpensAt() {
		assertThatThrownBy(() -> new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				true, 50, OPENS_AT, OPENS_AT)).isInstanceOf(InvalidProgramAdmissionConfigDataException.class);
	}

	@Test
	void constructor_rejectsClosesAtBeforeOpensAt() {
		assertThatThrownBy(() -> new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				true, 50, CLOSES_AT, OPENS_AT)).isInstanceOf(InvalidProgramAdmissionConfigDataException.class);
	}

	// --- updateDetails ---

	@Test
	void updateDetails_updatesCatalogFields() {
		ProgramAdmissionConfig config = newConfig();
		UUID newProgramId = UUID.randomUUID();
		UUID newPeriodId = UUID.randomUUID();
		UUID newGenerationId = UUID.randomUUID();
		Instant newOpensAt = Instant.parse("2026-02-01T00:00:00Z");
		Instant newClosesAt = Instant.parse("2026-04-01T00:00:00Z");

		config.updateDetails(newProgramId, newPeriodId, newGenerationId, false, 100, newOpensAt, newClosesAt);

		assertThat(config.getProgramId()).isEqualTo(newProgramId);
		assertThat(config.getPeriodId()).isEqualTo(newPeriodId);
		assertThat(config.getTargetGenerationId()).isEqualTo(newGenerationId);
		assertThat(config.isOffered()).isFalse();
		assertThat(config.getMaxCandidates()).isEqualTo(100);
		assertThat(config.getOpensAt()).isEqualTo(newOpensAt);
		assertThat(config.getClosesAt()).isEqualTo(newClosesAt);
	}

	@Test
	void updateDetails_leavesStatusUnchanged() {
		ProgramAdmissionConfig config = newConfig();
		config.close();

		config.updateDetails(config.getProgramId(), config.getPeriodId(), config.getTargetGenerationId(), true, 60,
				OPENS_AT, CLOSES_AT);

		assertThat(config.getStatus()).isEqualTo(ProgramAdmissionConfigStatus.CLOSED);
	}

	@Test
	void updateDetails_leavesSelectionStatusUnchanged() {
		ProgramAdmissionConfig config = newConfig();

		config.updateDetails(config.getProgramId(), config.getPeriodId(), config.getTargetGenerationId(), true, 60,
				OPENS_AT, CLOSES_AT);

		assertThat(config.getSelectionStatus()).isEqualTo(SelectionStatus.IN_REVIEW);
	}

	@Test
	void updateDetails_rejectsInvalidMaxCandidates() {
		ProgramAdmissionConfig config = newConfig();

		assertThatThrownBy(() -> config.updateDetails(config.getProgramId(), config.getPeriodId(),
				config.getTargetGenerationId(), true, 0, OPENS_AT, CLOSES_AT))
				.isInstanceOf(InvalidProgramAdmissionConfigDataException.class);
	}

	@Test
	void updateDetails_rejectsClosesAtBeforeOpensAt() {
		ProgramAdmissionConfig config = newConfig();

		assertThatThrownBy(() -> config.updateDetails(config.getProgramId(), config.getPeriodId(),
				config.getTargetGenerationId(), true, 50, CLOSES_AT, OPENS_AT))
				.isInstanceOf(InvalidProgramAdmissionConfigDataException.class);
	}

	// --- status toggle (simple 2-state, both directions, no sequence) ---

	@Test
	void open_setsStatusToOpen() {
		ProgramAdmissionConfig config = newConfig();
		config.close();

		config.open();

		assertThat(config.getStatus()).isEqualTo(ProgramAdmissionConfigStatus.OPEN);
	}

	@Test
	void open_isIdempotentOnAlreadyOpenConfig() {
		ProgramAdmissionConfig config = newConfig();

		config.open();

		assertThat(config.getStatus()).isEqualTo(ProgramAdmissionConfigStatus.OPEN);
	}

	@Test
	void close_setsStatusToClosed() {
		ProgramAdmissionConfig config = newConfig();

		config.close();

		assertThat(config.getStatus()).isEqualTo(ProgramAdmissionConfigStatus.CLOSED);
	}

	@Test
	void close_isIdempotentOnAlreadyClosedConfig() {
		ProgramAdmissionConfig config = newConfig();
		config.close();

		config.close();

		assertThat(config.getStatus()).isEqualTo(ProgramAdmissionConfigStatus.CLOSED);
	}

	@Test
	void closeThenOpen_bothDirectionsAllowed() {
		ProgramAdmissionConfig config = newConfig();

		config.close();
		config.open();
		config.close();

		assertThat(config.getStatus()).isEqualTo(ProgramAdmissionConfigStatus.CLOSED);
	}

	@Test
	void statusToggle_neverTouchesSelectionStatus() {
		ProgramAdmissionConfig config = newConfig();

		config.close();
		config.open();

		assertThat(config.getSelectionStatus()).isEqualTo(SelectionStatus.IN_REVIEW);
	}

	private static ProgramAdmissionConfig newConfig() {
		return new ProgramAdmissionConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true, 50, OPENS_AT,
				CLOSES_AT);
	}
}
