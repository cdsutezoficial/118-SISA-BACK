package mx.edu.utez.sisa.admission.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfig;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.AcademicProgramJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.ProgramAdmissionConfigJpaRepository;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionQuotaPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateNotificationPort;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.infrastructure.persistence.AdmissionQuotaAdapter;
import mx.edu.utez.sisa.admission.infrastructure.persistence.CandidateJpaRepository;
import mx.edu.utez.sisa.admission.infrastructure.persistence.CandidateRepositoryAdapter;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigCapacityReachedException;
import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import mx.edu.utez.sisa.finance.domain.port.out.CheckoutAttemptRepository;
import mx.edu.utez.sisa.finance.infrastructure.persistence.CheckoutAttemptRepositoryAdapter;
import mx.edu.utez.sisa.finance.infrastructure.persistence.PaymentJpaRepository;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * F1: dos cajeros cobran en efectivo, al mismo tiempo, el último lugar de una
 * carrera a dos candidatos distintos. Solo uno puede cobrar; al otro se le
 * rechaza antes de registrar nada (el dinero no se recibe).
 *
 * <p>Lo que se prueba es el candado y la cuenta de {@code confirmCash}
 * ({@code lockQuota} + {@code countOccupiedExcluding}) contra MySQL real, con
 * dos transacciones compitiendo por la misma fila. El lado de Finanzas es un
 * doble que marca el cargo como pagado en la misma transacción, que es lo único
 * que la cuenta del cupo lee de él.
 *
 * <p>{@code NOT_SUPPORTED} por la misma razón que
 * {@link CheckoutSlotClaimerConcurrencyIT}: cada hilo necesita su propia
 * conexión y su propio commit.
 *
 * <h2>Cuidado: esta clase recrea el esquema al que apunta</h2>
 * {@code ddl-auto=create-drop}: apunta {@code DB_URL} a una base desechable.
 */
