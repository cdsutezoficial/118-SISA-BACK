package mx.edu.utez.sisa.admission.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicProgramJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.ProgramAdmissionConfigJpaRepository;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.infrastructure.persistence.AdmissionPaymentJpaRepository;
import mx.edu.utez.sisa.admission.infrastructure.persistence.AdmissionPaymentRepositoryAdapter;
import mx.edu.utez.sisa.admission.infrastructure.persistence.AdmissionQuotaAdapter;
import mx.edu.utez.sisa.admission.infrastructure.persistence.CandidateJpaRepository;
import mx.edu.utez.sisa.admission.infrastructure.persistence.CandidateRepositoryAdapter;
import mx.edu.utez.sisa.admission.infrastructure.persistence.CheckoutAttemptRepositoryAdapter;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The mutual exclusion that {@link CheckoutSlotClaimerTest} cannot prove.
 *
 * <p>That class mocks {@code AdmissionQuotaPort}, so it asserts the lock is
 * <em>requested</em> before the count is read and not much more. Everything
 * interesting about the quota happens between two transactions racing for the same
 * row, and a mock has no rows. This class therefore uses a real database, real
 * {@code SELECT … FOR UPDATE}, and two threads released at the same instant.
 *
 * <p>Two outcomes are being pinned, and they pull in opposite directions:
 * <ol>
 * <li><b>Two different candidates, one place left → exactly one wins.</b> If the
 * count were a plain {@code SELECT} read outside the lock, both would see zero
 * occupied and both would claim: the career would oversell, which is the whole bug
 * this feature exists to prevent. A pass here means the second thread's count saw
 * the first thread's committed row.</li>
 * <li><b>The same candidate twice → both succeed.</b> The self-exclusion in
 * {@code countOccupiedByConfigIdExcludingCandidate} is a correctness requirement,
 * not an optimisation, and it is the kind of thing that passes in a unit test with
 * an argument captor while being wrong in the query.</li>
 * </ol>
 *
 * <h2>Why {@code NOT_SUPPORTED}</h2>
 * {@code @DataJpaTest} wraps each test in one transaction on one connection, which
 * would serialise the threads and turn this into a tautology. Suspending it gives
 * every thread its own connection and lets each {@code REQUIRES_NEW} claim commit
 * for real, so the second thread is genuinely reading committed data.
 *
 * <h2>Warning: this class recreates the schema it points at</h2>
 * There is no embedded test database, so {@code ddl-auto=create-drop} runs against
 * whatever {@code DB_URL} names. Point it at a throwaway database:
 *
 * <pre>
 * $env:DB_URL = "jdbc:mysql://localhost:3306/sisa_it?createDatabaseIfNotExist=true&amp;useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC"
 * </pre>
 */
