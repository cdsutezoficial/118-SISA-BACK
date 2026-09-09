package mx.edu.utez.sisa.academic_config.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPeriodStatusTransitionException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidPlanDataException;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Academic period (periodo escolar) aggregate root — a full standalone
 * aggregate, same style as {@link AcademicDivision}/{@link AcademicProgram}/
 * {@code SubjectClassification} (docs: {@code 02-config-academica.md} lines
 * 151-166; plan: {@code docs/plans/2026-07-20-academic-period.md}). Unlike
 * {@link GradeScale} (child-of-{@link AcademicPlan}), this aggregate owns its
 * own table, repository, and controller. {@code name} is deliberately NOT
 * unique — no evidence in the domain doc that it should be, same rationale as
 * {@code SubjectClassification.name}.
 *
 * <p>
 * Two kinds of business rules live here rather than in the use-case layer,
 * because both are pure invariants of this entity's own fields — no
 * repository access needed, mirroring {@link GradeScale}'s
 * {@code validateEntries} precedent (as opposed to {@code AcademicPlan}'s
 * {@code minPassingGrade} range check, which stays in
 * {@code CreateAcademicPlanUseCaseImpl} because it's a comparison against a
 * fixed external constant, not two fields of the same entity):
 * <ul>
 * <li>Date-range validation ({@link #validateDateRanges}): {@code startDate <
 * endDate}, {@code enrollmentStart < enrollmentEnd}, and {@code enrollmentEnd
 * <= endDate} (plan §4 — proposed, applied as the working assumption).
 * Reuses {@link InvalidPlanDataException} rather than a new type — same
 * "simple caller-input range validation" class of error it already covers for
 * {@code AcademicPlan}/{@code PlanLevel}/{@code GradeScale} range checks.
 * <li>Status-transition state machine ({@link #changeStatus}): PO-confirmed
 * (2026-07-20) strictly sequential, forward-only
 * {@code CONFIGURATION -> ENROLLMENT -> ACTIVE -> CLOSED}.
 * </ul>
 * {@code (year, periodNumber)} uniqueness, by contrast, DOES need repository
 * access (cross-record), so it stays in
 * {@code CreateAcademicPeriodUseCaseImpl}/{@code UpdateAcademicPeriodUseCaseImpl}
 * — same split as {@code AcademicPlan}'s {@code version} uniqueness.
 */
@Entity
@Table(name = "academic_period")
public class AcademicPeriod {

	/**
	 * The PO-confirmed (2026-07-20) forward-only sequence. A status maps to
	 * the ONLY status {@link #changeStatus} accepts as a target from it;
	 * {@code CLOSED} is deliberately absent as a key — it is terminal, so
	 * every transition requested from it is rejected. Requesting the SAME
	 * status (e.g. {@code CONFIGURATION -> CONFIGURATION}) is also rejected —
	 * the plan's decision says "Only the immediate-next status ... is a valid
	 * target from any given current status", and the current status is never
	 * its own immediate-next, so this state machine is deliberately NOT
	 * idempotent (unlike every binary ACTIVE/INACTIVE toggle in this module).
	 */
	private static final Map<PeriodStatus, PeriodStatus> NEXT_STATUS = new EnumMap<>(PeriodStatus.class);

	static {
		NEXT_STATUS.put(PeriodStatus.CONFIGURATION, PeriodStatus.ENROLLMENT);
		NEXT_STATUS.put(PeriodStatus.ENROLLMENT, PeriodStatus.ACTIVE);
		NEXT_STATUS.put(PeriodStatus.ACTIVE, PeriodStatus.CLOSED);
	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	/**
	 * Column explicitly named {@code period_year} rather than {@code year} —
	 * {@code YEAR} is a reserved SQL keyword in H2 (and ANSI SQL), so the
	 * default snake_case-of-field-name mapping produced an unquoted
	 * {@code year} column that both DDL generation and the JPQL-derived
	 * {@code search} query failed to parse ("expected identifier"). Found
	 * during the first IT run against H2 — same class of gotcha as a
	 * forgotten {@code UseCaseConfig} bean registration, just at the SQL
	 * layer instead of the Spring wiring layer.
	 */
	@Column(name = "period_year", nullable = false)
	private int year;

	@Column(name = "period_number", nullable = false)
	private int periodNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PeriodType type;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "enrollment_start", nullable = false)
	private LocalDate enrollmentStart;

	@Column(name = "enrollment_end", nullable = false)
	private LocalDate enrollmentEnd;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PeriodStatus status;

	protected AcademicPeriod() {
		// JPA
	}

	/**
	 * @param year         the calendar year the period belongs to; {@code (year, periodNumber)}
	 *                     uniqueness is enforced by {@code CreateAcademicPeriodUseCaseImpl}, not here
	 * @param periodNumber 1, 2, or 3 within {@code year} (docs: "1, 2, 3 dentro del año")
	 */
	public AcademicPeriod(String name, int year, int periodNumber, PeriodType type, LocalDate startDate,
			LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd) {
		validateDateRanges(startDate, endDate, enrollmentStart, enrollmentEnd);
		this.name = name;
		this.year = year;
		this.periodNumber = periodNumber;
		this.type = type;
		this.startDate = startDate;
		this.endDate = endDate;
		this.enrollmentStart = enrollmentStart;
		this.enrollmentEnd = enrollmentEnd;
		this.status = PeriodStatus.CONFIGURATION;
	}

	/**
	 * Updates the catalog fields (PUT {@code /periods/{id}}). {@code status}
	 * is deliberately absent — status transitions are the sole responsibility
	 * of {@link #changeStatus}, same separation as
	 * {@code SubjectClassification#updateDetails}.
	 * {@code (year, periodNumber)} uniqueness (excluding this record's own
	 * row) is revalidated by {@code UpdateAcademicPeriodUseCaseImpl}, not
	 * here.
	 */
	public void updateDetails(String name, int year, int periodNumber, PeriodType type, LocalDate startDate,
			LocalDate endDate, LocalDate enrollmentStart, LocalDate enrollmentEnd) {
		validateDateRanges(startDate, endDate, enrollmentStart, enrollmentEnd);
		this.name = name;
		this.year = year;
		this.periodNumber = periodNumber;
		this.type = type;
		this.startDate = startDate;
		this.endDate = endDate;
		this.enrollmentStart = enrollmentStart;
		this.enrollmentEnd = enrollmentEnd;
	}

	/**
	 * Transitions to {@code target}, enforcing the PO-confirmed (2026-07-20)
	 * strictly sequential, forward-only lifecycle — see {@link #NEXT_STATUS}.
	 * Rejects any target that is not the immediate next status from the
	 * current one: skips (e.g. {@code CONFIGURATION -> ACTIVE}), backward
	 * moves (e.g. {@code CLOSED -> ACTIVE}), and same-status requests are all
	 * invalid the same way — none of them equal {@code NEXT_STATUS.get(status)}.
	 */
	public void changeStatus(PeriodStatus target) {
		PeriodStatus expectedNext = NEXT_STATUS.get(this.status);
		if (expectedNext == null || expectedNext != target) {
			throw new InvalidPeriodStatusTransitionException("Invalid status transition from " + this.status + " to "
					+ target + ": " + (expectedNext == null
							? this.status + " is a terminal status and accepts no further transitions"
							: "the only valid next status from " + this.status + " is " + expectedNext));
		}
		this.status = target;
	}

	/**
	 * Time-driven counterpart to {@link #changeStatus}, invoked by the daily
	 * auto-advance job ({@code AdvanceAcademicPeriodStatusJob}). Walks the same
	 * {@link #NEXT_STATUS} machine forward — and only forward — while the clock
	 * passes each threshold: {@code ENROLLMENT} once {@code today >=
	 * enrollmentStart}, {@code ACTIVE} once {@code today >= startDate}, and
	 * {@code CLOSED} once {@code today > endDate}. Because it advances step by
	 * step over the exact same transitions as the manual PATCH, it legitimately
	 * catches up several elapsed stages in one run when the server was off or
	 * the cycle simply matured — a real elapsed time-span, not the user-action
	 * "skip" the PO rule forbids. It never moves backward and never touches a
	 * terminal ({@code CLOSED}) period.
	 *
	 * @return whether the status changed
	 */
	public boolean advanceByDate(LocalDate today) {
		boolean changed = false;
		while (true) {
			PeriodStatus next = NEXT_STATUS.get(this.status);
			if (next == null) {
				break;
			}
			LocalDate threshold = switch (next) {
				case ENROLLMENT -> this.enrollmentStart;
				case ACTIVE -> this.startDate;
				case CLOSED -> this.endDate;
				case CONFIGURATION -> throw new IllegalStateException(
						"NEXT_STATUS never maps to CONFIGURATION (bug in the state machine)");
			};
			boolean reached = next == PeriodStatus.CLOSED ? today.isAfter(threshold) : !today.isBefore(threshold);
			if (!reached) {
				break;
			}
			this.changeStatus(next);
			changed = true;
		}
		return changed;
	}

	/**
	 * Plan §4: {@code startDate < endDate} and {@code enrollmentStart <
	 * enrollmentEnd} (both confirmed, unambiguous), plus {@code enrollmentEnd
	 * <= endDate} (proposed-not-confirmed, applied as the working assumption
	 * — enrollment cannot close after the period itself ends).
	 */
	private static void validateDateRanges(LocalDate startDate, LocalDate endDate, LocalDate enrollmentStart,
			LocalDate enrollmentEnd) {
		if (startDate == null || endDate == null || !startDate.isBefore(endDate)) {
			throw new InvalidPlanDataException("startDate must be before endDate: [" + startDate + ", " + endDate + "]");
		}
		if (enrollmentStart == null || enrollmentEnd == null || !enrollmentStart.isBefore(enrollmentEnd)) {
			throw new InvalidPlanDataException(
					"enrollmentStart must be before enrollmentEnd: [" + enrollmentStart + ", " + enrollmentEnd + "]");
		}
		if (enrollmentEnd.isAfter(endDate)) {
			throw new InvalidPlanDataException(
					"enrollmentEnd must not be after endDate: enrollmentEnd=" + enrollmentEnd + ", endDate=" + endDate);
		}
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getYear() {
		return year;
	}

	public int getPeriodNumber() {
		return periodNumber;
	}

	public PeriodType getType() {
		return type;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public LocalDate getEnrollmentStart() {
		return enrollmentStart;
	}

	public LocalDate getEnrollmentEnd() {
		return enrollmentEnd;
	}

	public PeriodStatus getStatus() {
		return status;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AcademicPeriod that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