@DataJpaTest
@Import({ AdmissionQuotaAdapter.class, CandidateRepositoryAdapter.class, CheckoutAttemptRepositoryAdapter.class,
		CashPaymentQuotaConcurrencyIT.Wiring.class })
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CashPaymentQuotaConcurrencyIT {

	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

	private static final Instant NOW = TODAY.atTime(12, 0).atZone(ZONE).toInstant();

	private static final BigDecimal AMOUNT = new BigDecimal("500.00");

	@Autowired
	private ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

	@Autowired
	private AcademicProgramJpaRepository academicProgramJpaRepository;

	@Autowired
	private ProgramAdmissionConfigJpaRepository programConfigJpaRepository;

	@Autowired
	private CandidateJpaRepository candidateJpaRepository;

	@Autowired
	private PaymentJpaRepository paymentJpaRepository;

	@Autowired
	private AdmissionQuotaPort admissionQuotaPort;

	@Test
	void twoCashiersCannotSellTheLastPlaceTwice() throws Exception {
		ProgramAdmissionConfig config = saveConfig(1);
		UUID first = saveCandidateWithPendingFicha(config.getId());
		UUID second = saveCandidateWithPendingFicha(config.getId());

		List<Throwable> outcomes = race(first, second);

		assertThat(outcomes.stream().filter(ProgramAdmissionConfigCapacityReachedException.class::isInstance))
				.as("con un lugar, a un candidato se le rechaza el cobro").hasSize(1);
		assertThat(outcomes.stream().filter(Objects::isNull)).as("al otro se le cobra").hasSize(1);
		assertThat(admissionQuotaPort.countOccupiedExcluding(config.getId(), null,
				FichaPaymentWindow.attemptsHoldingSince(NOW, 60)))
				.as("el cupo de 1 se respeta: el rechazado no dejó cargo pagado").isEqualTo(1);
	}

	@Test
	void withTwoPlacesBothCashiersCollect() throws Exception {
		ProgramAdmissionConfig config = saveConfig(2);
		UUID first = saveCandidateWithPendingFicha(config.getId());
		UUID second = saveCandidateWithPendingFicha(config.getId());

		List<Throwable> outcomes = race(first, second);

		assertThat(outcomes).containsOnlyNulls();
	}

	private List<Throwable> race(UUID... candidateIds) throws Exception {
		CountDownLatch ready = new CountDownLatch(candidateIds.length);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(candidateIds.length);
		try {
			List<Future<Throwable>> futures = new ArrayList<>();
			for (UUID candidateId : candidateIds) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await(10, TimeUnit.SECONDS);
					try {
						confirmAdmissionPaymentUseCase.confirmCash(candidateId, AMOUNT, false, UUID.randomUUID(), "Caja");
						return null;
					} catch (RuntimeException e) {
						return e;
					}
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			List<Throwable> outcomes = new ArrayList<>();
			for (Future<Throwable> future : futures) {
				outcomes.add(future.get(30, TimeUnit.SECONDS));
			}
			return outcomes;
		} finally {
			pool.shutdownNow();
		}
	}

	private ProgramAdmissionConfig saveConfig(int maxCandidates) {
		AcademicProgram program = new AcademicProgram(UUID.randomUUID(), "Ingeniería en Software",
				"Software " + UUID.randomUUID(), "code-" + UUID.randomUUID(), AcademicLevel.INGENIERIA,
				ProgramModality.PRESENCIAL, null, null, null);
		academicProgramJpaRepository.save(program);
		ProgramAdmissionConfig config = new ProgramAdmissionConfig(program.getId(), UUID.randomUUID(),
				UUID.randomUUID(), true, maxCandidates, NOW.minusSeconds(86_400), NOW.plusSeconds(86_400));
		programConfigJpaRepository.save(config);
		return config;
	}

	private UUID saveCandidateWithPendingFicha(UUID admissionConfigId) {
		Candidate candidate = new Candidate(admissionConfigId,
				"ADM-2026-" + (100000 + (int) (Math.random() * 899999)), false, true, null);
		// Cada uno con su CURP: la regla de una candidatura por periodo no entra aquí.
		candidate.setCurp("CURP" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase());
		candidate.setFirstName("Prueba");
		candidate.setLastName1("Concurrencia");
		candidate = candidateJpaRepository.save(candidate);
		Payment charge = paymentJpaRepository.save(new Payment(candidate.getId(), null, "FICHA-ING", null, null, null,
				null, null, null, PaymentStatus.PENDING, PaymentOrigin.ADMISSION,
				TODAY.plusDays(5).atStartOfDay(ZONE).toInstant(), NOW));
		candidate.linkFichaPayment(charge.getId());
		candidateJpaRepository.save(candidate);
		return candidate.getId();
	}

	static class Wiring {

		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZONE);
		}

		/**
		 * Finanzas en pequeño: la ficha está pendiente y cobrarla la deja pagada en
		 * la misma transacción, que es lo que la cuenta del cupo ve.
		 */
		@Bean
		FichaPaymentPort fichaPaymentPort(PaymentJpaRepository payments) {
			FichaPaymentPort port = Mockito.mock(FichaPaymentPort.class);
			Mockito.when(port.findCharge(ArgumentMatchers.any()))
					.thenAnswer(call -> Optional.of(FichaTestData.pendingCharge(call.getArgument(0))));
			Mockito.when(port.settle(ArgumentMatchers.any())).thenAnswer(call -> {
				FichaPaymentPort.SettleFichaCommand command = call.getArgument(0);
				Payment payment = payments.findById(command.financePaymentId()).orElseThrow();
				payment.settle(AMOUNT, PaymentStatus.PAID);
				payments.save(payment);
				return new FichaPaymentPort.SettledFicha(AMOUNT, BigDecimal.ZERO, AMOUNT, AMOUNT, null, false);
			});
			return port;
		}

		@Bean
		ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase(CandidateRepository candidateRepository,
				FichaPaymentPort fichaPaymentPort, CheckoutAttemptRepository checkoutAttemptRepository,
				AdmissionQuotaPort admissionQuotaPort, Clock clock) {
			ProgramAdmissionConfigQueryPort configs = Mockito.mock(ProgramAdmissionConfigQueryPort.class);
			UUID periodId = UUID.randomUUID();
			Mockito.when(configs.findById(ArgumentMatchers.any())).thenAnswer(call -> Optional.of(
					new ProgramAdmissionConfigQueryPort.AdmissionConfigInfo(call.getArgument(0), null, null, null,
							null, null, periodId, null, null, null, 0)));
			return new ConfirmAdmissionPaymentUseCaseImpl(candidateRepository, fichaPaymentPort,
					checkoutAttemptRepository, Mockito.mock(CandidateNotificationPort.class), admissionQuotaPort,
					configs, clock, 60);
		}
	}
}