@DataJpaTest
@Import({ CheckoutSlotClaimer.class, AdmissionQuotaAdapter.class, AdmissionPaymentRepositoryAdapter.class,
		CandidateRepositoryAdapter.class, CheckoutAttemptRepositoryAdapter.class,
		CheckoutSlotClaimerConcurrencyIT.FixedClock.class })
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheckoutSlotClaimerConcurrencyIT {

	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	/**
	 * Pinned so the claim-expiry comparison cannot slide with the wall clock and
	 * silently change what "open" means between runs.
	 */
	private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

	private static final Instant NOW = TODAY.atTime(12, 0).atZone(ZONE).toInstant();

	/** A checkout tariff; the claim stores it, the count ignores it. */
	private static final BigDecimal CHECKOUT_AMOUNT = new BigDecimal("500.00");

	/**
	 * The ficha's payment window, the default the application ships with. The claim
	 * needs it because a claim only holds its slot while the ficha is still payable,
	 * and it has to be the same number the count compares against.
	 */
	private static final int PAYMENT_WINDOW_DAYS = 10;

	@Autowired
	private CheckoutSlotClaimer claimer;

	@Autowired
	private AcademicProgramJpaRepository academicProgramJpaRepository;

	@Autowired
	private ProgramAdmissionConfigJpaRepository programConfigJpaRepository;

	@Autowired
	private CandidateJpaRepository candidateJpaRepository;

	@Autowired
	private AdmissionPaymentJpaRepository admissionPaymentJpaRepository;

	@Test
	void onlyOneOfTwoCandidatesGetsTheLastPlace() throws Exception {
		ProgramAdmissionConfig config = saveConfig(1);
		UUID first = saveCandidateWithPendingFicha(config.getId());
		UUID second = saveCandidateWithPendingFicha(config.getId());

		List<Throwable> outcomes = race(first, second);

		assertThat(claimFailures(outcomes)).as("with one place left, exactly one candidate is refused").hasSize(1);
		assertThat(claimSuccesses(outcomes)).as("the other candidate is not refused").hasSize(1);
		assertThat(claimedCount(config.getId()))
				.as("the quota of 1 holds: the loser's transaction left nothing behind")
				.isEqualTo(1);
	}

	@Test
	void aCandidateMayClaimTwiceForTheSamePlace() throws Exception {
		ProgramAdmissionConfig config = saveConfig(1);
		UUID candidateId = saveCandidateWithPendingFicha(config.getId());

		List<Throwable> outcomes = race(candidateId, candidateId);

		assertThat(claimFailures(outcomes)).as("a candidate retrying is not refused for the place they hold").isEmpty();
		assertThat(claimSuccesses(outcomes)).hasSize(2);
	}

	/**
	 * Releases both callers at the same instant and collects whatever each one did.
	 *
	 * <p>The returned list holds {@code null} for a caller that claimed and the
	 * thrown exception for one that did not, which is why it is a list of
	 * {@link Throwable} rather than of outcomes with a flag.
	 */
	private List<Throwable> race(UUID... candidateIds) throws Exception {
		CountDownLatch ready = new CountDownLatch(candidateIds.length);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(candidateIds.length);
		try {
			List<Future<Throwable>> futures = new java.util.ArrayList<>();
			for (UUID candidateId : candidateIds) {
				Callable<Throwable> attempt = () -> {
					ready.countDown();
					// Both threads park here so neither can finish its first read
					// before the other has started, which is the whole point.
					go.await(10, TimeUnit.SECONDS);
					try {
						claimer.claim(candidateId, CHECKOUT_AMOUNT);
						return null;
					} catch (RuntimeException e) {
						return e;
					}
				};
				futures.add(pool.submit(attempt));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).as("both threads started").isTrue();
			go.countDown();

			List<Throwable> outcomes = new java.util.ArrayList<>();
			for (Future<Throwable> future : futures) {
				outcomes.add(future.get(20, TimeUnit.SECONDS));
			}
			return outcomes;
		} finally {
			pool.shutdownNow();
		}
	}

	private static List<Throwable> claimFailures(List<Throwable> outcomes) {
		return outcomes.stream().filter(ProgramAdmissionConfigCapacityReachedException.class::isInstance).toList();
	}

	private static List<Throwable> claimSuccesses(List<Throwable> outcomes) {
		return outcomes.stream().filter(java.util.Objects::isNull).toList();
	}

	/**
	 * Reads the occupancy straight from the database rather than from the claimer's
	 * port, so a claim that was written and then rolled back cannot hide here.
	 *
	 * <p>The two cutoffs come from {@code FichaPaymentWindow} for the same reason
	 * the production code uses it: the query asks "which day is it" in the admission
	 * zone, and a test that computed the day differently would be asserting against
	 * a boundary the application does not have.
	 */
	private long claimedCount(UUID admissionConfigId) {
		ZoneId zone = ZONE;
		return admissionPaymentJpaRepository.countOccupiedByConfigId(admissionConfigId,
				mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PAID,
				mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus.PENDING,
				FichaPaymentWindow.latestPayableRegistration(TODAY, PAYMENT_WINDOW_DAYS, zone),
				FichaPaymentWindow.startOfDay(TODAY, zone));
	}

	/**
	 * A config and its program. Deliberately no payment concept and no rate.
	 *
	 * <p>Until the occupancy query stopped walking the price ladder, this method had
	 * to seed an ADMISSION concept and a rate or every count read zero and there was
	 * no "last place" to compete over. Its absence now is the point: what holds a
	 * claim is the ficha's own dates and this config's window, so a career whose
	 * catalog is not even set up yet still holds its places correctly instead of
	 * reporting itself empty and overselling.
	 */
	private ProgramAdmissionConfig saveConfig(int maxCandidates) {
		AcademicProgram program = new AcademicProgram(UUID.randomUUID(), "Ingeniería en Software",
				"Software " + UUID.randomUUID(), "code-" + UUID.randomUUID(), AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, null, null);
		academicProgramJpaRepository.save(program);

		ProgramAdmissionConfig config = new ProgramAdmissionConfig(program.getId(), UUID.randomUUID(), UUID.randomUUID(),
				true, maxCandidates, NOW.minusSeconds(86_400), NOW.plusSeconds(86_400));
		programConfigJpaRepository.save(config);

		return config;
	}

	private UUID saveCandidateWithPendingFicha(UUID admissionConfigId) {
		Candidate candidate = candidateJpaRepository
				.save(new Candidate(UUID.randomUUID(), admissionConfigId, "FOLIO-" + UUID.randomUUID(), false, true,
						null));
		admissionPaymentJpaRepository.save(new AdmissionPayment(candidate.getId(),
				AdmissionPaymentConcept.ADMISSION_FICHA, new BigDecimal("1578.00"), "REF-" + UUID.randomUUID(),
				TODAY.plusDays(5)));
		return candidate.getId();
	}

	/**
	 * {@code UseCaseConfig} supplies a zone-pinned clock in production; the same
	 * fixed instant here keeps the expiry comparison reproducible.
	 */
	static class FixedClock {
		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZONE);
		}
	}
}
