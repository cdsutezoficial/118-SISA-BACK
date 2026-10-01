package mx.edu.utez.sisa.admission.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.Candidate;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The one definition of "a quota slot is taken", shared by the two callers that
 * need it and deliberately not written twice.
 *
 * <p>These are the two callers: the career dropdown, which asks how full a
 * program is, and {@code CheckoutSlotClaimer}, which asks whether there is room
 * left. They must agree exactly — if the dropdown showed a career as available
 * using a different rule than the claim enforced, the user would be offered a
 * career and then refused at the last step, which is the user-visible version of
 * the original overshoot bug. One JPQL string behind both is what stops that
 * drift, and {@code ProgramAdmissionConfigOptionsQueryIT#dropdownAndClaimerAgreeOnWhatOccupiesASlot}
 * is what keeps the promise honest.
 *
 * <p>Implemented as a shared superinterface rather than as one method with a
 * nullable "exclude this candidate" parameter. The nullable version was rejected
 * on purpose: a null parameter silently changes what is being counted, and a
 * caller that forgot to branch would get a plausible number instead of an error.
 * Here both variants are named for what they do, so the compiler-visible
 * difference is the argument list.
 *
 * <p>Both are keyed by {@code admissionConfigId}, which is what the catalog's
 * {@code findOpenOfferedOptions} subquery has always counted. Before this was fixed
 * these two counted by {@code programId} while the catalog counted by config, and
 * the two answers only ever agreed for programs sold in a single period — see
 * {@code ProgramAdmissionConfigOptionsQueryIT}.
 */
interface AdmissionPaymentOccupancyQueries {

	/**
	 * Occupied slots of one admission config's quota, counted the way the quota is
	 * enforced: a ficha that PAID, or a PENDING ficha that already claimed its
	 * slot at checkout.
	 *
	 * <p>The second clause is the fix for the overshoot. The count has to include
	 * in-flight checkouts because the count is what stops the last-plus-one
	 * applicant, and it is evaluated at the one moment a refusal is still free:
	 * before Evo has been asked to take the money.
	 *
* <p><b>What holds a claim is the ficha's own dates, and nothing else.</b> A
	 * claim occupies a slot while that ficha can still be paid: while it is inside
	 * its private {@code registeredAt + N-day} plazo, and while the config's
	 * {@code closesAt} has not passed. Both comparisons are made against the
	 * {@code Instant} that starts today, so the test is "which day is it", never
	 * "what hour is it" — a ficha issued on the 28th in a process closing on the
	 * 30th is payable through the 30th whatever time of day either lands.
	 *
	 * <p>This clause used to be an {@code EXISTS} over the payment catalog: "is
	 * there still an active ADMISSION concept with a live rate for this program".
	 * Two things made that the wrong rule. It decided when a claim expired from a
	 * date staff edit in the catalog rather than from the window the applicant was
	 * actually promised, so one claim could be released by the calendar and
	 * enforced by the checkout; and it walked the whole price ladder — program,
	 * then level, then general — on every single checkout attempt, which is the
	 * most contended write in the system, all of it inside a transaction holding a
	 * lock on the config row.
	 *
	 * <p>Not depending on any scheduled job is a property worth keeping: a claim is
	 * released because it no longer counts, not because somebody ran a cleanup.
	 *
	 * <p><b>Keyed by config, not by program</b> — this was the drift that let the
	 * picker and the checkout disagree. Counting {@code cfg.programId} summed the
	 * fichas of every period the program had ever been sold in, so a program whose
	 * 2026-1 cycle was full blocked its own 2027-1 cycle at checkout while the
	 * picker, which counts per config, happily offered it. See
	 * {@code ProgramAdmissionConfigOptionsQueryIT#aFullOldCycleDoesNotBlockTheSame
	 * ProgramsNewCycle}.
	 *
	 * <p>Both dates are resolved by the caller from a single {@code Clock}, so the
	 * dropdown, the claim and the sweep all expire against the same day.
	 */
	@Query("""
			SELECT COUNT(pay)
			FROM AdmissionPayment pay
			JOIN Candidate cand ON cand.id = pay.candidateId
			JOIN ProgramAdmissionConfig cfg ON cfg.id = cand.admissionConfigId
			WHERE cand.admissionConfigId = :admissionConfigId
			  AND (pay.paymentStatus = :paid
			       OR (pay.paymentStatus = :pending
			           AND pay.checkoutClaimedAt IS NOT NULL
			           AND cand.registeredAt >= :registeredNoLaterThan
			           AND cfg.closesAt >= :midnightToday))
			""")
	long countOccupiedByConfigId(@Param("admissionConfigId") UUID admissionConfigId,
			@Param("paid") AdmissionPaymentStatus paid, @Param("pending") AdmissionPaymentStatus pending,
			@Param("registeredNoLaterThan") Instant registeredNoLaterThan,
			@Param("midnightToday") Instant midnightToday);

	/**
	 * The same count, ignoring the requesting candidate's own ficha.
	 *
	 * <p>This is the variant the claim itself must use, and the difference is a
	 * real user-facing bug if it is missed. A candidate who starts a checkout, gets
	 * distracted, and clicks "pay" again already holds a claim — and their own claim
	 * is inside the count. On a career that has just sold its last slot,
	 * {@code occupied >= max} would be true and they would be refused a retry for a
	 * slot that is <em>theirs</em>. Excluding themselves makes the comparison mean
	 * what it is meant to mean: "is there room for <em>one more</em> ficha besides
	 * the one I already hold".
	 *
	 * <p>It is also correct for a candidate with no prior claim, because such a
	 * ficha is not in the count either way — a PENDING payment without
	 * {@code checkout_claimed_at} occupies nothing.
	 */
@Query("""
			SELECT COUNT(pay)
			FROM AdmissionPayment pay
			JOIN Candidate cand ON cand.id = pay.candidateId
			JOIN ProgramAdmissionConfig cfg ON cfg.id = cand.admissionConfigId
			WHERE cand.admissionConfigId = :admissionConfigId
			  AND pay.candidateId <> :candidateId
			  AND (pay.paymentStatus = :paid
			       OR (pay.paymentStatus = :pending
			           AND pay.checkoutClaimedAt IS NOT NULL
			           AND cand.registeredAt >= :registeredNoLaterThan
			           AND cfg.closesAt >= :midnightToday))
			""")
	long countOccupiedByConfigIdExcludingCandidate(@Param("admissionConfigId") UUID admissionConfigId,
			@Param("candidateId") UUID candidateId, @Param("paid") AdmissionPaymentStatus paid,
			@Param("pending") AdmissionPaymentStatus pending,
			@Param("registeredNoLaterThan") Instant registeredNoLaterThan,
			@Param("midnightToday") Instant midnightToday);
}
