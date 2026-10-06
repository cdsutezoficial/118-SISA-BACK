package mx.edu.utez.sisa.admission.domain.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import mx.edu.utez.sisa.admission.domain.model.Candidate;

/**
 * The one place that turns "how long a ficha may take to be paid" into dates.
 *
 * <p>It exists because three different places need that same arithmetic and were
 * each doing it slightly differently: the checkout's window check, the sweep that
 * expires unpaid fichas, and the occupancy queries that decide whether a claim
 * still holds a slot. A rule that three places approximate is a rule that will
 * eventually disagree with itself, and the symptom is not a crash — it is one
 * screen saying a career is full while the next one says there is room.
 *
 * <p>Every method here takes the zone explicitly. The admission zone is configured
 * ({@code UseCaseConfig}) rather than inherited from the JVM, and the callers all
 * already hold the injected {@code Clock}, so passing {@code clock.getZone()} is
 * how a caller stays on the same day the applicant is looking at.
 */
public final class FichaPaymentWindow {

	private FichaPaymentWindow() {
	}

	/**
	 * The last day a ficha may be paid, which is the earlier of its own plazo and
	 * the process's closing date.
	 *
	 * <p>Taking the minimum is the whole point: a ficha is never payable past the
	 * day sales close, however much of its own plazo is left, and it is never
	 * denied a day of its plazo early because the process happens to close soon.
	 * Whichever bound binds first, the applicant keeps the other one in reserve and
	 * never has to know about it.
	 *
	 * @param candidate      whose ficha is being dated; supplies the registration day
	 * @param processClosesOn the day sales close for the ficha's process, or
	 *                       {@code null} when no closing date is on record
	 * @param paymentWindowDays days allowed from registration, registration being
	 *                          day 0
	 * @param zone           the admission zone
	 */
	public static LocalDate deadlineOf(Candidate candidate, LocalDate processClosesOn, int paymentWindowDays,
			ZoneId zone) {
		LocalDate ownDeadline = candidate.paymentDeadline(zone, paymentWindowDays);
		if (processClosesOn == null) {
			return ownDeadline;
		}
		return ownDeadline.isBefore(processClosesOn) ? ownDeadline : processClosesOn;
	}

	/**
	 * Whether this ficha can still be paid on {@code today}.
	 *
	 * <p>A {@code LocalDate} comparison on purpose. The rule a ficha follows is
	 * written in days ("10 days from registration"), and comparing timestamps would
	 * make it secretly an hours rule: one applicant registered at 23:00 would lose
	 * almost a full day to somebody registered at 01:00 the same morning. Both dates
	 * are already resolved in the admission zone by the callers, so this compares
	 * days and nothing else.
	 */
	public static boolean isPayableOn(Candidate candidate, LocalDate processClosesOn, int paymentWindowDays,
			LocalDate today, ZoneId zone) {
		return !today.isAfter(deadlineOf(candidate, processClosesOn, paymentWindowDays, zone));
	}

	/**
	 * First instant of {@code today} in the given zone.
	 *
	 * <p>Queries store timestamps but the rules are written in days, so a
	 * {@code >=} comparison against the start of today is how a query answers
	 * "which day is it". That is what keeps a process closing on the 26th open for
	 * the whole of the 26th instead of expiring at the midnight that started it.
	 */
	public static Instant startOfDay(LocalDate today, ZoneId zone) {
		return today.atStartOfDay(zone).toInstant();
	}

	/**
	 * The earliest registration a ficha can still carry and be payable today.
	 *
	 * <p>{@code today - N}, the exact mirror of {@link Candidate#paymentDeadline}
	 * adding {@code N} to the registration day. A ficha registered on the 20th has
	 * its deadline on the 30th, so on the 30th the 20th is the earliest registration
	 * still alive and anything older has lapsed. Writing it as {@code today - (N-1)}
	 * here would hand everybody an extra day, and it would disagree with the
	 * deadline the very same class computes — the one bug this method must not have
	 * is two different answers to "when does a ficha expire" in one place.
	 *
	 * <p>A {@code LocalDate} subtraction and not a duration on timestamps, because
	 * the rule is about calendar days.
	 */
	public static Instant latestPayableRegistration(LocalDate today, int paymentWindowDays, ZoneId zone) {
		return today.minusDays(paymentWindowDays).atStartOfDay(zone).toInstant();
	}
}