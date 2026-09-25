package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.domain.port.out.PlaceNameLookupPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.AdmissionConfigInfo;
import mx.edu.utez.sisa.shared.model.Address;
import mx.edu.utez.sisa.shared.model.DiversityProfile;
import mx.edu.utez.sisa.shared.model.EmploymentInfo;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.HealthProfile;
import mx.edu.utez.sisa.shared.model.HighSchoolBackground;
import mx.edu.utez.sisa.shared.model.MaritalStatus;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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

	@Mock
	private PlaceNameLookupPort placeNameLookupPort;

	@Mock
	private OutreachChannelRepository outreachChannelRepository;

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	private GetCandidateFichaUseCaseImpl useCase;

	private UUID candidateId;
	private UUID personId;
	private UUID configId;
	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new GetCandidateFichaUseCaseImpl(candidateRepository, candidatePersonRepository, paymentRepository,
				programAdmissionConfigQueryPort, placeNameLookupPort, outreachChannelRepository,
				highSchoolTypeRepository);
		candidateId = UUID.randomUUID();
		personId = UUID.randomUUID();
		configId = UUID.randomUUID();
		programId = UUID.randomUUID();

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

		lenient().when(programAdmissionConfigQueryPort.findById(configId)).thenReturn(Optional
				.of(new AdmissionConfigInfo(configId, ProgramAdmissionConfigStatus.OPEN, programId, "Mecatrónica",
						ProgramModality.PRESENCIAL, "2026-2")));
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

	@Test
	void get_assemblesFullPaso4SectionsWithResolvedNames() {
		UUID stateId = UUID.randomUUID();
		UUID municipalityId = UUID.randomUUID();
		UUID channelId = UUID.randomUUID();
		UUID schoolTypeId = UUID.randomUUID();

		// Enrich the shared Person fixture with the Fase 7 profile fields.
		var candidate = new mx.edu.utez.sisa.admission.domain.model.Candidate(personId, configId, "ADM-2026-000001",
				true, true, channelId);
		ReflectionTestUtils.setField(candidate, "id", candidateId);
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

		Person person = new Person("CURP0000000000000000", "Juan", "Perez", "Lopez", null);
		person.setPersonalEmail("juan@correo.com");
		person.setBirthDate(LocalDate.of(2006, 3, 14));
		person.setGender(Gender.M);
		person.setNationality("Mexicana");
		person.setBirthStateId(stateId);
		person.setBirthMunicipalityId(municipalityId);
		person.setMaritalStatus(MaritalStatus.SOLTERO);
		person.setNativeLanguage("Español");
		person.setHasChildren(false);
		person.setMonthlyFamilyIncome(new BigDecimal("12000.00"));
		person.setAddress(new Address("Av. Lázaro Cárdenas", "100", "", "Centro", "Jiquilpan", "59510", stateId,
				municipalityId, null, null));
		person.setHealthProfile(new HealthProfile(true, "Asma", false, null, null));
		person.setDiversityProfile(new DiversityProfile(true, false, true, false, false, false, false, "Náhuatl", null));
		person.setEmploymentInfo(new EmploymentInfo(true, null, "Ferretería López", "Cajero",
				"3515123456", new BigDecimal("6500.00"), LocalTime.of(9, 0), LocalTime.of(18, 0)));
		person.setHighSchoolBackground(new HighSchoolBackground("CBTis 121", "Jiquilpan", schoolTypeId,
				new BigDecimal("8.9"), true, stateId, municipalityId, "16DCT0121B", true, null, null, null, null));
		when(candidatePersonRepository.findById(personId)).thenReturn(Optional.of(person));

		// Payment carries the EVO order id when an online checkout ran.
		AdmissionPayment payment = new AdmissionPayment(candidateId, AdmissionPaymentConcept.ADMISSION_FICHA,
				new BigDecimal("500.00"), "REF-20260922-000001", LocalDate.now().plusDays(10));
		ReflectionTestUtils.setField(payment, "orderId", "TESTUTEZ-ADM-2026-000001");
		when(paymentRepository.findByCandidateId(candidateId)).thenReturn(Optional.of(payment));

		when(placeNameLookupPort.findStateName(stateId)).thenReturn(Optional.of("Michoacán"));
		when(placeNameLookupPort.findMunicipalityName(municipalityId)).thenReturn(Optional.of("Jiquilpan"));
		when(outreachChannelRepository.findById(channelId))
				.thenReturn(Optional.of(new OutreachChannel("Portal de admisión")));
		when(highSchoolTypeRepository.findById(schoolTypeId))
				.thenReturn(Optional.of(new HighSchoolType("Bachillerato Tecnológico")));

		var ficha = useCase.get(candidateId);

		assertThat(ficha).isNotNull();
		// Datos generales + nombres de catálogos resueltos.
		assertThat(ficha.datosGenerales().birthDate()).isEqualTo(LocalDate.of(2006, 3, 14));
		assertThat(ficha.datosGenerales().gender()).isEqualTo(Gender.M);
		assertThat(ficha.datosGenerales().birthStateName()).isEqualTo("Michoacán");
		assertThat(ficha.datosGenerales().birthMunicipalityName()).isEqualTo("Jiquilpan");
		assertThat(ficha.datosGenerales().maritalStatus()).isEqualTo(MaritalStatus.SOLTERO);
		// Domicilio con nombres.
		assertThat(ficha.domicilio().street()).isEqualTo("Av. Lázaro Cárdenas");
		assertThat(ficha.domicilio().stateName()).isEqualTo("Michoacán");
		// Info complementaria.
		assertThat(ficha.informacionComplementaria().hasPreexistingCondition()).isTrue();
		assertThat(ficha.informacionComplementaria().conditionDescription()).isEqualTo("Asma");
		assertThat(ficha.informacionComplementaria().parentsSpeakIndigenousLanguage()).isTrue();
		assertThat(ficha.informacionComplementaria().parentsIndigenousLanguage()).isEqualTo("Náhuatl");
		// Ingresos.
		assertThat(ficha.ingresos().isEmployed()).isTrue();
		assertThat(ficha.ingresos().companyName()).isEqualTo("Ferretería López");
		assertThat(ficha.ingresos().workStartTime()).isEqualTo(LocalTime.of(9, 0));
		// Selección de carrera con modalidad/periodo/canal resueltos.
		assertThat(ficha.seleccionCarrera().modalityName()).isEqualTo("Presencial");
		assertThat(ficha.seleccionCarrera().periodName()).isEqualTo("2026-2");
		assertThat(ficha.seleccionCarrera().outreachChannelName()).isEqualTo("Portal de admisión");
		assertThat(ficha.seleccionCarrera().isFirstChoice()).isTrue();
		// Antecedentes escolares.
		assertThat(ficha.antecedentesEscolares().schoolName()).isEqualTo("CBTis 121");
		assertThat(ficha.antecedentesEscolares().schoolTypeName()).isEqualTo("Bachillerato Tecnológico");
		assertThat(ficha.antecedentesEscolares().schoolStateName()).isEqualTo("Michoacán");
		assertThat(ficha.antecedentesEscolares().cct()).isEqualTo("16DCT0121B");
		// Pago expone el orderId de EVO.
		assertThat(ficha.orderId()).isEqualTo("TESTUTEZ-ADM-2026-000001");
	}

	@Test
	void get_withoutProfilesResolvesNullSectionsWithoutFailing() {
		var ficha = useCase.get(candidateId);

		assertThat(ficha).isNotNull();
		assertThat(ficha.domicilio().street()).isNull();
		assertThat(ficha.informacionComplementaria().hasPreexistingCondition()).isFalse();
		assertThat(ficha.antecedentesEscolares().schoolName()).isNull();
		assertThat(ficha.ingresos().isEmployed()).isFalse();
		assertThat(ficha.orderId()).isNull();
	}
}