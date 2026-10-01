package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.service.FichaPaymentWindow;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link AdmissionPaymentRepository} adapter delegating to
 * {@link AdmissionPaymentJpaRepository}.
 *
 * <p>It also owns the translation from the rule the caller states in calendar days
 * to the {@code Instant} cutoffs the occupancy queries compare against, so that
 * translation exists once instead of at each call site.
 */
@Component
public class AdmissionPaymentRepositoryAdapter implements AdmissionPaymentRepository {

	private final AdmissionPaymentJpaRepository jpaRepository;

	/**
	 * Read for its <b>zone</b> only, never for the instant. The admission zone is
	 * configured rather than inherited from the JVM ({@code UseCaseConfig}), and a
	 * quota boundary decided in the server's default zone is one the applicant
	 * cannot see on any screen — the dropdown, the checkout and the PDF would each
	 * disagree about which day it is.
	 */
	private final ZoneId zone;

	public AdmissionPaymentRepositoryAdapter(AdmissionPaymentJpaRepository jpaRepository, Clock clock) {
		this.jpaRepository = jpaRepository;
		this.zone = clock.getZone();
	}

	@Override
	public AdmissionPayment save(AdmissionPayment payment) {
		return jpaRepository.save(payment);
	}

	@Override
	public Optional<AdmissionPayment> findByCandidateId(UUID candidateId) {
		return jpaRepository.findByCandidateId(candidateId);
	}

	@Override
	public long countPaidByAdmissionConfigId(UUID admissionConfigId) {
		return jpaRepository.countPaidByAdmissionConfigId(admissionConfigId, AdmissionPaymentStatus.PAID);
	}

	/**
	 * A PENDING ficha occupies its slot only while it is still payable: inside its
	 * own plazo and inside the process's window. Both are day rules, so they are
	 * resolved into the two {@code Instant}s the query compares against.
	 *
	 * <p>The arithmetic is {@code FichaPaymentWindow}'s, not this class's, because
	 * the career picker needs the identical two cutoffs for its own copy of the
	 * occupancy subquery and two copies of a day rule is how they start disagreeing.
	 */
	@Override
	public long countOccupiedByConfigId(UUID admissionConfigId, LocalDate today, int paymentWindowDays) {
		return jpaRepository.countOccupiedByConfigId(admissionConfigId, AdmissionPaymentStatus.PAID,
				AdmissionPaymentStatus.PENDING, latestPayableRegistration(today, paymentWindowDays),
				startOfDay(today));
	}

	@Override
	public long countOccupiedByConfigIdExcludingCandidate(UUID admissionConfigId, UUID candidateId,
			LocalDate today, int paymentWindowDays) {
		return jpaRepository.countOccupiedByConfigIdExcludingCandidate(admissionConfigId, candidateId,
				AdmissionPaymentStatus.PAID, AdmissionPaymentStatus.PENDING,
				latestPayableRegistration(today, paymentWindowDays), startOfDay(today));
	}

	private Instant startOfDay(LocalDate today) {
		return FichaPaymentWindow.startOfDay(today, zone);
	}

	private Instant latestPayableRegistration(LocalDate today, int paymentWindowDays) {
		return FichaPaymentWindow.latestPayableRegistration(today, paymentWindowDays, zone);
	}
}
