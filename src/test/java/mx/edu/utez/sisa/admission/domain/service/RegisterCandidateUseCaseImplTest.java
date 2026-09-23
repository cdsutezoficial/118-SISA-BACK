package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.AntecedentesEscolares;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Contacto;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.DatosGenerales;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Domicilio;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.InformacionComplementaria;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Ingresos;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.CandidateRegistrationResult;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.SeleccionCarrera;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.AdmissionConfigInfo;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;
import mx.edu.utez.sisa.shared.model.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterCandidateUseCaseImplTest {

	private static final UUID ADMISSION_CONFIG_ID = UUID.randomUUID();
	private static final UUID CHANNEL_ID = UUID.randomUUID();
	private static final UUID SCHOOL_TYPE_ID = UUID.randomUUID();
	private static final BigDecimal FICHA_AMOUNT = new BigDecimal("500.00");
	private static final int DEADLINE_DAYS = 10;

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CandidatePersonRepository candidatePersonRepository;

	@Mock
	private AdmissionPaymentRepository admissionPaymentRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	@Mock
	private OutreachChannelRepository outreachChannelRepository;

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	private RegisterCandidateUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new RegisterCandidateUseCaseImpl(candidateRepository, candidatePersonRepository,
				admissionPaymentRepository, programAdmissionConfigQueryPort, outreachChannelRepository,
				highSchoolTypeRepository, FICHA_AMOUNT, DEADLINE_DAYS);
	}

	@Test
	void register_generatesPaymentWithReferenceAmountAndDeadline() {
		Person person = new Person("CURP0000000000000000", "Juan", "Perez", "Lopez", null);
		ReflectionTestUtils.setField(person, "id", UUID.randomUUID());
		when(outreachChannelRepository.findById(CHANNEL_ID))
				.thenReturn(Optional.of(new OutreachChannel("Portal de admisión")));
		when(highSchoolTypeRepository.findById(SCHOOL_TYPE_ID))
				.thenReturn(Optional.of(new mx.edu.utez.sisa.admission.domain.model.HighSchoolType("Bachillerato")));
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.empty());
		when(candidatePersonRepository.save(any())).thenReturn(person);
		when(candidateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(programAdmissionConfigQueryPort.findById(ADMISSION_CONFIG_ID))
				.thenReturn(Optional.of(new AdmissionConfigInfo(ADMISSION_CONFIG_ID, ProgramAdmissionConfigStatus.OPEN,
						"Ingeniería en Sistemas")));

		CandidateRegistrationResult result = useCase.register(command());

		assertThat(result.status()).isEqualTo(CandidateStatus.REGISTERED);
		assertThat(result.payment()).isNotNull();
		assertThat(result.payment().referenceNumber()).startsWith("REF-");
		assertThat(result.payment().amount()).isEqualByComparingTo(FICHA_AMOUNT);
		assertThat(result.payment().deadline()).isEqualTo(LocalDate.now().plusDays(DEADLINE_DAYS));
		assertThat(result.payment().paymentStatus()).isEqualTo(AdmissionPaymentStatus.PENDING);

		ArgumentCaptor<AdmissionPayment> paymentCaptor = ArgumentCaptor.forClass(AdmissionPayment.class);
		verify(admissionPaymentRepository).save(paymentCaptor.capture());
		AdmissionPayment saved = paymentCaptor.getValue();
		assertThat(saved.getConcept()).isEqualTo(AdmissionPaymentConcept.ADMISSION_FICHA);
		assertThat(saved.getReferenceNumber()).isEqualTo(result.payment().referenceNumber());
		assertThat(saved.getPaymentStatus()).isEqualTo(AdmissionPaymentStatus.PENDING);
	}

	private static RegisterCandidateCommand command() {
		return new RegisterCandidateCommand(datosGenerales(), new Domicilio("Calle", "1", null, "Colonia", "Ciudad",
				"91000", UUID.randomUUID(), UUID.randomUUID()), new Contacto("a@b.com", null, "555"),
				new InformacionComplementaria(false, null, false, null, false, null, false, null, false, false, false,
						false, false),
				new Ingresos(BigDecimal.ZERO, false, null, null, null, null, null, null, null),
				new AntecedentesEscolares("Prep", SCHOOL_TYPE_ID, true, UUID.randomUUID(), UUID.randomUUID(), null,
						null, BigDecimal.valueOf(8.5), null, false),
				new SeleccionCarrera(ADMISSION_CONFIG_ID, CHANNEL_ID, true), true);
	}

	private static DatosGenerales datosGenerales() {
		return new DatosGenerales("CURP0000000000000000", "Juan", "Perez", "Lopez", LocalDate.of(2006, 1, 1),
				Gender.M, "Mexicana", null, null, null, null, MaritalStatus.SOLTERO, "Español", false);
	}
}