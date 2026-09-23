package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.AdmissionConfigInfo;
import mx.edu.utez.sisa.shared.model.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCandidateFichaUseCaseImplTest {

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CandidatePersonRepository candidatePersonRepository;

	@Mock
	private AdmissionPaymentRepository paymentRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private GetCandidateFichaUseCaseImpl useCase;

	private UUID candidateId;
	private UUID personId;
	private UUID configId;

	@BeforeEach
	void setUp() {
		useCase = new GetCandidateFichaUseCaseImpl(candidateRepository, candidatePersonRepository, paymentRepository,
				programAdmissionConfigQueryPort);
		candidateId = UUID.randomUUID();
		personId = UUID.randomUUID();
		configId = UUID.randomUUID();

		var candidate = new mx.edu.utez.sisa.admission.domain.model.Candidate(personId, configId, "ADM-2026-000001",
				true, true, null);
		ReflectionTestUtils.setField(candidate, "id", candidateId);
		lenient().when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

		Person person = new Person("CURP0000000000000000", "Juan", "Perez", "Lopez", null);
		person.setPersonalEmail("juan@correo.com");
		ReflectionTestUtils.setField(person, "id", personId);
		lenient().when(candidatePersonRepository.findById(personId)).thenReturn(Optional.of(person));

		lenient().when(paymentRepository.findByCandidateId(candidateId)).thenReturn(Optional
				.of(new AdmissionPayment(candidateId, AdmissionPaymentConcept.ADMISSION_FICHA, new BigDecimal("500.00"),
						"REF-20260922-000001", LocalDate.now().plusDays(10))));

		lenient().when(programAdmissionConfigQueryPort.findById(configId)).thenReturn(
				Optional.of(new AdmissionConfigInfo(configId, ProgramAdmissionConfigStatus.OPEN, "Mecatrónica")));
	}

	@Test
	void get_assemblesFichaWithPersonPaymentAndProgramName() {
		var ficha = useCase.get(candidateId);

		assertThat(ficha).isNotNull();
		assertThat(ficha.folio()).isEqualTo("ADM-2026-000001");
		assertThat(ficha.programName()).isEqualTo("Mecatrónica");
		assertThat(ficha.email()).isEqualTo("juan@correo.com");
		assertThat(ficha.referenceNumber()).isEqualTo("REF-20260922-000001");
		assertThat(ficha.paymentStatus()).isEqualTo(AdmissionPaymentStatus.PENDING);
	}

	@Test
	void get_unknownCandidateReturnsNull() {
		UUID missing = UUID.randomUUID();
		when(candidateRepository.findById(missing)).thenReturn(Optional.empty());

		assertThat(useCase.get(missing)).isNull();
	}
}