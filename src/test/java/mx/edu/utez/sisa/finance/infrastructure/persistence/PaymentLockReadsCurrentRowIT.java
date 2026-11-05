package mx.edu.utez.sisa.finance.infrastructure.persistence;

import mx.edu.utez.sisa.finance.domain.model.Payment;
import mx.edu.utez.sisa.finance.domain.model.PaymentOrigin;
import mx.edu.utez.sisa.finance.domain.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1, el doble cobro de 0.26 s: una confirmación ya había leído el cargo (pendiente)
 * cuando otra lo pagó. Al bloquearlo después, debe ver el cargo como quedó, no la
 * copia que Hibernate guardaba de la primera lectura. Con esa copia, las dos
 * confirmaciones registraron un abono.
 *
 * <h2>Aviso: esta clase recrea el esquema al que apunta</h2>
 * Correrla solo con {@code DB_URL} apuntando a {@code sisa_it}.
 */
@DataJpaTest
@Import(PaymentRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentLockReadsCurrentRowIT {

	private static final Instant NOW = Instant.parse("2026-11-05T19:39:00Z");

	@Autowired
	private PaymentRepositoryAdapter payments;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void aLockTakenAfterAnEarlierReadSeesWhatAnotherTransactionCommitted() throws Exception {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		UUID id = tx.execute(status -> payments.save(new Payment(UUID.randomUUID(), null, "FICHA-ING", null, null,
				null, new BigDecimal("1500.00"), new BigDecimal("1500.00"), null, PaymentStatus.PENDING,
				PaymentOrigin.ADMISSION, NOW.plusSeconds(86_400), NOW)).getId());

		CountDownLatch read = new CountDownLatch(1);
		CountDownLatch paidElsewhere = new CountDownLatch(1);
		CompletableFuture<PaymentStatus> second = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
			assertThat(payments.findById(id)).get().extracting(Payment::getStatus).isEqualTo(PaymentStatus.PENDING);
			read.countDown();
			await(paidElsewhere);
			return payments.findByIdForUpdate(id).orElseThrow().getStatus();
		}));

		await(read);
		tx.executeWithoutResult(status -> {
			Payment payment = payments.findByIdForUpdate(id).orElseThrow();
			payment.settle(new BigDecimal("1500.00"), PaymentStatus.PAID);
			payments.save(payment);
		});
		paidElsewhere.countDown();

		assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(PaymentStatus.PAID);
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
