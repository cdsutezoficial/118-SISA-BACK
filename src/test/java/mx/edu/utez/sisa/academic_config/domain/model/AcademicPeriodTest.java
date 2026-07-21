package mx.edu.utez.sisa.academic_config.domain.model;

import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPeriodStatusTransitionException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicPeriodTest {

	private static final LocalDate START = LocalDate.of(2026, 1, 5);
	private static final LocalDate END = LocalDate.of(2026, 4, 30);
	private static final LocalDate ENROLLMENT_START = LocalDate.of(2025, 12, 1);
	private static final LocalDate ENROLLMENT_END = LocalDate.of(2025, 12, 20);

	// --- construction / defaults ---

	@Test
	void constructor_defaultsToConfigurationStatus() {
		AcademicPeriod period = newPeriod();

		assertThat(period.getStatus()).isEqualTo(PeriodStatus.CONFIGURATION);
	}

	@Test
	void constructor_setsAllFields() {
		AcademicPeriod period = newPeriod();

		assertThat(period.getName()).isEqualTo("Enero-Abril 2026");
		assertThat(period.getYear()).isEqualTo(2026);
		assertThat(period.getPeriodNumber()).isEqualTo(1);
		assertThat(period.getType()).isEqualTo(PeriodType.CUATRIMESTRAL);
		assertThat(period.getStartDate()).isEqualTo(START);
		assertThat(period.getEndDate()).isEqualTo(END);
		assertThat(period.getEnrollmentStart()).isEqualTo(ENROLLMENT_START);
		assertThat(period.getEnrollmentEnd()).isEqualTo(ENROLLMENT_END);
	}

	// --- date-range validation (rule 1: startDate < endDate) ---

	@Test
	void constructor_rejectsStartDateNotBeforeEndDate() {
		assertThatThrownBy(() -> new AcademicPeriod("X", 2026, 1, PeriodType.CUATRIMESTRAL, END, START,
				ENROLLMENT_START, ENROLLMENT_END)).isInstanceOf(InvalidPlanDataException.class);
	}

	@Test
	void constructor_rejectsStartDateEqualToEndDate() {
		assertThatThrownBy(() -> new AcademicPeriod("X", 2026, 1, PeriodType.CUATRIMESTRAL, START, START,
				ENROLLMENT_START, ENROLLMENT_END)).isInstanceOf(InvalidPlanDataException.class);
	}

	// --- date-range validation (rule 2: enrollmentStart < enrollmentEnd) ---

	@Test
	void constructor_rejectsEnrollmentStartNotBeforeEnrollmentEnd() {
		assertThatThrownBy(() -> new AcademicPeriod("X", 2026, 1, PeriodType.CUATRIMESTRAL, START, END,
				ENROLLMENT_END, ENROLLMENT_START)).isInstanceOf(InvalidPlanDataException.class);
	}

	@Test
	void constructor_rejectsEnrollmentStartEqualToEnrollmentEnd() {
		assertThatThrownBy(() -> new AcademicPeriod("X", 2026, 1, PeriodType.CUATRIMESTRAL, START, END,
				ENROLLMENT_START, ENROLLMENT_START)).isInstanceOf(InvalidPlanDataException.class);
	}

	// --- date-range validation (rule 3: enrollmentEnd <= endDate) ---

	@Test
	void constructor_rejectsEnrollmentEndAfterEndDate() {
		assertThatThrownBy(() -> new AcademicPeriod("X", 2026, 1, PeriodType.CUATRIMESTRAL, START, END,
				ENROLLMENT_START, END.plusDays(1))).isInstanceOf(InvalidPlanDataException.class);
	}

	@Test
	void constructor_allowsEnrollmentEndEqualToEndDate() {
		AcademicPeriod period = new AcademicPeriod("X", 2026, 1, PeriodType.CUATRIMESTRAL, START, END,
				ENROLLMENT_START, END);

		assertThat(period.getEnrollmentEnd()).isEqualTo(END);
	}

	// --- updateDetails revalidates the same rules ---

	@Test
	void updateDetails_revalidatesDateRanges() {
		AcademicPeriod period = newPeriod();

		assertThatThrownBy(() -> period.updateDetails("X", 2026, 1, PeriodType.CUATRIMESTRAL, END, START,
				ENROLLMENT_START, ENROLLMENT_END)).isInstanceOf(InvalidPlanDataException.class);
	}

	@Test
	void updateDetails_successfulUpdateChangesFieldsAndLeavesStatusUnchanged() {
		AcademicPeriod period = newPeriod();

		period.updateDetails("Renombrado", 2027, 2, PeriodType.SEMESTRAL, START.plusYears(1), END.plusYears(1),
				ENROLLMENT_START.plusYears(1), ENROLLMENT_END.plusYears(1));

		assertThat(period.getName()).isEqualTo("Renombrado");
		assertThat(period.getYear()).isEqualTo(2027);
		assertThat(period.getPeriodNumber()).isEqualTo(2);
		assertThat(period.getType()).isEqualTo(PeriodType.SEMESTRAL);
		assertThat(period.getStatus()).isEqualTo(PeriodStatus.CONFIGURATION);
	}

	// --- status-transition state machine ---

	@Test
	void changeStatus_configurationToEnrollmentSucceeds() {
		AcademicPeriod period = newPeriod();

		period.changeStatus(PeriodStatus.ENROLLMENT);

		assertThat(period.getStatus()).isEqualTo(PeriodStatus.ENROLLMENT);
	}

	@Test
	void changeStatus_enrollmentToActiveSucceeds() {
		AcademicPeriod period = newPeriod();
		period.changeStatus(PeriodStatus.ENROLLMENT);

		period.changeStatus(PeriodStatus.ACTIVE);

		assertThat(period.getStatus()).isEqualTo(PeriodStatus.ACTIVE);
	}

	@Test
	void changeStatus_activeToClosedSucceeds() {
		AcademicPeriod period = newPeriod();
		period.changeStatus(PeriodStatus.ENROLLMENT);
		period.changeStatus(PeriodStatus.ACTIVE);

		period.changeStatus(PeriodStatus.CLOSED);

		assertThat(period.getStatus()).isEqualTo(PeriodStatus.CLOSED);
	}

	@Test
	void changeStatus_rejectsSkippingConfigurationToActive() {
		AcademicPeriod period = newPeriod();

		assertThatThrownBy(() -> period.changeStatus(PeriodStatus.ACTIVE))
				.isInstanceOf(InvalidPeriodStatusTransitionException.class);
		assertThat(period.getStatus()).isEqualTo(PeriodStatus.CONFIGURATION);
	}

	@Test
	void changeStatus_rejectsSkippingConfigurationToClosed() {
		AcademicPeriod period = newPeriod();

		assertThatThrownBy(() -> period.changeStatus(PeriodStatus.CLOSED))
				.isInstanceOf(InvalidPeriodStatusTransitionException.class);
	}

	@Test
	void changeStatus_rejectsSkippingEnrollmentToClosed() {
		AcademicPeriod period = newPeriod();
		period.changeStatus(PeriodStatus.ENROLLMENT);

		assertThatThrownBy(() -> period.changeStatus(PeriodStatus.CLOSED))
				.isInstanceOf(InvalidPeriodStatusTransitionException.class);
		assertThat(period.getStatus()).isEqualTo(PeriodStatus.ENROLLMENT);
	}

	@Test
	void changeStatus_rejectsBackwardActiveToEnrollment() {
		AcademicPeriod period = newPeriod();
		period.changeStatus(PeriodStatus.ENROLLMENT);
		period.changeStatus(PeriodStatus.ACTIVE);

		assertThatThrownBy(() -> period.changeStatus(PeriodStatus.ENROLLMENT))
				.isInstanceOf(InvalidPeriodStatusTransitionException.class);
		assertThat(period.getStatus()).isEqualTo(PeriodStatus.ACTIVE);
	}

	@Test
	void changeStatus_rejectsBackwardClosedToActive() {
		AcademicPeriod period = newPeriod();
		period.changeStatus(PeriodStatus.ENROLLMENT);
		period.changeStatus(PeriodStatus.ACTIVE);
		period.changeStatus(PeriodStatus.CLOSED);

		assertThatThrownBy(() -> period.changeStatus(PeriodStatus.ACTIVE))
				.isInstanceOf(InvalidPeriodStatusTransitionException.class);
	}

	@Test
	void changeStatus_rejectsBackwardClosedToConfiguration() {
		AcademicPeriod period = newPeriod();
		period.changeStatus(PeriodStatus.ENROLLMENT);
		period.changeStatus(PeriodStatus.ACTIVE);
		period.changeStatus(PeriodStatus.CLOSED);

		assertThatThrownBy(() -> period.changeStatus(PeriodStatus.CONFIGURATION))
				.isInstanceOf(InvalidPeriodStatusTransitionException.class);
	}

	@Test
	void changeStatus_closedIsTerminalAndRejectsAnyFurtherTransition() {
		AcademicPeriod period = newPeriod();
		period.changeStatus(PeriodStatus.ENROLLMENT);
		period.changeStatus(PeriodStatus.ACTIVE);
		period.changeStatus(PeriodStatus.CLOSED);

		assertThatThrownBy(() -> period.changeStatus(PeriodStatus.CLOSED))
				.isInstanceOf(InvalidPeriodStatusTransitionException.class);
		assertThat(period.getStatus()).isEqualTo(PeriodStatus.CLOSED);
	}

	@Test
	void changeStatus_rejectsRequestingTheSameStatusAsCurrent() {
		// Deliberately NOT idempotent, unlike the binary ACTIVE/INACTIVE
		// toggles elsewhere in this module — see AcademicPeriod's javadoc on
		// NEXT_STATUS: "Only the immediate-next status ... is a valid target",
		// and a status is never its own immediate-next.
		AcademicPeriod period = newPeriod();

		assertThatThrownBy(() -> period.changeStatus(PeriodStatus.CONFIGURATION))
				.isInstanceOf(InvalidPeriodStatusTransitionException.class);
	}

	private static AcademicPeriod newPeriod() {
		return new AcademicPeriod("Enero-Abril 2026", 2026, 1, PeriodType.CUATRIMESTRAL, START, END, ENROLLMENT_START,
				ENROLLMENT_END);
	}
}
