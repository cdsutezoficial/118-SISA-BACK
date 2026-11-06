package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.PaymentConceptExpiredException;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Whether a ficha may be paid today: its own payment window and the tuition
 * concept's. Taken out of {@link InitiateFichaPaymentUseCaseImpl} so the online
 * checkout and the cash desk (F1) refuse on exactly the same dates.
 */
public class FichaPaymentGate {

	/**
	 * What an applicant is told when the tuition concept is not payable today for
	 * a reason that is not the calendar: no concept configured, or none with a
	 * rate. It is a catalog problem, so it points at the office instead of at a
	 * date that does not exist.
	 */
	private static final String CONCEPT_NOT_CONFIGURED_MESSAGE =
			"No se encontró pago vigente configurado para este proceso. Comunícate con Servicios escolares.";

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final FichaAmountResolver fichaAmountResolver;

	private final Clock clock;

	private final int fichaDeadlineDays;

	public FichaPaymentGate(ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort,
			FichaAmountResolver fichaAmountResolver, Clock clock, int fichaDeadlineDays) {
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.fichaAmountResolver = fichaAmountResolver;
		this.clock = clock;
		this.fichaDeadlineDays = fichaDeadlineDays;
	}

	/**
	 * Refuses to start a checkout when any of the three date gates has closed,
	 * and does it <em>before</em> the gateway is touched.
	 *
	 * <p>That ordering is the whole point of the method. The gates are checked at
	 * registration too, but registration and payment are days apart and the
	 * catalog can change in between — extending a period is how staff normally
	 * handle a late applicant, and closing it is how they close a cohort. A
	 * ticket issued on the last open day would otherwise stay payable for as long
	 * as the applicant kept clicking, which is exactly the contradiction this
	 * block exists to remove.
	 *
	 * <p>Cutting before the gateway call is what makes a closed period free. Once
	 * {@code INITIATE_CHECKOUT} has run, Evo has an order in {@code PENDING} that
	 * it will remember, and re-opening the period would then hit the duplicate
	 * order rejection from {@code §2.3} of the plan on an id we already burned.
	 *
	 * <p>The gates run in order, and the order is the rule:
	 * <ol>
	 * <li><b>the ficha's payment window</b> — the earlier of its own deadline
	 * (day 0 = registration, plus {@code deadline-days}) and the admission
	 * process' closing date. Both bounds are the same ones the nightly expiry
	 * sweep and the occupancy queries read, so a ficha the portal shows as
	 * payable cannot be one the engine considers closed;</li>
	 * <li><b>the tuition concept's window</b>, which is configuration and must
	 * not be reported as a date the applicant can see; a concept that is missing
	 * or not payable here is a catalog error, not a closed period.</li>
	 * </ol>
	 *
	 * <p>Gate 0 used to be the ficha's own deadline alone and gate 1 the process
	 * closing date, reported as two distinct errors. That distinction was a lie
	 * the applicant could not act on: the process closing means the same thing as
	 * her window closing, and one of them always arrived first. Both now report
	 * the ficha's window as expired, which is the fact she can verify.
	 *
	 * <p>Every boundary is a calendar date in the admission zone, not an
	 * {@code Instant}: a comparison at midnight UTC would refuse a ficha that is
	 * still payable by the applicant's calendar (see {@code §10.2}).
	 *
	 * <p>Prices nothing: it only decides whether the window is open. The caller
	 * prices the checkout separately with the returned config, because the
	 * amount that governs is the live tariff at the click, not the quote frozen
	 * at registration.
	 *
	 * @return the candidate's admission config, so the caller can re-quote the
	 *         tariff for the same program without resolving it twice
	 */
	public ProgramAdmissionConfigQueryPort.AdmissionConfigInfo requireOpen(Candidate candidate) {
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = programAdmissionConfigQueryPort
				.findById(candidate.getAdmissionConfigId())
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException(
						"No existe la configuración de admisión: " + candidate.getAdmissionConfigId()));

		LocalDate today = LocalDate.now(clock);
		ZoneId zone = clock.getZone();

		// Gate 0 — the ficha's payment window (own plazo ∩ process closing date).
		LocalDate admissionClosesOn = config.closesAt().atZone(zone).toLocalDate();
		if (today.isAfter(FichaPaymentWindow.deadlineOf(candidate, admissionClosesOn, fichaDeadlineDays, zone))) {
			throw new FichaPaymentExpiredException("Tu ficha venció. El plazo de pago de " + fichaDeadlineDays
					+ (fichaDeadlineDays == 1 ? " día" : " días") + " terminó.");
		}

		// Gate 2 — the tuition concept exists and is payable today.
		requireConceptWindowOpen(config, today);

		return config;
	}

	/**
	 * The concept window, reworded for the admission flow. The resolver reports
	 * "no concept" and "concept outside its window" with distinct types and
	 * catalog-facing dates; by the time the checkout reaches here both mean the
	 * same thing to the applicant — admissions has no payable tuition configured
	 * for her program — so both keep their type (and therefore their 409 code)
	 * and take the office-facing message instead of a date that would send her to
	 * look at a screen she cannot fix.
	 */
	private void requireConceptWindowOpen(ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config, LocalDate today) {
		try {
			fichaAmountResolver.requirePayableOn(config.programId(), today);
		} catch (PaymentConceptExpiredException ex) {
			throw new PaymentConceptExpiredException(CONCEPT_NOT_CONFIGURED_MESSAGE);
		} catch (FichaPaymentConceptNotFoundException ex) {
			throw new FichaPaymentConceptNotFoundException(CONCEPT_NOT_CONFIGURED_MESSAGE);
		}
	}
}
